package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.output.OutputWriter.FieldOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Returns the recent log events captured by the logging adapter (see {@link LoggingBackends}),
 * oldest first, in place of Bastille's {@code GetRecentLogsEvents}.
 *
 * <pre>{@code
 * <recent-logs backend="logback" version="1.5.37" threshold="INFO" root-level="INFO" capacity="256" captured="1234">
 *   <event level="WARN" logger="org.example.Service" thread="main" timestamp="1792710123456"
 *          datetime="2026-10-23T01:02:03.456Z" message="...">
 *     <throwable class="java.io.IOException" message="..." omitted="12">
 *       <step>org.example.Service.read(Service.java:42)</step>
 *       <cause class="..." message="..." omitted="..."><step>...</step></cause>
 *     </throwable>
 *   </event>
 * </recent-logs>
 * }</pre>
 *
 * <p>{@code version} is the version of the logging framework (omitted when unknown).
 * {@code threshold} is the capture threshold, {@code root-level} the level of the root logger of
 * the application (omitted when not set), {@code captured} the number of events captured since the
 * capture started, of which the last {@code capacity} at most are held. An event is
 * {@code truncated} when text was cut or stack trace elements left out (see {@link RecentEvent}).
 *
 * <p>When the logging framework does not support recent events: {@code <no-recent-logs backend="..." version="..."/>}
 * with a 200, so that the Logs page still works.
 *
 * <p>In JSON: {@code {"backend": "logback", ..., "events": [{"level": "WARN", ..., "throwable": {"class": ..., "step": [...], "cause": {...}}}]}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetRecentEvents implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    write(LoggingBackends.current(), out);
    return Response.ok();
  }

  /**
   * Writes the recent events of this logging framework.
   */
  static void write(LoggingBackend backend, OutputWriter out) {
    @Nullable RecentEvents events = backend.recentEvents();
    if (events == null) {
      noRecentLogs(backend, out);
      return;
    }
    RecentEvents.Snapshot snapshot = events.snapshot();
    out.startObject("recent-logs");
    out.field("backend", backend.name());
    @Nullable String version = backend.version();
    if (version != null) out.field("version", version);
    out.field("threshold", events.threshold().toString());
    @Nullable EventLevel root = backend.rootLevel();
    if (root != null) out.field("root-level", root.toString());
    out.field("capacity", events.capacity());
    out.field("captured", snapshot.captured());
    out.startArray("events", ContextOption.JSON_ONLY);
    for (RecentEvent event : snapshot.events()) {
      out.startObject("event");
      out.field("level", event.level().toString());
      @Nullable String logger = event.logger();
      if (logger != null) out.field("logger", logger);
      @Nullable String thread = event.thread();
      if (thread != null) out.field("thread", thread);
      out.field("timestamp", event.timestamp());
      out.field("datetime", Instant.ofEpochMilli(event.timestamp()).toString());
      @Nullable String message = event.message();
      if (message != null) out.field("message", message);
      if (event.truncated()) out.field("truncated", true);
      RecentEvent.@Nullable Trace throwable = event.throwable();
      if (throwable != null) toOutput(throwable, "throwable", out);
      out.endObject();
    }
    out.endArray();
    out.endObject();
  }

  /**
   * Writes that the logging framework does not support recent events.
   */
  static void noRecentLogs(LoggingBackend backend, OutputWriter out) {
    out.startObject("no-recent-logs");
    out.field("backend", backend.name());
    @Nullable String version = backend.version();
    if (version != null) out.field("version", version);
    out.endObject();
  }

  private static void toOutput(RecentEvent.Trace trace, String name, OutputWriter out) {
    out.startObject(name);
    out.field("class", trace.className());
    @Nullable String message = trace.message();
    if (message != null) out.field("message", message);
    if (trace.omitted() > 0) out.field("omitted", trace.omitted());
    out.field("step", trace.steps(), FieldOption.XML_ELEMENT);
    RecentEvent.@Nullable Trace cause = trace.cause();
    if (cause != null) toOutput(cause, "cause", out);
    out.endObject();
  }

}
