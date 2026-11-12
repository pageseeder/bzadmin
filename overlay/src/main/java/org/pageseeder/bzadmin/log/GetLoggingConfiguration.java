package org.pageseeder.bzadmin.log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Issue;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Verdict;

/**
 * Whether the logging of the application writes events, and why not when it does not
 * ({@code docs/design/logging-configuration.md}).
 *
 * <p>Always writes the verdict, the issues and the resets observed, so that every view of the Logs page
 * can show a notice; the appenders, loggers, turbo filters, scan, status messages and the startup only
 * with {@code view=config}.
 *
 * <pre>{@code
 * <logging-configuration framework="logback" version="1.5.38" verdict="problems">
 *   <issue type="stopped-appender" severity="error" appender="a2" name="FILE"/>
 *   <resets count="1" last="2026-11-10T01:02:03Z"/>
 *   <!-- With view=config -->
 *   <appender id="a2" name="FILE" class="..." started="false" writes="false" file="/var/log/app/app.log" loggers="ROOT"/>
 *   <loggers total="1">
 *     <logger name="ROOT" level="INFO" effective-level="INFO" additive="true" output="true" appenders="a1 a2"/>
 *   </loggers>
 *   <turbo-filter class="..."/>
 *   <scan enabled="true" files="logback.xml"/>
 *   <statuses since="reset" errors="1" warnings="0" infos="42">
 *     <status level="ERROR" time="..." origin="..." message="..."><cause class="..." message="..."/></status>
 *   </statuses>
 *   <startup initialised="true" ... current="true"><candidate .../></startup>
 * </logging-configuration>
 * }</pre>
 *
 * <p>Without logback: {@code verdict="unsupported"}, with the startup only. Read-only, side-effect free
 * and not cacheable. In JSON, names are in camel case: {@code {"framework": ..., "issues": [...],
 * "appenders": [...], "loggers": {"total": ..., "items": [...]}, "statuses": {..., "items": [...]},
 * "startup": {..., "candidates": [...]}}}.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetLoggingConfiguration implements Generator {

  private static final String ITEMS = "items";

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    LoggingBackend backend = LoggingBackends.current();
    LoggingConfiguration configuration = configuration(backend);
    LoggingStartup startup = LoggingStartup.read(configuration.lastReset);
    write(configuration, startup, "config".equals(req.getParameter("view")), out);
    return Response.ok();
  }

  /**
   * @return the configuration of the logging framework, {@code unsupported} when it has none.
   */
  static LoggingConfiguration configuration(LoggingBackend backend) {
    @Nullable LoggingConfiguration configuration = backend.configuration();
    return configuration != null ? configuration
        : LoggingConfiguration.without(backend.name(), backend.version(), Verdict.UNSUPPORTED);
  }

  /**
   * Writes the configuration.
   *
   * @param details Whether to write the details (the Configuration view).
   */
  static void write(LoggingConfiguration c, LoggingStartup startup, boolean details, OutputWriter out) {
    out.startObject("logging-configuration");
    out.field("framework", c.framework);
    out.optionalField("version", c.version);
    out.field("verdict", c.verdict.toString());
    List<Issue> issues = new ArrayList<>(c.issues);
    if (c.inspected()) issues.addAll(startup.issues());
    // Errors first (the order of Severity), stable otherwise
    issues.sort((a, b) -> a.severity.compareTo(b.severity));
    out.startArray("issues", ContextOption.JSON_ONLY);
    for (Issue issue : issues) issue(issue, out);
    out.endArray();
    if (c.inspected()) {
      out.startObject("resets");
      out.field("count", c.resets);
      if (c.lastReset != null) out.field("last", c.lastReset.toString());
      out.endObject();
    }
    if (details) {
      if (c.inspected()) details(c, out);
      startup.write(out);
    }
    out.endObject();
  }

  private static void issue(Issue issue, OutputWriter out) {
    out.startObject("issue");
    out.field("type", issue.type);
    out.field("severity", issue.severity.toString());
    out.optionalField("appender", issue.appender);
    out.optionalField("name", issue.name);
    out.optionalField("logger", issue.logger);
    if (issue.count >= 0) out.field("count", issue.count);
    out.optionalField("location", issue.location);
    out.optionalField("path", issue.path);
    out.endObject();
  }

  private static void details(LoggingConfiguration c, OutputWriter out) {
    out.startArray("appenders", ContextOption.JSON_ONLY);
    for (LoggingConfiguration.Appender a : c.appenders) {
      out.startObject("appender");
      out.field("id", a.id);
      out.optionalField("name", a.name);
      out.field("class", a.className);
      out.field("started", a.started);
      out.field("writes", a.writes);
      if (a.capture) out.field("capture", true);
      out.optionalField("file", a.file);
      out.optionalField("holder", a.holder);
      out.field("loggers", String.join(" ", a.loggers));
      out.endObject();
    }
    out.endArray();

    out.startObject("loggers");
    out.field("total", c.loggerTotal);
    out.startArray(ITEMS, ContextOption.JSON_ONLY);
    for (LoggingConfiguration.Logger l : c.loggers) {
      out.startObject("logger");
      out.field("name", l.name);
      if (l.level != null) out.field("level", l.level.toString());
      if (l.effectiveLevel != null) out.field("effective-level", l.effectiveLevel.toString());
      out.field("additive", l.additive);
      out.field("output", l.output);
      out.field("appenders", String.join(" ", l.appenders));
      out.endObject();
    }
    out.endArray();
    out.endObject();

    out.startArray("turbo-filters", ContextOption.JSON_ONLY);
    for (String filter : c.turboFilters) {
      out.startObject("turbo-filter");
      out.field("class", filter);
      out.endObject();
    }
    out.endArray();

    out.startObject("scan");
    out.field("enabled", c.scan);
    if (c.scan) out.field("files", String.join(" ", c.watched));
    out.endObject();

    out.startObject("statuses");
    out.field("since", c.statusesSince);
    out.field("errors", c.errors);
    out.field("warnings", c.warnings);
    out.field("infos", c.infos);
    out.startArray(ITEMS, ContextOption.JSON_ONLY);
    for (LoggingConfiguration.Status s : c.statuses) {
      out.startObject("status");
      out.field("level", s.level);
      out.field("time", Instant.ofEpochMilli(s.time).toString());
      out.optionalField("origin", s.origin);
      out.field("message", s.message);
      out.startArray("causes", ContextOption.JSON_ONLY);
      for (LoggingConfiguration.Cause cause : s.causes) {
        out.startObject("cause");
        out.field("class", cause.className);
        out.optionalField("message", cause.message);
        out.endObject();
      }
      out.endArray();
      out.endObject();
    }
    out.endArray();
    out.endObject();
  }

}
