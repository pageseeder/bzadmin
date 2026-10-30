package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The Logs page renders the output of {@code ListLogFiles}, {@code GetLogEntries} and
 * {@code GetRecentEvents}: the Recent and Files views, files selected by ID, the cursor for new
 * lines, the recent events, their logging framework and capture threshold, and expected failures.
 */
final class LogsXsltTest {

  private static final String FILES = "<log-files origin='detected'><log-directory name='logs' path='/var/log' total='1' truncated='false'>"
      + "<log-file id='0123456789abcdef' name='app.log' size='2048' datetime='2026-10-23T01:02:03Z'/>"
      + "</log-directory></log-files>";

  private static final String RECENT = "<recent-logs backend='logback' version='1.5.37' threshold='INFO' root-level='INFO' capacity='256' captured='0'/>";

  @Test
  void selectsLogFilesById() throws Exception {
    String html = page("bzadmin-logs", "files", "logs-files", "ok", FILES);
    assertTrue(html.contains("<form id=\"switch-logs\" action=\"/ctx/bzadmin/logs/raw.html\">"), html);
    assertTrue(html.contains("<select id=\"switch-logs-file\" class=\"input-group-field\" name=\"id\">"), "Opens the file without script");
    assertTrue(html.contains("<option value=\"0123456789abcdef\">app.log (2 KB)</option>"), html);
    assertTrue(html.contains("<a href=\"/ctx/bzadmin/logs/raw.html?id=0123456789abcdef\" class=\"log-file-link\" data-id=\"0123456789abcdef\">app.log</a>"), html);
    assertTrue(html.contains("<td title=\"/var/log\">logs</td>"), html);
    assertTrue(html.contains("<td data-sort=\"2048\">2 KB</td>"), html);
    assertTrue(html.contains("The directories of the file appenders of the logging framework."), html);
  }

  @Test
  void showsTheRecentEventsOrTheFilesView() throws Exception {
    String recent = logsPage("bzadmin-logs", "", RECENT, FILES);
    assertTrue(recent.contains("<a href=\"/ctx/bzadmin/logs.html\" class=\"choice active\" aria-current=\"page\">Recent</a>"), recent);
    assertTrue(recent.contains("<a href=\"/ctx/bzadmin/logs.html?view=files\" class=\"choice\">Files</a>"), recent);
    assertTrue(recent.contains("set-threshold"), recent);
    assertFalse(recent.contains("switch-logs"), recent);

    String files = logsPage("bzadmin-logs", "files", RECENT, FILES);
    assertTrue(files.contains("<a href=\"/ctx/bzadmin/logs.html\" class=\"choice\">Recent</a>"), files);
    assertTrue(files.contains("<a href=\"/ctx/bzadmin/logs.html?view=files\" class=\"choice active\" aria-current=\"page\">Files</a>"), files);
    assertTrue(files.contains("switch-logs"), files);
    assertFalse(files.contains("set-threshold"), files);

    // A log file on its own page is in the Files view
    String raw = page("bzadmin-logs-raw", "", "logs-raw", "ok", "<no-log id='0123456789abcdef'/>");
    assertTrue(raw.contains("<a href=\"/ctx/bzadmin/logs.html?view=files\" class=\"choice active\" aria-current=\"page\">Files</a>"), raw);
  }

  @Test
  void explainsHowToRegisterLogFiles() throws Exception {
    String none = page("bzadmin-logs", "files", "logs-files", "ok", "<log-files origin='none'/>");
    assertTrue(none.contains("list their directories or files in the global property <code>berlioz.admin.logs</code>"), none);
    assertFalse(none.contains("switch-logs"), none);
    String configured = page("bzadmin-logs", "files", "logs-files", "ok", "<log-files origin='configured'/>");
    assertTrue(configured.contains("No log files in the directories and files of <code>berlioz.admin.logs</code>"), configured);
    assertTrue(configured.contains("The directories and files listed in the global property <code>berlioz.admin.logs</code>."), configured);
  }

  @Test
  void reportsATruncatedDirectory() throws Exception {
    String html = page("bzadmin-logs", "files", "logs-files", "ok",
        "<log-files origin='configured'><log-directory name='logs' path='/var/log' total='1001' truncated='true'>"
        + "<log-file id='0123456789abcdef' name='app.log' size='1' datetime='2026-10-23T01:02:03Z'/>"
        + "</log-directory></log-files>");
    assertTrue(html.contains("Only the 1 most recent of the 1001 log files of <i>logs</i> are listed: open the others by name, <code>/ctx/bzadmin/logs/raw.html?name=logs/…</code>"), html);
  }

