package org.pageseeder.bzadmin.log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The last lines of a text file, read with bounded memory and work: at most a number of bytes, a
 * number of lines, and a number of characters per line.
 *
 * <p>The file is read as bytes and split at line feeds (a byte that UTF-8 never uses inside a
 * character), so a window that starts in the middle of a character only drops the partial line it
 * starts with, and each line is decoded as UTF-8 on its own (malformed input is replaced).
 *
 * <p>Only the bytes up to the size of the file when it is opened are read: a file that grows while
 * it is read does not extend the work. A {@link LogCursor} continues from the end of a previous read,
 * reading only the bytes added since, and starts again from the end of the file when the file was
 * replaced (rotation) or is shorter than the position (truncation). {@link #readBefore} reads the
 * lines before a position instead, to show earlier lines; {@link #before()} is where to read them
 * from.
 */
final class LogTail {

  /** The size of the buffer for reading the file. */
  private static final int BUFFER_SIZE = 64 * 1024;

  /** The lines kept, oldest first. */
  private final List<String> lines;

  /** The byte position of the start of each line kept. */
  private final long[] positions;

  /** The number of the first line in the file (from 1), or 0 when unknown. */
  private final long first;

  /** Whether lines before the first one were not read or dropped. */
  private final boolean truncated;

  /** Whether the last line has no line feed yet (the cursor points to its start). */
  private final boolean incomplete;

  /** Whether the cursor did not apply: the tail was read from the end of the file. */
  private final boolean reset;

  /** Where to continue from. */
  private final LogCursor cursor;

  /** The start of the first line kept, to read the lines before it, or {@code null} if there are none. */
  private final @Nullable LogCursor before;

  /** The number of bytes read. */
  private final long read;

  private LogTail(Lines kept, long first, boolean truncated, boolean incomplete, boolean reset, LogCursor cursor, long read) {
    this.lines = kept.list();
    this.positions = kept.positions();
    this.first = first;
    this.truncated = truncated;
    this.incomplete = incomplete;
    this.reset = reset;
    this.cursor = cursor;
    this.before = this.positions.length > 0 && this.positions[0] > 0 ? new LogCursor(cursor.identity(), this.positions[0], first) : null;
    this.read = read;
  }

  /**
   * @param channel       The file, positioned anywhere; it is not closed.
   * @param identity      The identity of the file, to tell whether the cursor applies to it.
   * @param from          Where a previous read ended, or {@code null} to read the end of the file.
   * @param maxLines      The maximum number of lines to keep.
   * @param maxBytes      The maximum number of bytes to read.
   * @param maxLineLength The maximum number of characters kept for each line; the rest is dropped.
   */
  static LogTail read(SeekableByteChannel channel, String identity, @Nullable LogCursor from, int maxLines, long maxBytes, int maxLineLength)
      throws IOException {
    long size = channel.size();
    if (from == null || !applies(channel, identity, from, size, maxBytes)) {
      return end(channel, identity, size, from != null, maxLines, maxBytes, maxLineLength);
    }
    return window(channel, identity, new Window(from.position(), size, from.line(), 0, false), false, new Lines(maxLines, maxLineLength));
  }

  /**
   * Reads the lines that end at a position, as far back as the bounds allow.
   *
   * @param channel       The file, positioned anywhere; it is not closed.
   * @param identity      The identity of the file, to tell whether the cursor applies to it.
   * @param before        The start of the first line of a previous read ({@link #before()}), or
   *                      {@code null} to read the end of the file.
   * @param maxLines      The maximum number of lines to keep: the last ones before the position.
   * @param maxBytes      The maximum number of bytes to read.
   * @param maxLineLength The maximum number of characters kept for each line; the rest is dropped.
   */
  static LogTail readBefore(SeekableByteChannel channel, String identity, @Nullable LogCursor before, int maxLines, long maxBytes,
      int maxLineLength) throws IOException {
    long size = channel.size();
    if (before == null || !before.identity().equals(identity) || before.position() > size
        || (before.position() > 0 && !endsLine(channel, before.position()))) {
      return end(channel, identity, size, before != null, maxLines, maxBytes, maxLineLength);
    }
    long start = Math.max(0, before.position() - maxBytes);
    return window(channel, identity, new Window(start, before.position(), start == 0 ? 1 : 0, before.line(), start > 0), false,
        new Lines(maxLines, maxLineLength));
  }

  /**
   * Reads the end of the file.
   *
   * @param reset Whether a cursor was given but did not apply.
   */
  private static LogTail end(SeekableByteChannel channel, String identity, long size, boolean reset, int maxLines, long maxBytes,
      int maxLineLength) throws IOException {
    long start = Math.max(0, size - maxBytes);
    return window(channel, identity, new Window(start, size, start == 0 ? 1 : 0, 0, start > 0), reset, new Lines(maxLines, maxLineLength));
  }

  /**
   * Reads the lines of a window.
   *
   * @param reset Whether a cursor was given but did not apply.
   * @param kept  Where to keep the lines, empty.
   */
  private static LogTail window(SeekableByteChannel channel, String identity, Window window, boolean reset, Lines kept)
      throws IOException {
    long start = window.start;
    long line = window.line;
    long endLine = window.endLine;
    Splitter splitter = new Splitter(kept, start, window.partial);
    long position = splitter.read(channel, window.end);
    boolean incomplete = splitter.finish(position);
    long complete = kept.total() - (incomplete ? 1 : 0);
    long first;
    long next;
    if (line > 0) {
      first = line + kept.total() - kept.size();
      next = line + complete;
    } else if (endLine > 0 && !incomplete) {
      first = endLine - kept.size();
      next = endLine;
    } else {
      first = 0;
      next = 0;
    }
    LogCursor cursor = new LogCursor(identity, splitter.end, next);
    boolean truncated = window.partial || kept.total() > kept.size();
    return new LogTail(kept, first, truncated, incomplete, reset, cursor, position - start);
  }

  /**
   * @return whether the cursor applies to this file, which is not shorter, has no more added than a
   *   read allows, and has a line feed before the position (or the position is its start).
   */
  private static boolean applies(SeekableByteChannel channel, String identity, LogCursor from, long size, long maxBytes)
      throws IOException {
    return from.identity().equals(identity) && from.position() <= size && size - from.position() <= maxBytes
        && (from.position() == 0 || endsLine(channel, from.position()));
  }

  /**
   * @return whether the byte before this position is a line feed.
   */
  private static boolean endsLine(SeekableByteChannel channel, long position) throws IOException {
    ByteBuffer previous = ByteBuffer.allocate(1);
    channel.position(position - 1);
    return channel.read(previous) == 1 && previous.get(0) == '\n';
  }

  List<String> lines() {
    return this.lines;
  }

  /**
   * @return the byte position of the start of each line, in the order of {@link #lines()}.
   */
  long[] positions() {
    return this.positions;
  }

  /**
   * @return the number of the first line in the file (from 1), or 0 when it is not known.
   */
  long first() {
    return this.first;
  }

  /**
   * @return whether the file has lines before the first one that were not returned.
   */
  boolean truncated() {
    return this.truncated;
  }

  /**
   * @return whether the last line has no line feed yet: the next read returns it again.
   */
  boolean incomplete() {
    return this.incomplete;
  }

  /**
   * @return whether a cursor was given but did not apply (the file was replaced or truncated, or more
   *   was added than a read allows), so the end of the file was read.
   */
  boolean reset() {
    return this.reset;
  }

  /**
   * @return where to continue from.
   */
  LogCursor cursor() {
    return this.cursor;
  }

  /**
   * @return the start of the first line, to read the lines before it with {@link #readBefore}, or
   *   {@code null} when there are none (the first line is the first of the file, or no line was kept).
   */
  @Nullable LogCursor before() {
    return this.before;
  }

  /**
   * @return the number of bytes read from the file.
   */
  long bytesRead() {
    return this.read;
  }

  /**
   * The part of a file to read.
   */
  private static final class Window {

    /** Where to start reading. */
    private final long start;

    /** Where to stop reading. */
    private final long end;

    /** The number of the line at the start, or 0 when unknown. */
    private final long line;

    /** The number of the line at the end, or 0 when unknown (used when the line at the start is unknown). */
    private final long endLine;

    /** Whether the start may be in the middle of a line, which is then dropped. */
    private final boolean partial;

    Window(long start, long end, long line, long endLine, boolean partial) {
      this.start = start;
      this.end = end;
      this.line = line;
      this.endLine = endLine;
      this.partial = partial;
    }
  }

  /**
   * Splits the bytes read at line feeds, keeping the bytes of each line that can make the characters
   * kept.
   */
  private static final class Splitter {

    private final Lines kept;

    /** The maximum number of bytes of a line that can make the characters kept (4 bytes per character). */
    private final int maxLineBytes;

    private final ByteArrayOutputStream current = new ByteArrayOutputStream();

    /** Whether the current line has more bytes than those kept. */
    private boolean overflow = false;

    /** Whether the current line started before the window: it is dropped. */
    private boolean partial;

    /** The position after the last line feed. */
    private long end;

    Splitter(Lines kept, long start, boolean partial) {
      this.kept = kept;
      this.maxLineBytes = kept.maxLineLength * 4;
      this.end = start;
      this.partial = partial;
    }

    /**
     * Reads the channel from its position up to a size.
     *
     * @return the position where reading stopped.
     */
    long read(SeekableByteChannel channel, long size) throws IOException {
      long position = this.end;
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      channel.position(position);
      while (position < size) {
        buffer.clear();
        buffer.limit((int) Math.min(BUFFER_SIZE, size - position));
        int n = channel.read(buffer);
        // Truncated while it is read
        if (n < 0) break;
        split(buffer.array(), n, position);
        position += n;
      }
      return position;
    }

    private void split(byte[] bytes, int n, long position) {
      for (int i = 0; i < n; i++) {
        byte b = bytes[i];
        if (b == '\n') {
          endLine(position + i + 1);
        } else if (this.current.size() < this.maxLineBytes) {
          this.current.write(b);
        } else {
          this.overflow = true;
        }
      }
    }

    private void endLine(long next) {
      if (!this.partial) this.kept.add(this.current.toByteArray(), this.overflow, this.end);
      this.partial = false;
      this.current.reset();
      this.overflow = false;
      this.end = next;
    }

    /**
     * Keeps the last line if it has no line feed yet.
     *
     * @param position Where reading stopped.
     * @return whether the last line kept has no line feed yet.
     */
    boolean finish(long position) {
      boolean incomplete = this.current.size() > 0 && !this.partial;
      if (incomplete) this.kept.add(this.current.toByteArray(), this.overflow, this.end);
      // A single line longer than the window: continue after it
      if (this.partial) this.end = position;
      return incomplete;
    }
  }

  /**
   * The last lines, decoded.
   */
  private static final class Lines {

    private final Deque<String> kept = new ArrayDeque<>();

    private final Deque<Long> positions = new ArrayDeque<>();

    private final int maxLines;

    private final int maxLineLength;

    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);

    private long total = 0;

    Lines(int maxLines, int maxLineLength) {
      this.maxLines = maxLines;
      this.maxLineLength = maxLineLength;
    }

    void add(byte[] bytes, boolean overflow, long position) {
      int length = bytes.length;
      if (!overflow && length > 0 && bytes[length - 1] == '\r') length--;
      String line = decode(bytes, length);
      if (overflow || line.length() > this.maxLineLength) {
        int cut = this.maxLineLength;
        // Do not split a surrogate pair
        if (cut > 0 && cut < line.length() && Character.isHighSurrogate(line.charAt(cut - 1))) cut--;
        line = line.substring(0, Math.min(cut, line.length())) + '…';
      }
      this.kept.addLast(line);
      this.positions.addLast(position);
      if (this.kept.size() > this.maxLines) {
        this.kept.removeFirst();
        this.positions.removeFirst();
      }
      this.total++;
    }

    private String decode(byte[] bytes, int length) {
      try {
        CharBuffer chars = this.decoder.reset().decode(ByteBuffer.wrap(bytes, 0, length));
        return chars.toString();
      } catch (CharacterCodingException ex) {
        // Not with REPLACE
        throw new IllegalStateException(ex);
      }
    }

    long total() {
      return this.total;
    }

    int size() {
      return this.kept.size();
    }

    List<String> list() {
      return new ArrayList<>(this.kept);
    }

    long[] positions() {
      return this.positions.stream().mapToLong(Long::longValue).toArray();
    }
  }

}
