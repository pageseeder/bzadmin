package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;

import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Sets the capture threshold of the recent log events, in place of Bastille's
 * {@code SetRecentLogsThreshold}: an operation ({@link AdminPermission#OPERATE}).
 *
 * <p>The threshold only selects among the events that the application logs: it does not change the
 * level of any logger of the application.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code threshold}: {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR}
 *     or {@code OFF} (400 otherwise)</li>
 * </ul>
 *
 * <pre>{@code <recent-logs-threshold level="WARN" was="INFO"/>}</pre>
 *
 * <p>When the logging framework does not support recent events: {@code <no-recent-logs backend="..."/>}
 * with a 501.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class SetRecentEventsThreshold implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    EventLevel threshold = req.parameter("threshold").asEnum(EventLevel.class).required();
    return set(LoggingBackends.current(), threshold, out);
  }

  /**
   * Sets the capture threshold of this logging framework.
   */
  static Response set(LoggingBackend backend, EventLevel threshold, OutputWriter out) {
    @Nullable RecentEvents events = backend.recentEvents();
    if (events == null) {
      GetRecentEvents.noRecentLogs(backend, out);
      return Response.status(ContentStatus.NOT_IMPLEMENTED);
    }
    EventLevel was = events.threshold();
    events.setThreshold(threshold);
    out.startObject("recent-logs-threshold");
    out.field("level", threshold.toString());
    out.field("was", was.toString());
    out.endObject();
    return Response.ok();
  }

}