  @Test
  void rendersARawLogWithItsCursor() throws Exception {
    String html = page("bzadmin-logs-raw", "logs-raw", "ok",
        "<log id='0123456789abcdef' name='app.log' size='20' truncated='false' reset='false' cursor='00ff.12.3'>"
        + "<line position='0' n='1' level='WARN'>&lt;script&gt;one WARN</line><line position='9' n='2' incomplete='true'>two</line></log>");
    assertTrue(html.contains("Raw log: <a href=\"/ctx/bzadmin/logs/raw.html?name=app.log\"><i>app.log</i></a>"), html);
    assertTrue(html.contains("<form id=\"follow-log\" action=\"/ctx/bzadmin/logs/raw.html\">"), html);
    assertTrue(html.contains("name=\"id\" value=\"0123456789abcdef\""), html);
    assertTrue(html.contains("name=\"cursor\" value=\"00ff.12.3\""), html);
    assertTrue(html.contains("data-reset=\"false\""), html);
    assertTrue(html.contains("<pre class=\"rawlog\" data-reset=\"false\"><code><span class=\"warn\" data-position=\"0\" data-n=\"1\">&lt;script&gt;one WARN</span>\n"
        + "<span class=\"warn\" data-position=\"9\" data-n=\"2\" data-incomplete=\"true\">two</span>\n</code></pre>"), "One span per line, the numbers in data-n: " + html);
    assertTrue(html.contains("&lt;script&gt;one WARN"), "Escaped");
    assertTrue(html.indexOf("</pre>") < html.indexOf("id=\"follow-log\""), "The new lines are shown under the lines");
    // The first line of the file: the form for earlier lines is hidden until the page drops lines
    assertTrue(html.contains("<form id=\"earlier-log\" action=\"/ctx/bzadmin/logs/raw.html\" hidden>"), html);
  }

  @Test
  void rendersARawLogWithEarlierLines() throws Exception {
    String html = page("bzadmin-logs-raw", "logs-raw", "ok",
        "<log id='0123456789abcdef' name='app.log' size='20' truncated='true' reset='false' cursor='00ff.20.0' before='00ff.12.0'>"
        + "<line position='12'>last</line></log>");
    assertTrue(html.contains("<form id=\"earlier-log\" action=\"/ctx/bzadmin/logs/raw.html\">"), html);
    assertTrue(html.contains("name=\"before\" value=\"00ff.12.0\""), html);
    assertTrue(html.contains("Earlier lines of the log file are not shown (line numbers are not available for large files)."), html);
    assertTrue(html.indexOf("id=\"earlier-log\"") < html.indexOf("<pre"), "Above the lines");
    assertFalse(html.contains("Only the last"), html);
    // Lines dropped but no line to read before (a line longer than a read)
    String bounded = page("bzadmin-logs-raw", "logs-raw", "ok",
        "<log id='0123456789abcdef' name='app.log' size='20' truncated='true' reset='false' cursor='00ff.20.0'><line position='12'>last</line></log>");
    assertTrue(bounded.contains("Only the last 1 lines of this log file are shown."), bounded);
  }

  @Test
  void reportsAnUnknownOrBusyLog() throws Exception {
    String missing = page("bzadmin-logs-raw", "logs-raw", "not-found", "<no-log id='0123456789abcdef'/>");
    assertTrue(missing.contains("No such log file"), missing);
    String byName = page("bzadmin-logs-raw", "logs-raw", "not-found", "<no-log name='app.2026-10-23.log'/>");
    assertTrue(byName.contains("Raw log: <i>app.2026-10-23.log</i>"), byName);
    assertFalse(missing.contains("follow-log"), missing);
    String busy = page("bzadmin-logs-raw", "logs-raw", "service-unavailable", "<no-log id='0123456789abcdef' reason='busy'/>");
    assertTrue(busy.contains("Too many log files are being read"), busy);
    assertFalse(busy.contains("No such log file"), busy);
  }

