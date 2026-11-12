package org.pageseeder.bzadmin.log;

import static org.pageseeder.bzadmin.berlioz.SnapshotFields.flag;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.number;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.objects;
import static org.pageseeder.bzadmin.berlioz.SnapshotFields.text;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.servlet.AppInitializer;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Issue;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Severity;

/**
 * How Berlioz configured logging at startup, from its snapshot ({@code AppInitializer.loggingSnapshot()},
 * Berlioz 0.14.6): the files it considered, the one in use, and the {@code startup-*} issues.
 *
 * <p>The snapshot records the startup only. When the logback adapter observed a reset after its
 * {@code time}, logging was reconfigured since, so the startup is no longer {@linkplain #current current}
 * and gives no issue.
 */
final class LoggingStartup {

  private static final String CANDIDATES = "candidates";

  private final Map<String, Object> fields;

  private final boolean current;

  private LoggingStartup(Map<String, Object> fields, boolean current) {
    this.fields = fields;
    this.current = current;
  }

  /**
   * @param lastReset The time of the last reset observed by the adapter, or {@code null}.
   * @return the startup recorded by Berlioz.
   */
  static LoggingStartup read(@Nullable Instant lastReset) {
    return of(AppInitializer.loggingSnapshot().fields(), lastReset);
  }

  /**
   * @param fields    The fields of Berlioz's logging snapshot.
   * @param lastReset The time of the last reset observed by the adapter, or {@code null}.
   * @return the startup of these fields.
   */
  static LoggingStartup of(Map<String, Object> fields, @Nullable Instant lastReset) {
    @Nullable Instant time = time(text(fields, "time"));
    boolean current = lastReset == null || time == null || !lastReset.isAfter(time);
    return new LoggingStartup(fields, current);
  }

  private static @Nullable Instant time(@Nullable String value) {
    if (value == null) return null;
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  /**
   * @return whether Berlioz configured logging (it was started by {@code AppInitializer}).
   */
  boolean initialised() {
    return flag(this.fields, "initialised");
  }

  /**
   * @return whether no reset was observed since the startup.
   */
  boolean current() {
    return this.current;
  }

  /**
   * @return the {@code startup-*} issues, only while the startup is current.
   */
  List<Issue> issues() {
    List<Issue> issues = new ArrayList<>();
    if (!initialised() || !this.current) return issues;
    if ("console-default".equals(text(this.fields, "configuration")) || flag(this.fields, "console-default")) {
      issues.add(new Issue("startup-console-default", Severity.WARNING));
      return issues;
    }
    for (Map<String, Object> candidate : objects(this.fields, CANDIDATES)) {
      @Nullable String outcome = text(candidate, "outcome");
      if ("failed".equals(outcome)) {
        issues.add(new Issue("startup-file-failed", Severity.WARNING).file(text(candidate, "location"), text(candidate, "path")));
      } else if ("loaded-with-errors".equals(outcome) && flag(candidate, "in-use")) {
        issues.add(new Issue("startup-file-with-errors", Severity.WARNING)
            .file(text(candidate, "location"), text(candidate, "path")).count(number(candidate, "problem-count", 0)));
      }
    }
    return issues;
  }

  /**
   * Writes the fields of the snapshot as Berlioz names them, without {@code logger-factory} unless the
   * backend is not logback, and whether the startup is current.
   */
  void write(OutputWriter out) {
    out.startObject("startup");
    Map<String, Object> fields = new LinkedHashMap<>(this.fields);
    if ("logback".equals(text(fields, "backend"))) fields.remove("logger-factory");
    List<Map<String, Object>> candidates = objects(fields, CANDIDATES);
    fields.remove(CANDIDATES);
    // In XML, the values are attributes, written before the nested objects
    values(out, fields, false);
    if (initialised()) out.field("current", this.current);
    values(out, fields, true);
    out.startArray(CANDIDATES, ContextOption.JSON_ONLY);
    for (Map<String, Object> candidate : candidates) object(out, "candidate", candidate);
    out.endArray();
    out.endObject();
  }

  private static void object(OutputWriter out, String name, Map<String, Object> fields) {
    out.startObject(name);
    values(out, fields, false);
    values(out, fields, true);
    out.endObject();
  }

  /**
   * Writes the values of the fields, or their nested objects and lists of objects.
   */
  private static void values(OutputWriter out, Map<String, Object> fields, boolean nested) {
    for (Map.Entry<String, Object> field : fields.entrySet()) {
      if (isNested(field.getValue()) == nested) value(out, field.getKey(), field.getValue());
    }
  }

  private static boolean isNested(Object value) {
    return value instanceof Map
        || (value instanceof List && !((List<?>) value).isEmpty() && ((List<?>) value).get(0) instanceof Map);
  }

  /**
   * Writes a value of the snapshot as Berlioz does: a list of objects is an array named after the key,
   * each object named after the key without its trailing "s".
   */
  @SuppressWarnings("unchecked")
  private static void value(OutputWriter out, String name, Object value) {
    if (value instanceof Map) {
      object(out, name, (Map<String, Object>) value);
    } else if (value instanceof Boolean) {
      out.field(name, (Boolean) value);
    } else if (value instanceof Long) {
      out.field(name, (Long) value);
    } else if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (!list.isEmpty() && list.get(0) instanceof Map) {
        String item = name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
        out.startArray(name, ContextOption.JSON_ONLY);
        for (Object o : list) if (o instanceof Map) object(out, item, (Map<String, Object>) o);
        out.endArray();
      } else {
        List<String> strings = new ArrayList<>();
        for (Object o : list) strings.add(String.valueOf(o));
        out.field(name, strings);
      }
    } else if (value != null) {
      out.field(name, value.toString());
    }
  }

}
