package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.JsonOutputAdapter;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Issue;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Severity;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Verdict;

/**
 * The output of {@code GetLoggingConfiguration} and the startup recorded by Berlioz: the verdict and
 * issues in every view, the details with {@code view=config}, the {@code startup-*} issues while the
 * startup is current, and no absolute path from the startup.
 */
final class LoggingConfigurationOutputTest {

  private static final Instant STARTUP = Instant.parse("2026-11-10T01:02:00Z");

  @Test
  void writesTheVerdictAndIssuesInEveryView() throws Exception {
    LoggingConfiguration c = problems();
    String summary = xml(c, startup(file("loaded"), null), false);
    assertTrue(summary.startsWith("<logging-configuration framework=\"logback\" version=\"1.5.38\" verdict=\"problems\">"), summary);
    assertTrue(summary.contains("<issue type=\"stopped-appender\" severity=\"error\" appender=\"a2\" name=\"FILE\"/>"), summary);
    assertTrue(summary.contains("<resets count=\"1\" last=\"2026-11-10T01:01:59Z\"/>"), summary);
    assertFalse(summary.contains("<appender "), "No details: " + summary);
    assertFalse(summary.contains("<startup"), summary);

    String details = xml(c, startup(file("loaded"), null), true);
    assertTrue(details.contains("<appender id=\"a2\" name=\"FILE\" class=\"ch.qos.logback.core.FileAppender\" started=\"false\" writes=\"false\" file=\"/var/log/app.log\" loggers=\"ROOT\"/>"), details);
    assertTrue(details.contains("<loggers total=\"1\"><logger name=\"ROOT\" level=\"INFO\" effective-level=\"INFO\" additive=\"true\" output=\"true\" appenders=\"a1 a2\"/></loggers>"), details);
    assertTrue(details.contains("<scan enabled=\"false\"/>"), details);
    assertTrue(details.contains("<statuses since=\"reset\" errors=\"1\" warnings=\"0\" infos=\"3\"><status level=\"ERROR\" time=\"2026-11-10T01:02:03Z\""
        + " origin=\"ch.qos.logback.core.FileAppender\" message=\"openFile failed\"><cause class=\"java.io.FileNotFoundException\" message=\"denied\"/></status></statuses>"), details);
    assertTrue(details.contains("<startup initialised=\"true\""), details);
  }

  @Test
  void writesCamelCaseJson() throws Exception {
    StringWriter json = new StringWriter();
    try (JsonOutputAdapter out = new JsonOutputAdapter(json)) {
      GetLoggingConfiguration.write(problems(), startup(file("loaded"), null), true, out);
    }
    String text = json.toString();
    assertTrue(text.contains("\"verdict\":\"problems\""), text);
    assertTrue(text.contains("\"issues\":[{\"type\":\"stopped-appender\""), text);
    assertTrue(text.contains("\"effectiveLevel\":\"INFO\""), text);
    assertTrue(text.contains("\"loggers\":{\"total\":1,\"items\":[{"), text);
    assertTrue(text.contains("\"candidates\":[{"), text);
    assertTrue(text.contains("\"inUse\":true"), text);
  }

  @Test
  void writesUnsupportedWithTheStartupOnly() throws Exception {
    LoggingConfiguration c = LoggingConfiguration.without("org.slf4j.helpers.NOPLoggerFactory", null, Verdict.UNSUPPORTED);
    Map<String, Object> fields = fields("nop", "none");
    fields.put("logger-factory", "org.slf4j.helpers.NOPLoggerFactory");
    fields.put("candidates", List.of(candidate("logback", "webinf", "config/logback.xml", "ignored", false)));
    String xml = xml(c, LoggingStartup.of(fields, null), true);
    assertEquals("<logging-configuration framework=\"org.slf4j.helpers.NOPLoggerFactory\" verdict=\"unsupported\">"
        + "<startup initialised=\"true\" framework=\"logback\" backend=\"nop\" configuration=\"none\" mode=\"test\" time=\"2026-11-10T01:02:00Z\""
        + " logger-factory=\"org.slf4j.helpers.NOPLoggerFactory\" current=\"true\">"
        + "<candidate framework=\"logback\" location=\"webinf\" path=\"config/logback.xml\" outcome=\"ignored\"/></startup></logging-configuration>",
        xml.replaceAll("\\s*<\\?xml[^>]*>\\s*", ""));
  }

  @Test
  @SuppressWarnings("unchecked")
  void reportsTheStartupWhileCurrent() {
    // A file failed and Berlioz used a later one
    Map<String, Object> fields = fields("logback", "file");
    fields.put("candidates", List.of(
        candidate("logback", "webinf", "config/logback-test.xml", "failed", false),
        candidate("logback", "webinf", "config/logback.xml", "loaded-with-errors", true)));
    ((Map<String, Object>) ((List<?>) fields.get("candidates")).get(1)).put("problem-count", 2L);
    LoggingStartup startup = LoggingStartup.of(fields, STARTUP.minusSeconds(1));
    assertTrue(startup.current());
    assertEquals(List.of("startup-file-failed", "startup-file-with-errors"), types(startup.issues()));
    Issue failed = startup.issues().get(0);
    assertEquals("webinf", failed.location);
    assertEquals("config/logback-test.xml", failed.path);
    assertEquals(2, startup.issues().get(1).count);

    // Reconfigured after startup: no issue
    LoggingStartup later = LoggingStartup.of(fields, STARTUP.plusSeconds(60));
    assertFalse(later.current());
    assertEquals(List.of(), later.issues());

    // No file could be loaded: one issue, not one per file
    Map<String, Object> console = fields("logback", "console-default");
    console.put("console-default", true);
    console.put("candidates", List.of(candidate("logback", "webinf", "config/logback.xml", "failed", false)));
    assertEquals(List.of("startup-console-default"), types(LoggingStartup.of(console, null).issues()));

    // Not started by AppInitializer
    Map<String, Object> none = new LinkedHashMap<>();
    none.put("initialised", false);
    LoggingStartup uninitialised = LoggingStartup.of(none, null);
    assertFalse(uninitialised.initialised());
    assertEquals(List.of(), uninitialised.issues());
  }