  @Test
  void rendersTheRecentEvents() throws Exception {
    String html = page("bzadmin-logs-recent", "logs-recent", "ok",
        "<recent-logs backend='logback' threshold='DEBUG' root-level='INFO' capacity='256' captured='300'>"
        + "<event level='WARN' logger='org.example.Service' thread='main' timestamp='1792710123456' datetime='2026-10-22T23:02:03.456Z' message='&lt;script&gt;'>"
        + "<throwable class='java.lang.IllegalStateException' message='failed'><step>a.B.c(B.java:1)</step>"
        + "<cause class='java.io.IOException' omitted='3'><step>b.C.d(C.java:2)</step></cause></throwable></event>"
        + "<event level='INFO' logger='org.example.Service' thread='main' timestamp='1792710123457' datetime='2026-10-22T23:02:03.457Z' message='Hello'/>"
        + "</recent-logs>");
    assertTrue(html.replaceAll("\\s+", " ").contains("Captured from <b>logback</b> by an appender that bzadmin attaches to the root logger"), html);
    assertTrue(html.contains("<dt>Captured</dt><dd class=\"key-metric-value\">300</dd>"), "The counts are a key metric");
    assertFalse(html.contains("2 shown"), html);
    assertTrue(html.contains("its level (root logger) is <b>INFO</b>"), html);
    assertTrue(html.contains("Events below INFO are not logged, so not captured."), html);
    assertTrue(html.contains("action=\"/ctx/bzadmin/logs/recent/threshold.xml\""), html);
    assertTrue(html.contains("<option value=\"DEBUG\" selected>Debug</option>"), html);
    assertTrue(html.contains("<option value=\"TRACE\">Trace</option>"), html);
    assertTrue(html.contains("&lt;script&gt;"), "Escaped");
    assertFalse(html.contains("<script>"), html);
    assertTrue(html.contains("<summary>java.lang.IllegalStateException: failed</summary>"), html);
    assertTrue(html.contains("    at a.B.c(B.java:1)\nCaused by: java.io.IOException\n    at b.C.d(C.java:2)\n    ... 3 more\n"), html);
    assertTrue(html.contains("<tr class=\"info\">"), html);

    String above = page("bzadmin-logs-recent", "logs-recent", "ok",
        "<recent-logs backend='logback' threshold='WARN' root-level='INFO' capacity='256' captured='0'/>");
    assertFalse(above.contains("are not logged"), above);
    assertTrue(logsPage("bzadmin-logs", "", RECENT, FILES).contains("Captured from <b>logback 1.5.37</b>"), "The version of logback");
    assertTrue(above.contains("<option value=\"WARN\" selected>Warnings</option>"), above);
  }

  @Test
  void abbreviatesTheLoggersOfKnownPackages() throws Exception {
    String html = page("bzadmin-logs-recent", "logs-recent", "ok",
        "<recent-logs backend='logback' threshold='INFO' root-level='INFO' capacity='256' captured='4'>"
        + "<event level='INFO' logger='org.pageseeder.berlioz.Init' thread='main' timestamp='1792710123000' datetime='2026-10-22T23:02:03Z' message='a'/>"
        + "<event level='INFO' logger='org.pageseeder.bzadmin.guard.Check' thread='main' timestamp='1792710123001' datetime='2026-10-22T23:02:03Z' message='b'/>"
        + "<event level='INFO' logger='org.hibernate.Version' thread='main' timestamp='1792710123002' datetime='2026-10-22T23:02:03Z' message='c'/>"
        + "<event level='INFO' logger='com.allette.Service' thread='main' timestamp='1792710123003' datetime='2026-10-22T23:02:03Z' message='d'/>"
        + "</recent-logs>");
    assertTrue(html.contains("<abbr title=\"org.pageseeder.berlioz\">[berlioz]</abbr>.Init</td>"), html);
    assertTrue(html.contains("<abbr title=\"org.pageseeder.bzadmin\">[bzadmin]</abbr>.guard.Check</td>"), "Loggers are not normalized like generators: " + html);
    assertTrue(html.contains("<abbr title=\"org.hibernate\">[hibernate]</abbr>.Version</td>"), html);
    assertTrue(html.contains("(main)\">com.allette.Service</td>"), "Other packages in full: " + html);
  }

