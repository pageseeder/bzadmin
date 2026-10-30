package org.pageseeder.bzadmin.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.NoSuchFileException;

import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.output.OutputWriter.FieldOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.log.LogFiles.LogFile;

import org.jspecify.annotations.Nullable;

/**
 * Returns the last lines of a log file listed by {@link ListLogFiles}, named by its ID, with the
 * XML of Bastille's {@code GetRawLogEntries}, a cursor to read the lines added since, and another
 * to read the lines before.
 *
 * <p>The work is bounded: at most {@value #MAX_LINES} lines, {@value #MAX_LINE_LENGTH} characters
 * per line, from at most {@value #MAX_BYTES} bytes of the file (the last ones, or those added since
 * the cursor, or before the position to read earlier lines from), and at most {@value LogReads#MAX_CONCURRENT} reads at the same time.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code id}: the ID of the log file, from the listing</li>
 *   <li>{@code name}: or the name of the log file, e.g. {@code app.2026-10-23.log}, optionally after
 *     the name of its directory ({@code logs/app.2026-10-23.log}); only compared with the names of
 *     the files found (see {@link LogFiles#findByName(String)}), never used as a path</li>
 *   <li>{@code lines}: the number of lines, {@value #DEFAULT_LINES} by default</li>
 *   <li>{@code cursor}: the cursor of a previous response, to return only the lines added since</li>
 *   <li>{@code before}: the {@code before} cursor of a previous response, to return the lines before
 *     its first line instead ({@code cursor} is then ignored)</li>
 * </ul>
 *
 * <pre>{@code
 * <log id="0123456789abcdef" name="app.log" size="5242880" truncated="true" reset="false" cursor="..." before="...">
 *   <line position="5242000" n="1201" level="INFO">...</line>
 *   <line position="5242810" n="1202" incomplete="true">...</line>
 * </log>
 * }</pre>
 *
 * <p>{@code n} is the line number when it is known (the file was read from its start), and is omitted
 * otherwise, and {@code position} the byte offset of its start. The last line is {@code incomplete} when it has no line feed yet: the cursor points to
 * its start, so the next response returns it again. {@code reset} means that the cursor did not
 * apply (the file was rotated or truncated, or more was added than a read allows, or it is not a
 * cursor) and the end of the file was read instead. {@code before} is the start of the first line,
 * to read the lines before it; it is absent when the first line is the first of the file.
 *
 * <p>A cursor is {@code <identity>.<position>.<line>} (see {@link LogCursor}): the page makes the
 * {@code before} cursor of a line from another cursor of the file and the {@code position} and
 * {@code n} of the line (0 when unknown), when it keeps fewer lines than it received.
 *
 * <p>{@code id} or {@code name} is required (400 without either; {@code id} is used when both are
 * given). An unknown ID or name returns {@code <no-log id="..."/>} or {@code <no-log name="..."/>} with a 404, too many reads at the same time
 * {@code <no-log id="..." reason="busy"/>} with a 503.
 *
 * <p>In JSON: {@code {"id": ..., "name": "app.log", "truncated": true, "cursor": ..., "lines": [{"position": 5242000, "n": 1201, "level": "INFO", "text": "..."}]}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetLogEntries implements Generator {

  static final int DEFAULT_LINES = 1000;

  static final int MAX_LINES = 5000;

  static final int MAX_LINE_LENGTH = 8192;

  static final long MAX_BYTES = 4L * 1024 * 1024;

  /** Levels to look for, as Bastille. */
  private static final String[] LEVELS = {"INFO", "WARN", "ERROR", "DEBUG"};

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    int lines = req.parameter("lines").asInt().clamp(1, MAX_LINES).defaultValue(DEFAULT_LINES);
    @Nullable String id = req.getParameter("id");
    @Nullable String name = id == null ? req.getParameter("name") : null;
    // Neither: the usual response of Berlioz for a missing parameter (400)
    if (id == null && name == null) req.parameter("id").asString().required();
    @Nullable String before = req.getParameter("before");
    @Nullable String given = before != null ? before : req.getParameter("cursor");
    @Nullable LogCursor cursor = LogCursor.parse(given);
    LogFiles logs = LogFiles.fromSettings();
    @Nullable LogFile file = id != null ? logs.find(id) : logs.findByName(name);
    if (file == null) return noLog(id, name, null, ContentStatus.NOT_FOUND, out);
    if (!LogReads.acquire()) return noLog(id, name, "busy", ContentStatus.SERVICE_UNAVAILABLE, out);
    LogTail tail;
    long size;
    try {
      // The identity before opening: if the file is replaced in between, the cursor will not apply
      String identity = file.identity();
      try (SeekableByteChannel channel = file.open()) {
        size = channel.size();
        tail = before != null
            ? LogTail.readBefore(channel, identity, cursor, lines, MAX_BYTES, MAX_LINE_LENGTH)
            : LogTail.read(channel, identity, cursor, lines, MAX_BYTES, MAX_LINE_LENGTH);
      }
    } catch (NoSuchFileException ex) {
      // Deleted or replaced by a link since it was listed
      return noLog(id, name, null, ContentStatus.NOT_FOUND, out);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    } finally {
      LogReads.release();
    }
    // Not a cursor: as one that does not apply
    log(file, size, tail, given != null && cursor == null, out);
    return Response.ok();
  }

  private static void log(LogFile file, long size, LogTail tail, boolean invalidCursor, OutputWriter out) {
    out.startObject("log");
    out.field("id", file.id());
    out.field("name", file.name());
    out.field("size", size);
    out.field("truncated", tail.truncated());
    out.field("reset", tail.reset() || invalidCursor);
    out.field("cursor", tail.cursor().toString());
    @Nullable LogCursor before = tail.before();
    if (before != null) out.field("before", before.toString());
    out.startArray("lines", ContextOption.JSON_ONLY);
    long n = tail.first();
    int count = tail.lines().size();
    for (int i = 0; i < count; i++) {
      String line = tail.lines().get(i);
      out.startObject("line");
      out.field("position", tail.positions()[i]);
      if (n > 0) out.field("n", n++);
      @Nullable String level = level(line);
      if (level != null) out.field("level", level);
      if (tail.incomplete() && i == count - 1) out.field("incomplete", true);
      out.field("text", line, FieldOption.XML_TEXT);
      out.endObject();
    }
    out.endArray();
    out.endObject();
  }

  private static Response noLog(@Nullable String id, @Nullable String name, @Nullable String reason, ContentStatus status, OutputWriter out) {
    out.startObject("no-log");
    if (id != null) out.field("id", id);
    if (name != null) out.field("name", name);
    if (reason != null) out.field("reason", reason);
    out.endObject();
    return Response.status(status);
  }

  private static @Nullable String level(String line) {
    for (String level : LEVELS) {
      if (line.contains(level)) return level;
    }
    return null;
  }

}