  @Test
  void addsTheStartupIssuesAfterTheErrors() throws Exception {
    Map<String, Object> fields = fields("logback", "file");
    fields.put("candidates", List.of(candidate("logback", "webinf", "config/logback-test.xml", "failed", false)));
    String xml = xml(problems(), LoggingStartup.of(fields, null), false);
    assertTrue(xml.indexOf("stopped-appender") < xml.indexOf("startup-file-failed"), xml);
    assertTrue(xml.contains("<issue type=\"startup-file-failed\" severity=\"warning\" location=\"webinf\" path=\"config/logback-test.xml\"/>"), xml);
    // Not for a framework that cannot be inspected
    String unsupported = xml(LoggingConfiguration.without("x", null, Verdict.UNSUPPORTED), LoggingStartup.of(fields, null), false);
    assertFalse(unsupported.contains("<issue"), unsupported);
  }

  @Test
  void dropsTheLoggerFactoryWithLogback() throws Exception {
    Map<String, Object> fields = fields("logback", "file");
    fields.put("logger-factory", "ch.qos.logback.classic.LoggerContext");
    Map<String, Object> hint = new LinkedHashMap<>();
    hint.put("name", "logback.configurationFile");
    hint.put("value", "[webinf]/config/custom.xml");
    fields.put("native-setting", hint);
    String xml = xml(problems(), LoggingStartup.of(fields, null), true);
    assertFalse(xml.contains("logger-factory"), xml);
    assertTrue(xml.contains("<native-setting name=\"logback.configurationFile\" value=\"[webinf]/config/custom.xml\"/>"), xml);
  }

  /** Berlioz's snapshot of this JVM: not started by AppInitializer. */
  @Test
  void readsTheSnapshotOfBerlioz() {
    LoggingStartup startup = LoggingStartup.read(null);
    assertFalse(startup.initialised());
  }

  private static LoggingConfiguration problems() {
    LoggingConfiguration.Builder b = new LoggingConfiguration.Builder("logback", "1.5.38");
    b.verdict = Verdict.PROBLEMS;
    b.issues.add(new Issue("stopped-appender", Severity.ERROR).appender("a2", "FILE"));
    LoggingConfiguration.Appender file = new LoggingConfiguration.Appender("a2", "FILE", "ch.qos.logback.core.FileAppender",
        false, false, false, "/var/log/app.log", null);
    file.loggers.add("ROOT");
    b.appenders.add(file);
    b.loggers.add(new LoggingConfiguration.Logger("ROOT", EventLevel.INFO, EventLevel.INFO, true, List.of("a1", "a2"), true));
    b.loggerTotal = 1;
    b.statusesSince = "reset";
    b.errors = 1;
    b.infos = 3;
    b.statuses.add(new LoggingConfiguration.Status("ERROR", Instant.parse("2026-11-10T01:02:03Z").toEpochMilli(),
        "ch.qos.logback.core.FileAppender", "openFile failed", List.of(new LoggingConfiguration.Cause("java.io.FileNotFoundException", "denied"))));
    b.resets = 1;
    b.lastReset = STARTUP.minusSeconds(1);
    return b.build();
  }

  private static LoggingStartup startup(Map<String, Object> candidate, Instant lastReset) {
    Map<String, Object> fields = fields("logback", "file");
    fields.put("candidates", List.of(candidate));
    return LoggingStartup.of(fields, lastReset);
  }

  private static Map<String, Object> fields(String backend, String configuration) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("initialised", true);
    fields.put("framework", "logback");
    fields.put("backend", backend);
    fields.put("configuration", configuration);
    fields.put("mode", "test");
    fields.put("time", STARTUP.toString());
    return fields;
  }

  private static Map<String, Object> file(String outcome) {
    return candidate("logback", "webinf", "config/logback.xml", outcome, true);
  }

  private static Map<String, Object> candidate(String framework, String location, String path, String outcome, boolean inUse) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("framework", framework);
    c.put("location", location);
    c.put("path", path);
    c.put("outcome", outcome);
    if (inUse) c.put("in-use", true);
    return c;
  }

  private static String xml(LoggingConfiguration c, LoggingStartup startup, boolean details) throws Exception {
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      GetLoggingConfiguration.write(c, startup, details, out);
    }
    return xml.toString().replaceAll("\\s*<\\?xml[^>]*>\\s*", "");
  }

  private static List<String> types(List<Issue> issues) {
    return issues.stream().map(i -> i.type).collect(java.util.stream.Collectors.toList());
  }

}