  @Test
  void summarisesTheRecentEventsInKeyMetrics() throws Exception {
    String html = page("bzadmin-logs-recent", "logs-recent", "ok",
        "<recent-logs backend='logback' threshold='INFO' root-level='INFO' capacity='256' captured='1300'>"
        + "<event level='ERROR' logger='org.example.Old' timestamp='1792710123000' datetime='2026-10-22T23:02:03Z' message='a'/>"
        + "<event level='ERROR' logger='org.example.Last' timestamp='1792710124000' datetime='2026-10-22T23:02:04Z' message='b'/>"
        + "<event level='WARN' logger='org.example.Service' timestamp='1792710125000' datetime='2026-10-22T23:02:05Z' message='c'/>"
        + "</recent-logs>");
    assertTrue(html.contains("<div class=\"key-metric warning\"><dt>Errors</dt><dd class=\"key-metric-value\">2</dd>"
        + "<dd class=\"key-metric-detail\">In the last 3 events</dd>"), html);
    assertTrue(html.contains("<dt>Warnings</dt><dd class=\"key-metric-value\">1</dd>"), html);
    assertTrue(html.matches("(?s).*<dt>Last error</dt><dd class=\"key-metric-value\">\\d+ \\w+ ago</dd><dd class=\"key-metric-detail\">Last</dd>.*"), html);
    assertTrue(html.contains("<dt>Captured</dt><dd class=\"key-metric-value\">1,300</dd>"
        + "<dd class=\"key-metric-detail\">Since the application started; the last 256 are kept</dd>"), html);

    String errors = page("bzadmin-logs-recent", "logs-recent", "ok",
        "<recent-logs backend='logback' threshold='ERROR' root-level='INFO' capacity='256' captured='0'/>");
    assertTrue(errors.contains("<div class=\"key-metric\"><dt>Errors</dt><dd class=\"key-metric-value muted\">0</dd>"), errors);
    assertTrue(errors.contains("<dt>Warnings</dt><dd class=\"key-metric-value muted\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></dd>"
        + "<dd class=\"key-metric-detail\">Not captured: the threshold is ERROR</dd>"), errors);
    assertTrue(errors.contains("<dt>Last error</dt><dd class=\"key-metric-value\">None</dd>"), errors);
  }

  @Test
  void summarisesTheLogFilesInKeyMetrics() throws Exception {
    String html = logsPage("bzadmin-logs", "files", RECENT, "<log-files origin='detected'>"
        + "<log-directory name='logs' path='/var/log' total='3' truncated='true'>"
        + "<log-file id='0123456789abcdef' name='app.log' size='2048' datetime='2026-10-23T01:02:03Z'/>"
        + "<log-file id='1123456789abcdef' name='app.2026-10-20.log' size='3000000' datetime='2026-10-20T23:59:59Z'/>"
        + "</log-directory></log-files>");
    assertTrue(html.contains("<dt>Log files</dt><dd class=\"key-metric-value\">2</dd><dd class=\"key-metric-detail\">In 1 directory, not all listed</dd>"), html);
    assertTrue(html.contains("<dt>Total size</dt><dd class=\"key-metric-value\">3 MB</dd>"), html);
    assertTrue(html.contains("<dt>Largest</dt><dd class=\"key-metric-value\">3 MB</dd><dd class=\"key-metric-detail\">app.2026-10-20.log</dd>"), html);
    assertTrue(html.matches("(?s).*<dt>Last modified</dt><dd class=\"key-metric-value\">\\d+ \\w+ ago</dd><dd class=\"key-metric-detail\">app.log</dd>.*"), html);
    assertFalse(logsPage("bzadmin-logs", "", RECENT, FILES).contains("<dt>Log files</dt>"), "Not in the Recent view");
  }

  @Test
  void explainsUnsupportedRecentEvents() throws Exception {
    String html = page("bzadmin-logs", "logs-recent", "ok", "<no-recent-logs backend='org.slf4j.helpers.NOPLoggerFactory'/>");
    assertTrue(html.contains("Recent events need logback: the logging framework of this application is <code>org.slf4j.helpers.NOPLoggerFactory</code>."), html);
    assertFalse(html.contains("set-threshold"), html);
    String version = page("bzadmin-logs", "logs-recent", "ok", "<no-recent-logs backend='org.eclipse.jetty.logging.JettyLoggerFactory' version='10.0.26'/>");
    assertTrue(version.contains("is <code>org.eclipse.jetty.logging.JettyLoggerFactory</code> (version 10.0.26)."), version);
  }

  private static String page(String service, String name, String status, String content) throws Exception {
    return page(service, "", name, status, content);
  }

  private static String page(String service, String view, String name, String status, String content) throws Exception {
    return transform(service, view, content(name, status, content));
  }

  /** The Logs page with both of its generators. */
  private static String logsPage(String service, String view, String recent, String files) throws Exception {
    return transform(service, view, content("logs-files", "ok", files) + content("logs-recent", "ok", recent));
  }

  private static String content(String name, String status, String content) {
    return "<content generator='org.pageseeder.bzadmin.log.GetLogEntries' name='" + name + "' target='main' status='" + status + "'>"
        + content + "</content>";
  }

  private static String transform(String service, String view, String contents) throws Exception {
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/logs.html'/><path context='/ctx' info='/bzadmin/logs' extension='.html'/>"
        + "<http-parameters>" + (view.isEmpty() ? "" : "<parameter name='view'>" + view + "</parameter>") + "</http-parameters>"
        + "<berlioz version='0.14.4' mode='test'/></header>"
        + contents + "</root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}
