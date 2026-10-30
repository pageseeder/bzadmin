package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The Application and System pages render the summaries of {@code GetApplicationSummary} and
 * {@code GetSystemSummary}, in fully configured and minimal hosts: optional absence is not an error.
 */
final class DiagnosticsXsltTest {

  @Test
  void summarisesAFullyConfiguredApplication() throws Exception {
    String html = page("bzadmin-application", "summary",
        "<application-summary mode='test'>"
        + "<component name='bzadmin' version='0.14.0-beta.1'/><component name='berlioz' version='0.14.4'/>"
        + "<component name='java' product='OpenJDK 64-Bit Server VM' vendor='Eclipse Adoptium' version='11.0.32+1'/>"
        + "<component name='xslt' product='SAXON' version='HE 12.10' vendor='Saxonica' xslt-version='3.0' factory='net.sf.saxon.TransformerFactoryImpl'/>"
        + "<component name='container' product='jetty/10.0.26' servlet-api='4.0'/>"
        + "<logging backend='logback' recent-events='true' log-files='backend'/>"
        + "<cache-provider id='ehcache2' label='EHCache 2.11.0.3.31' managers='2'/>"
        + "<cache-provider id='jcache' label='JCache' managers='1'/>"
        + "<access roles='true' fallback-login='true'/>"
        + "<status services='ready' configuration='loaded' properties='12' xslt-cache='manual'/>"
        + "</application-summary>");
    assertTrue(html.contains("<dd class=\"key-metric-value\">test</dd>"), html);
    assertTrue(html.contains("<th>Berlioz administration</th>"), html);
    assertTrue(html.contains("0.14.0-beta.1"), html);
    assertTrue(html.contains("SAXON HE 12.10 (Saxonica) <span class=\"tag\">XSLT 3.0</span>"), html);
    assertTrue(html.contains("<span class=\"muted\">jetty/10.0.26</span> <span class=\"tag\">Servlet 4.0</span>"), html);
    assertTrue(html.contains("Recent event capture is available."), html);
    assertTrue(html.contains("2 managers detected."), html);
    assertTrue(html.contains("1 manager detected."), html);
    assertTrue(html.contains("Host access attribute, container roles, fallback login"), html);
    assertTrue(html.contains("12 properties"), html);
    assertFalse(html.contains("Unknown"), html);
    assertTrue(html.indexOf("id=\"application-metrics\"") < html.indexOf("Runtime components"), html);
    assertTrue(html.indexOf("Runtime components") < html.indexOf("Integrations"), html);
    assertTrue(html.contains("<summary>Technical details</summary>"), html);
    assertTrue(html.contains("Configured mechanisms do not identify which one authorised this request."), html);
    assertFalse(html.contains("Application status"), html);
    for (String metric : new String[]{"mode", "services", "configuration", "berlioz"}) {
      assertTrue(html.contains("popovertarget=\"application-info-" + metric + "\""), html);
      assertTrue(html.contains("id=\"application-info-" + metric + "\" class=\"info-popover\" popover=\"\""), html);
    }
    assertTrue(html.contains("<dd class=\"key-metric-value\">0.14.4</dd>"), html);
    assertEquals(2, html.split("name=\"ok\" class=\"metric-status-icon\" aria-hidden=\"true\"", -1).length - 1, html);
    assertFalse(html.contains("Last load"), html);
    assertFalse(html.contains(">XSLT cache</th>"), html);
  }

  @Test
  void summarisesAMinimalApplication() throws Exception {
    String html = page("bzadmin-application", "summary",
        "<application-summary mode='default'>"
        + "<component name='bzadmin'/><component name='berlioz' version='0.14.4'/>"
        + "<component name='java' version='11.0.32+1'/>"
        + "<logging backend='org.slf4j.helpers.NOPLoggerFactory' recent-events='false' log-files='property'/>"
        + "<access roles='false' fallback-login='false'/>"
        + "<status services='reload-failed' configuration='empty' properties='0' xslt-cache='auto'/>"
        + "</application-summary>");
    assertTrue(html.contains("<tr data-component=\"bzadmin\"><th>Berlioz administration</th><td>Unknown</td>"), html);
    assertTrue(html.contains("Unknown: the container does not identify itself to Berlioz"), html);
    assertTrue(html.contains("None: Berlioz cannot create its transformer factory"), html);
    assertTrue(html.contains("This backend does not provide recent event capture."), html);
    assertTrue(html.contains("Log files may still be available."), html);
    assertTrue(html.contains("No cache provider detected"), html);
    assertTrue(html.contains("class=\"key-metric warning\"><dt>Services"), html);
    assertTrue(html.contains("class=\"key-metric warning\"><dt>Configuration"), html);
    assertEquals(2, html.split("name=\"warning\" class=\"metric-status-icon\" aria-hidden=\"true\"", -1).length - 1, html);
    assertTrue(html.contains("<p>Host access attribute.</p>"), html);
    assertTrue(html.contains("Berlioz keeps running the previous valid registry."), html);
    assertTrue(html.contains("The summary cannot distinguish an empty file from a load failure."), html);
  }

  @Test
  void keepsPendingServicesAndMissingConfigurationNeutral() throws Exception {
    String html = page("bzadmin-application", "summary",
        "<application-summary mode='test'><status services='pending' configuration='none'/></application-summary>");
    assertFalse(html.contains("metric-status-icon"), html);
    assertFalse(html.contains("key-metric warning"), html);
    assertTrue(html.contains(">Pending</dd>"), html);
    assertTrue(html.contains(">No file</dd>"), html);
    assertTrue(html.contains("title=\"Not available\""), html);
  }

  /**
   * ListLibraries writes each manifest header as an {@code attribute} with its name and value: the
   * table shows the key headers, and the dialog of each library its whole manifest.
   */
  @Test
  void listsTheLibrariesWithTheirManifest() throws Exception {
    String html = page("bzadmin-application", "libraries",
        "<libraries>"
        + "<library file='saxon-he-12.10.jar' name='saxon-he' version='12.10'>"
        + "<attribute name='bundle-license' value='http://www.apache.org/licenses/LICENSE-2.0.txt'/>"
        + "<attribute name='implementation-title' value='Saxon-HE'/>"
        + "<attribute name='implementation-vendor' value='Saxonica'/>"
        + "<attribute name='implementation-version' value='12.10 build 7'/>"
        + "<attribute name='manifest-version' value='1.0'/>"
        + "</library>"
        + "<library file='activation.jar' name='activation'>"
        + "<attribute name='bundle-license' value='https://github.com/javaee/activation/blob/master/LICENSE.txt'/>"
        + "</library>"
        + "<library file='caffeine-3.1.8.jar' name='caffeine' version='3.1.8'>"
        + "<attribute name='automatic-module-name' value='com.github.benmanes.caffeine'/>"
        + "</library>"
        + "<library file='checker-qual-3.37.0.jar' name='checker-qual' version='3.37.0'>"
        + "<attribute name='automatic-module-name' value='org.checkerframework.checker.qual'/>"
        + "<attribute name='bundle-symbolicname' value='checker-qual'/>"
        + "</library>"
        + "<library file='plain.jar' name='plain'/>"
        + "</libraries>");
    assertTrue(html.contains("<td data-sort=\"Saxon-HE\">Saxon-HE<br><small class=\"library-file\">saxon-he-12.10.jar</small></td>"), html);
    assertTrue(html.contains("<abbr title=\"12.10 build 7\">12.10</abbr>"), html);
    assertTrue(html.contains("<td>Saxonica</td>"), html);
    assertTrue(html.contains("<abbr title=\"http://www.apache.org/licenses/LICENSE-2.0.txt\">Apache-2.0</abbr>"), html);
    assertTrue(html.contains(">github.com</a>"), html);
    assertTrue(html.contains("<td data-sort=\"plain\">plain<br>"), html);
    // Without vendor, the namespace of the module name; without anything, an en dash
    assertTrue(html.contains(">github.com/benmanes</span></td>"), html);
    assertTrue(html.contains(">checkerframework.org</span></td>"), html);
    assertTrue(html.contains("<td>–</td>"), html);
    assertFalse(html.contains(">--<"), html);
    // The whole manifest, in the dialog the button opens
    Matcher button = Pattern.compile("class=\"button[^\"]*\" commandfor=\"(library-[^\"]+)\" command=\"show-modal\" aria-label=\"Show manifest of Saxon-HE\"").matcher(html);
    assertTrue(button.find(), html);
    assertTrue(html.contains("<dialog id=\"" + button.group(1) + "\" class=\"dialog library-dialog\" aria-labelledby=\"" + button.group(1) + "-title\">"), html);
    assertTrue(html.contains("<h2 id=\"" + button.group(1) + "-title\" class=\"dialog-title\">Saxon-HE "), html);
    assertTrue(html.contains("<td>implementation-title</td><td>Saxon-HE</td>"), html);
    assertTrue(html.contains("<td>manifest-version</td><td>1.0</td>"), html);
    assertTrue(html.contains("No manifest, or a manifest without main attributes."), html);
    assertFalse(html.contains("attribute-name"), html);
  }

  /** The CPU, threads and memory views of the same summary, each with its key metrics. */
  private static final String JVM_SUMMARY = "<system-summary processors='8'>"
        + "<runtime start='2026-10-23T01:02:03Z' uptime-ms='90061000' load-average='1.25'/>"
        + "<cpu process-load='0.021' system-load='0.15' process-time-ms='5230000'/>"
        + "<memory-area type='heap' init='1000' used='52428800' committed='268435456' max='4294967296'/>"
        + "<memory-area type='non-heap' used='61865984' committed='66650112'/>"
        + "<memory-pool name='G1 Eden Space' type='heap' used='23068672' committed='155189248' peak-used='23068672'/>"
        + "<memory-pool name='Metaspace' type='non-heap' used='40000000' committed='41000000'/>"
        + "<garbage-collector name='G1 Young Generation' collections='3' time-ms='1200' pools='G1 Eden Space,G1 Survivor Space'/>"
        + "<threads live='24' daemon='20' peak='26' started='40' new='0' runnable='6' blocked='1' waiting='10' timed-waiting='7' terminated='0' deadlocked='0'/>"
        + "</system-summary>";

  /** The key metrics of a page, as label, value and detail, without the info popovers. */
  private static String metrics(String html) {
    Matcher row = Pattern.compile("<dl class=\"key-metrics\">(.*?)</dl>", Pattern.DOTALL).matcher(html);
    assertTrue(row.find(), html);
    return row.group(1).replaceAll("<p id=\"system-info-[^\"]+\" class=\"info-popover\" popover=\"\">.*?</p>", "")
        .replaceAll(" <button type=\"button\" class=\"info-button[^>]*><svg-icon name=\"info-circle\"></svg-icon></button>", "")
        // Meter label associations are checked by OverlayXslt; keep value snapshots independent of IDs.
        .replaceAll("<span id=\"metric-label-[^\"]+\">([^<]+)</span>", "$1")
        .replaceAll(" aria-labelledby=\"metric-label-[^\"]+\"", "");
  }

  @Test
  void summarisesTheGeneratorStatisticsInKeyMetrics() throws Exception {
    String html = page("bzadmin-application", "statistics",
        "<statistics since='2026-10-27T10:00:00+11:00'>"
        + "<statistic generator='org.pageseeder.bzadmin.guard.berlioz.generator.GetGlobalConfig' count='12' avg-etag='10' avg-process='1500'>"
        + "<status ok='10' bad_request='2'/></statistic>"
        + "<statistic generator='org.example.SlowSearch' count='5' avg-etag='10' avg-process='250000'>"
        + "<status ok='3' internal_server_error='1' not_found='1'/></statistic>"
        + "</statistics>");
    assertTrue(html.contains("<dl class=\"key-metrics\" id=\"statistics-metrics\">"), html);
    assertTrue(html.contains("<dt>Generator calls</dt><dd class=\"key-metric-value\">17</dd><dd class=\"key-metric-detail\">by 2 generators</dd>"), html);
    assertTrue(html.contains("<div class=\"key-metric warning\"><dt>Server errors</dt><dd class=\"key-metric-value\">1</dd>"
        + "<dd class=\"key-metric-detail\">2 bad requests, 1 not found</dd>"), html);
    assertTrue(html.contains("<dt>Slowest generator</dt><dd class=\"key-metric-value\">250.0 ms</dd><dd class=\"key-metric-detail\">On average: org.example.SlowSearch</dd>"), html);
    assertTrue(html.contains("<div class=\"key-metric warning\"><dt>Slow generators</dt><dd class=\"key-metric-value\">1</dd>"), html);
    // Zero values of the table are muted
    String zero = page("bzadmin-application", "statistics",
        "<statistics since='2026-10-27T10:00:00+11:00'>"
        + "<statistic generator='org.example.Fast' count='1' min-etag='0' max-etag='0' avg-etag='0' min-process='0' max-process='12' avg-process='12'>"
        + "<status ok='1'/></statistic>"
        + "</statistics>");
    assertTrue(zero.contains("<td class=\"muted\">0</td>"), zero);
    assertTrue(zero.contains("<td>12</td>"), zero);
    assertTrue(zero.contains("<td class=\"status muted\">0</td>"), zero);

    String empty = page("bzadmin-application", "statistics", "<statistics since='2026-10-27T10:00:00+11:00'/>");
    assertTrue(empty.contains("<dt>Slowest generator</dt><dd class=\"key-metric-value muted\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></dd>"), empty);
    assertTrue(empty.contains("<div class=\"key-metric\"><dt>Server errors</dt><dd class=\"key-metric-value muted\">0</dd>"), empty);
  }

  @Test
  void showsTheCpuByDefault() throws Exception {
    String html = page("bzadmin-system", "system-summary", JVM_SUMMARY);
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">CPU</a>"), html);
    assertTrue(html.contains("href=\"/ctx/bzadmin/x.html?view=threads\" class=\"choice\">Threads</a>"), html);
    assertFalse(html.contains("Overview"), html);
    assertEquals(""
        + "<div class=\"key-metric\" id=\"metric-cpu\"><dt>Process CPU</dt><dd class=\"key-metric-value\">2.1%</dd>"
        + "<dd class=\"key-metric-meter\"><meter min=\"0\" max=\"1\" high=\"0.8\" optimum=\"0\" value=\"0.021\"></meter></dd><dd class=\"key-metric-detail\">Machine 15.0%</dd></div>"
        + "<div class=\"key-metric\" id=\"metric-load-average\"><dt>Load average</dt><dd class=\"key-metric-value\">1.25</dd>"
        + "<dd class=\"key-metric-meter\"><meter min=\"0\" max=\"8\" high=\"6.4\" optimum=\"0\" value=\"1.25\"></meter></dd><dd class=\"key-metric-detail\">8 processors</dd></div>"
        + "<div class=\"key-metric\" id=\"metric-cpu-time\"><dt>CPU time</dt><dd class=\"key-metric-value\">1h 27min</dd><dd class=\"key-metric-detail\">Average 0.7% since start</dd></div>"
        + "<div class=\"key-metric\" id=\"metric-gc-time\"><dt>GC time</dt><dd class=\"key-metric-value\">0.0%</dd><dd class=\"key-metric-detail\">3 collections, 1,200 ms</dd></div>", metrics(html));
    // Each metric with its explanation
    for (String name : new String[]{"cpu-process", "load-average", "cpu-process-time", "gc-time"}) {
      assertTrue(html.contains("<p id=\"system-info-" + name + "\" class=\"info-popover\" popover=\"\">"), name);
    }
    assertTrue(html.contains("<span class=\"uptime\">JVM up <time datetime=\"PT90061S\"><span aria-hidden=\"true\">1d 1h</span><span class=\"visually-hidden\">1 day, 1 hour</span></time></span>"), html);
    assertFalse(html.contains("system-info-uptime"), html);
    assertFalse(html.contains("title=\"Started"), html);
    assertTrue(html.contains("data-href=\"/ctx/bzadmin/x.html?view=cpu\" id=\"refresh-metrics\""), html);
    // No table repeating the metrics, the live chart
    assertFalse(html.contains("cpu-table"), html);
    assertTrue(html.contains("id=\"cpu-live\""), html);
    assertTrue(html.contains("data-href=\"/ctx/bzadmin/system/cpu/sample.xml\""), html);
    assertTrue(html.contains("<line-chart series=\"3\""), html);
    assertTrue(html.contains("aria-details=\"cpu-current-values\" aria-describedby=\"cpu-history\""), html);
    assertTrue(html.contains("<p id=\"cpu-history\" class=\"chart-history\">0 samples</p>"), html);
    assertTrue(html.contains("data-history=\"min\""), html);
    assertTrue(html.contains("data-history=\"max\""), html);
    assertFalse(html.contains("See <a href=\"#cpu-current-values"), html);
    assertFalse(html.contains("chart-history-details"), html);
    assertFalse(html.contains("id=\"threads-details\""), html);
    assertTrue(page("bzadmin-system", "system-summary", JVM_SUMMARY, "cpu").contains("class=\"choice active\" aria-current=\"page\">CPU</a>"));
  }

  @Test
  void expandsCompactUptimeUnits() throws Exception {
    String[][] cases = {
        {"0", "PT0S", "0 seconds", "0s"},
        {"999", "PT0S", "0 seconds", "0s"},
        {"61000", "PT61S", "1 minute, 1 second", "1min 1s"},
        {"7200000", "PT7200S", "2 hours, 0 minutes", "2h 0min"},
        {"172800000", "PT172800S", "2 days, 0 hours", "2d 0h"}
    };
    for (String[] sample : cases) {
      String html = page("bzadmin-system", "system-summary",
          JVM_SUMMARY.replace("uptime-ms='90061000'", "uptime-ms='" + sample[0] + "'"));
      assertTrue(html.contains("<time datetime=\"" + sample[1] + "\"><span aria-hidden=\"true\">" + sample[3] + "</span><span class=\"visually-hidden\">" + sample[2] + "</span></time>"), html);
    }
  }

  @Test
  void showsTheThreadsOfTheJvm() throws Exception {
    String html = page("bzadmin-system", "system-summary", JVM_SUMMARY, "threads");
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Threads</a>"), html);
    assertEquals(""
        + "<div class=\"key-metric\"><dt>Live</dt><dd class=\"key-metric-value\">24</dd><dd class=\"key-metric-detail\">20 daemon</dd></div>"
        + "<div class=\"key-metric\"><dt>Peak</dt><dd class=\"key-metric-value\">26</dd><dd class=\"key-metric-detail\">40 started since start</dd></div>"
        + "<div class=\"key-metric warning\"><dt>Blocked</dt><dd class=\"key-metric-value\">1</dd><dd class=\"key-metric-detail\">of 24 live</dd></div>"
        + "<div class=\"key-metric\"><dt>Deadlocked</dt><dd class=\"key-metric-value muted\">0</dd><dd class=\"key-metric-detail\">None</dd></div>", metrics(html));
    // Read again by the Refresh button of the threads, the only one of the view
    assertTrue(html.contains("<section class=\"system-metrics\" id=\"system-metrics\" data-href=\"/ctx/bzadmin/x.html?view=threads\">"), html);
    assertFalse(html.contains("id=\"refresh-metrics\""), html);
    // The states come with the list
    assertFalse(html.contains("class=\"thread-states\""), html);
    assertFalse(html.contains("threads-summary-table"), html);
    // The list, loaded by the page
    assertTrue(Pattern.compile("data-href=\"/ctx/bzadmin/system/threads.html\" class=\"button[^\"]*\"><svg-icon name=\"eye\"></svg-icon> Show threads</button>").matcher(html).find(), html);
    assertFalse(html.contains("threads-table"), html);
    assertFalse(html.contains("id=\"cpu-live\""), html);
    String deadlocked = page("bzadmin-system", "system-summary", JVM_SUMMARY.replace("deadlocked='0'", "deadlocked='2'"), "threads");
    assertTrue(metrics(deadlocked).contains("<div class=\"key-metric warning\"><dt>Deadlocked</dt><dd class=\"key-metric-value\">2</dd><dd class=\"key-metric-detail\">Marked in the thread list</dd></div>"), deadlocked);
  }

  @Test
  void describesTheLiveMemoryHistory() throws Exception {
    String live = page("bzadmin-system", "runtime", "<memory free='100' total='200' max='1000'/>", "memory");
    assertTrue(live.contains("aria-details=\"memory-current-values\" aria-describedby=\"memory-history\""), live);
    assertTrue(live.contains("<p id=\"memory-history\" class=\"chart-history\">0 samples</p>"), live);
    assertTrue(live.contains("data-history=\"min\""), live);
    assertTrue(live.contains("data-history=\"max\""), live);
    assertFalse(live.contains("See <a href=\"#memory-current-values"), live);
    assertFalse(live.contains("chart-history-details"), live);
  }

  @Test
  void showsTheMemoryOfTheJvm() throws Exception {
    String html = page("bzadmin-system", "system-summary", JVM_SUMMARY, "memory");
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Memory</a>"), html);
    assertTrue(html.contains("<th>Max</th><th>Peak</th>"), html);
    assertTrue(html.contains("<b>Peak</b>: highest used memory since the JVM started, shown in the Memory areas and pools table."), html);
    assertFalse(html.contains("title=\"Highest used since the JVM started\""), html);
    assertEquals(""
        + "<div class=\"key-metric\" id=\"metric-heap-used\"><dt>Heap used</dt><dd class=\"key-metric-value\">1.2%</dd>"
        + "<dd class=\"key-metric-meter\"><meter min=\"0\" max=\"1\" high=\"0.85\" optimum=\"0\" value=\"0.01220703125\"></meter></dd><dd class=\"key-metric-detail\">52.4 MB of 4.3 GB</dd></div>"
        + "<div class=\"key-metric\" id=\"metric-heap-committed\"><dt>Heap committed</dt><dd class=\"key-metric-value\">6.2%</dd>"
        + "<dd class=\"key-metric-meter\"><meter min=\"0\" max=\"1\" optimum=\"0\" value=\"0.0625\"></meter></dd><dd class=\"key-metric-detail\">268.4 MB of 4.3 GB</dd></div>"
        + "<div class=\"key-metric\"><dt>Non-heap</dt><dd class=\"key-metric-value\">61.9 MB</dd><dd class=\"key-metric-detail\">66.7 MB committed</dd></div>"
        + "<div class=\"key-metric\" id=\"metric-gc-time\"><dt>GC time</dt><dd class=\"key-metric-value\">0.0%</dd><dd class=\"key-metric-detail\">3 collections, 1,200 ms</dd></div>", metrics(html));
    assertTrue(html.contains("<th>Heap</th><td>52.4 MB</td><td>268.4 MB</td><td>4.3 GB</td>"), html);
    assertTrue(html.contains("<th>Non-heap</th><td>61.9 MB</td><td>66.7 MB</td><td>Undefined</td>"), html);
    // The pools follow their area
    assertTrue(html.indexOf("G1 Eden Space</td>") < html.indexOf("<th>Non-heap</th>"), html);
    assertTrue(html.indexOf("Metaspace</td>") > html.indexOf("<th>Non-heap</th>"), html);
    assertTrue(html.contains("<td>1,200 ms</td><td>G1 Eden Space, G1 Survivor Space</td>"), html);
    assertFalse(html.contains("id=\"cpu-live\""), html);
  }

  @Test
  void summarisesAJvmWithoutOptionalMetrics() throws Exception {
    String summary = "<system-summary processors='1'>"
        + "<runtime start='2026-10-23T01:02:03Z' uptime-ms='999'/>"
        + "<memory-area type='heap' used='1' committed='2'/><memory-area type='non-heap' used='3' committed='4'/>"
        + "<garbage-collector name='Epsilon' pools='Heap'/>"
        + "<threads live='1' daemon='0' peak='1' started='1' new='0' runnable='1' blocked='0' waiting='0' timed-waiting='0' terminated='0' deadlocked='0'/>"
        + "</system-summary>";
    String unavailable = "<dd class=\"key-metric-value muted\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></dd>";
    String cpu = metrics(page("bzadmin-system", "system-summary", summary));
    assertTrue(cpu.contains("<dt>Process CPU</dt>" + unavailable + "</div>"), cpu);
    assertTrue(cpu.contains("<dt>Load average</dt>" + unavailable + "<dd class=\"key-metric-detail\">1 processor</dd>"), cpu);
    assertTrue(cpu.contains("<dt>CPU time</dt>" + unavailable + "</div>"), cpu);
    assertTrue(cpu.contains("<dt>GC time</dt>" + unavailable + "</div>"), cpu);
    String memory = page("bzadmin-system", "system-summary", summary, "memory");
    assertTrue(metrics(memory).contains("<dt>Heap used</dt><dd class=\"key-metric-value\">1 bytes</dd><dd class=\"key-metric-detail\">No maximum</dd>"), memory);
    assertTrue(metrics(memory).contains("<dt>Heap committed</dt><dd class=\"key-metric-value\">2 bytes</dd><dd class=\"key-metric-detail\">No maximum</dd>"), memory);
    assertTrue(memory.contains("<td>Epsilon</td><td>Not available</td><td>Not available</td>"), memory);
    String none = page("bzadmin-system", "system-summary",
        "<system-summary processors='1'><runtime start='2026-10-23T01:02:03Z' uptime-ms='0'/>"
        + "<memory-area type='heap' used='1' committed='2'/><memory-area type='non-heap' used='3' committed='4'/>"
        + "<threads live='1' daemon='0' peak='1' started='1' new='0' runnable='1' blocked='0' waiting='0' timed-waiting='0' terminated='0'/>"
        + "</system-summary>", "memory");
    assertTrue(none.contains("No garbage collector reported by this JVM."), none);
  }

  @Test
  void showsTheFileSystem() throws Exception {
    String xml = "<root service='bzadmin-system' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/x.html'/><path context='/ctx' info='/bzadmin/x' extension='.html'/>"
        + "<http-parameters><parameter name='view'>files</parameter></http-parameters>"
        + "<berlioz version='0.14.4' mode='test'/></header>"
        + "<content generator='org.pageseeder.bzadmin.X' name='system-summary' target='main' status='ok'>" + JVM_SUMMARY + "</content>"
        + "<content generator='org.pageseeder.bzadmin.X' name='filesystem' target='main' status='ok'><file-system free-space='50000000000' total-space='1000000000000'/></content>"
        + "</root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">File system</a>"), html);
    assertEquals("<div class=\"key-metric warning\"><dt>Free space</dt><dd class=\"key-metric-value\">5.0%</dd>"
        + "<dd class=\"key-metric-meter\"><meter min=\"0\" max=\"1\" low=\"0.1\" high=\"0.2\" optimum=\"1\" value=\"0.05\"></meter></dd>"
        + "<dd class=\"key-metric-detail\">50 GB free of 1 TB</dd></div>", metrics(html));
    assertFalse(html.contains("diskspace"), html);
    assertTrue(html.contains("<h2>Application files</h2>"), html);
    assertTrue(html.contains("Show details</button>"), html);
  }

  @Test
  void showsTheSystemPropertiesWithRedactedValues() throws Exception {
    String xml = "<root service='bzadmin-system' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/x.html'/><path context='/ctx' info='/bzadmin/x' extension='.html'/>"
        + "<http-parameters><parameter name='view'>properties</parameter></http-parameters>"
        + "<berlioz version='0.14.5' mode='test'/></header>"
        + "<content generator='org.pageseeder.bzadmin.X' name='system-summary' target='main' status='ok'>" + JVM_SUMMARY + "</content>"
        + "<content generator='org.pageseeder.bzadmin.X' name='sysproperties' target='main' status='ok'><system>"
        + "<property name='file.encoding' value='UTF-8'/><property name='java.vendor' value='Eclipse Adoptium'/>"
        + "<property name='java.version' value='11.0.31'/><property name='java.vm.name' value='OpenJDK 64-Bit Server VM'/>"
        + "<property name='os.arch' value='amd64'/><property name='os.name' value='Linux'/><property name='os.version' value='5.15.0'/>"
        + "<property name='user.timezone' value='Australia/Sydney'/><property name='zeta.password' value='[REDACTED]'/>"
        + "</system></content>"
        + "</root>";
    String html = OverlayXslt.transform(new StreamSource(new StringReader(xml)));
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Properties</a>"), html);
    assertEquals("<div class=\"key-metric\"><dt>Properties</dt><dd class=\"key-metric-value\">9</dd><dd class=\"key-metric-detail\">1 redacted</dd></div>"
        + "<div class=\"key-metric\"><dt>OS</dt><dd class=\"key-metric-value\">Linux</dd><dd class=\"key-metric-detail\">5.15.0 · amd64</dd></div>"
        + "<div class=\"key-metric\"><dt>Java</dt><dd class=\"key-metric-value\">11.0.31</dd><dd class=\"key-metric-detail\">OpenJDK 64-Bit Server VM · Eclipse Adoptium</dd></div>"
        + "<div class=\"key-metric\"><dt>Encoding</dt><dd class=\"key-metric-value\">UTF-8</dd><dd class=\"key-metric-detail\">Australia/Sydney</dd></div>", metrics(html));
    assertTrue(html.contains("<h2>System properties</h2>"), html);
    // In Berlioz's order (sorted by name), the redacted value as a tag
    assertTrue(html.contains("<tr><th scope=\"row\">user.timezone</th><td>Australia/Sydney</td></tr>"
        + "<tr><th scope=\"row\">zeta.password</th><td><span class=\"tag\""), html);
    assertFalse(html.contains("[REDACTED]"), html);

    // An encoding other than UTF-8 calls for attention; missing properties are unavailable
    String legacy = metrics(OverlayXslt.transform(new StreamSource(new StringReader(xml
        .replace("value='UTF-8'", "value='Cp1252'").replaceAll("<property name='(os|user)\\.[^>]*>", "")))));
    assertTrue(legacy.contains("<div class=\"key-metric warning\"><dt>Encoding</dt><dd class=\"key-metric-value\">Cp1252</dd></div>"), legacy);
    assertTrue(legacy.contains("<dt>OS</dt><dd class=\"key-metric-value muted\" title=\"Not available\">"), legacy);

    // Not in the other views
    String cpu = OverlayXslt.transform(new StreamSource(new StringReader(xml.replace(">properties</parameter>", ">cpu</parameter>"))));
    assertFalse(cpu.contains("<h2>System properties</h2>"), cpu);
  }

  private static final String THREADS = "<threads live='4' listed='4' cpu-time='true' allocated-bytes='true' deadlocks='0'>"
      + "<thread id='49' name='qtp1409442094-49' priority='5' state='RUNNABLE' alive='true' daemon='false' group='main'"
      + " in-native='true' blocked-count='0' waited-count='12' allocated-bytes='2048000'>"
      + "<origin component='Jetty' role='selector' basis='stack' loader='other'/>"
      + "<activity class='org.eclipse.jetty.io.ManagedSelector' method='nioSelect' file='ManagedSelector.java' line='180'/>"
      + "<times cpu='70000' user='60000' system='10000'/></thread>"
      + "<thread id='51' name='qtp1409442094-51' priority='5' state='TIMED_WAITING' alive='true' daemon='false' group='main'"
      + " blocked-count='3' waited-count='1200' allocated-bytes='1000'>"
      + "<origin component='Jetty' role='idle' basis='stack' loader='other'/>"
      + "<activity class='org.eclipse.jetty.util.BlockingArrayQueue' method='poll' file='BlockingArrayQueue.java' line='219'/>"
      + "<lock name='java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@1a2b'/>"
      + "<times cpu='14460000' user='12000000' system='2460000'/></thread>"
      + "<thread id='70' name='qtp1409442094-70' priority='5' state='BLOCKED' alive='true' daemon='false' group='main'"
      + " current='true' blocked-count='1' waited-count='0' allocated-bytes='25000000'>"
      + "<origin component='Jetty' role='request' basis='stack' loader='application'/>"
      + "<activity class='org.example.app.Repository' method='load' file='Repository.java' line='42'/>"
      + "<lock name='org.example.app.Repository@3c4d' owner-id='71' owner-name='worker'/>"
      + "<times cpu='12345678901' user='12000000000' system='345678901'/></thread>"
      + "<thread id='2' name='Reference Handler' priority='10' state='RUNNABLE' alive='true' daemon='true' group='system'"
      + " blocked-count='0' waited-count='0'>"
      + "<origin component='JVM' role='internal' basis='name' loader='none'/>"
      + "<activity class='java.lang.ref.Reference' method='waitForReferencePendingList' native='true'/></thread>"
      + "</threads>";

  @Test
  void listsTheThreadsOnTheirOwnPage() throws Exception {
    String html = page("bzadmin-system-threads", "threads", THREADS);
    assertTrue(html.contains("<th>Δ CPU</th>"), html);
    assertTrue(html.contains("<th>Δ Allocated</th>"), html);
    assertTrue(html.contains("<th>Blocked / waited</th>"), html);
    Matcher explanation = Pattern.compile("<p id=\"system-info-thread-columns\"[^>]*>(.*?)</p>").matcher(html);
    assertTrue(explanation.find(), html);
    assertTrue(explanation.group(1).contains("<b>Δ CPU</b>: CPU used since the last refresh, as a share of one processor."), html);
    assertTrue(explanation.group(1).contains("<b>Δ Allocated</b>: memory allocated per second since the last refresh."), html);
    assertTrue(explanation.group(1).contains("<b>Blocked / waited</b>: how many times the thread was blocked on a lock or waited for a notification, since it started."), html);
    assertFalse(html.contains("<abbr title=\"CPU used since"), html);
    assertFalse(html.contains("<abbr title=\"Memory allocated per"), html);
    assertFalse(html.contains("<abbr title=\"Times the thread"), html);
    assertTrue(html.contains("<div id=\"threads-details\">"), html);
    // A panel with the heading, the states and origins, then the buttons; the table after it
    assertTrue(html.contains("<section class=\"panel threads-panel\"><h2>Threads</h2><div id=\"threads-overview\">"), html);
    assertTrue(html.indexOf("id=\"threads-overview\"") < html.indexOf("class=\"actions threads-actions\""), html);
    assertTrue(html.indexOf("id=\"since-last-refresh\"") < html.indexOf("<table class=\"basic-table threads-table is-panel\""), html);
    assertTrue(html.contains("id=\"refresh-threads\""), html);
    assertTrue(html.contains("id=\"hide-idle-threads\""), html);
    // By origin, then the listed threads by state to the right
    assertTrue(html.indexOf("<h3>By origin") < html.indexOf("<div class=\"thread-states\"><h3>By state"), html);
    assertTrue(html.contains("</h3><pie-chart values=\"2,1,0,1\" labels=\"Runnable,Blocked,Waiting,Timed waiting\"></pie-chart>"), html);
    // By component, the largest first, and by role
    assertTrue(html.contains("<tr><th scope=\"row\">Jetty</th><td class=\"number\">3</td><td><span class=\"tag thread-role idle\">1 idle</span> "
        + "<span class=\"tag thread-role request\">1 request</span> <span class=\"tag thread-role selector\">1 selector</span></td></tr>"
        + "<tr><th scope=\"row\">JVM</th><td class=\"number\">1</td><td><span class=\"tag thread-role internal\">1 internal</span></td></tr>"), html);
  }

  @Test
  void listsEachThreadInARow() throws Exception {
    String html = page("bzadmin-system-threads", "threads", THREADS);
    assertTrue(html.contains("<span class=\"thread-name\">qtp1409442094-49</span>"), html);
    assertFalse(html.contains("title=\"49: qtp1409442094-49"), html);
    assertFalse(html.contains("class=\"thread-component\" title="), html);
    // Each row: its role as a class, origin, state with the lock it waits for, activity, counters
    assertTrue(html.contains("<tr class=\"selector\" data-id=\"49\">"), html);
    assertTrue(html.contains("<tr class=\"idle\" data-id=\"51\">"), html);
    assertTrue(html.contains("<span class=\"thread-component\">Jetty</span> <span class=\"tag thread-role selector\">selector</span>"), html);
    assertTrue(html.contains("<svg-icon name=\"stop\"></svg-icon> blocked</span>"), html);
    assertTrue(html.contains("on Repository@3c4d, held by worker (71)"), html);
    assertFalse(html.contains("class=\"thread-lock\" title="), html);
    assertFalse(html.contains("<code class=\"\" title="), html);
    assertFalse(html.contains("<code class=\"jdk\" title="), html);
    assertFalse(html.contains("on AbstractQueuedSynchronizer$ConditionObject@1a2b"), "Not for idle threads waiting without owner");
    assertTrue(html.contains("<code class=\"\">Repository.load</code>"), html);
    assertTrue(html.contains("<code class=\"jdk\">Reference.waitForReferencePendingList</code>"), html);
    assertTrue(html.contains("<td class=\"number\" data-sort=\"12345678901\">12.3 s</td>"), html);
    assertTrue(html.contains("<td class=\"number\" data-sort=\"14460000\">14 ms</td>"), html);
    assertTrue(html.contains("<td class=\"number\" data-sort=\"70000\">0.1 ms</td>"), html);
    assertTrue(html.contains("<td class=\"number cpu-delta\" data-cpu=\"70000\">–</td>"), html);
    assertTrue(html.contains("<td class=\"number\" data-sort=\"25000000\">25 MB</td>"), html);
    assertTrue(html.contains("data-allocated=\"25000000\">–</td>"), html);
    assertTrue(html.contains("<td class=\"number\" data-sort=\"3\">3 / 1,200</td>"), html);
    assertTrue(html.contains("<span class=\"tag\">this request</span>"), html);
    assertTrue(html.contains("data-href=\"/ctx/bzadmin/system/thread.html?id=70\" aria-label=\"Stack of qtp1409442094-70\">"), html);
    assertTrue(html.contains("<dialog id=\"thread-dialog\""), html);
    assertFalse(html.contains("callout"), html);
  }

  @Test
  void warnsOfDeadlocksAndLeavesOutUnavailableCounters() throws Exception {
    String html = page("bzadmin-system-threads", "threads",
        "<threads live='3000' listed='2000' truncated='true' cpu-time='false' allocated-bytes='false' deadlocks='2'>"
        + "<thread id='5' name='worker' priority='5' state='BLOCKED' alive='true' daemon='false' deadlocked='true' blocked-count='1' waited-count='0'>"
        + "<origin component='Unknown' role='running' basis='none' loader='system'/>"
        + "<lock name='java.lang.Object@1' owner-id='6' owner-name='other'/></thread></threads>");
    assertTrue(html.contains("<p class=\"callout alert\"><svg-icon name=\"warning\"></svg-icon> 2 threads are deadlocked"), html);
    assertTrue(html.contains("Only the first 1 of 3000 threads are listed."), html);
    assertTrue(html.contains("<span class=\"tag warning\">deadlocked</span>"), html);
    assertTrue(html.contains("<span class=\"thread-component\">Unknown</span>"), html);
    assertFalse(html.contains("<th>Allocated</th>"), html);
    assertFalse(html.contains("<th>Δ Allocated</th>"), html);
    assertFalse(html.contains("allocated-delta"), html);
    assertFalse(html.contains("cpu-delta"), html);
  }

  @Test
  void showsAThreadWithItsStack() throws Exception {
    String html = page("bzadmin-system-thread", "thread",
        "<thread id='70' name='qtp1409442094-70' priority='5' state='BLOCKED' alive='true' daemon='false' group='main'"
        + " blocked-count='1' waited-count='0' allocated-bytes='25000000' frames='300' stack-truncated='true'>"
        + "<origin component='Jetty' role='request' basis='stack' loader='application'/>"
        + "<activity class='org.example.app.Repository' method='load' file='Repository.java' line='42'/>"
        + "<lock name='org.example.app.Repository@3c4d' owner-id='71' owner-name='worker'/>"
        + "<times cpu='12345678901' user='12000000000' system='345678901'/>"
        + "<stacktrace><element class='org.example.app.Repository' method='load' filename='Repository.java' line='42'/>"
        + "<element class='org.example.app.Service' method='get' filename='Service.java' line='7'><locked class='org.example.app.Service' identity='5e6f'/></element>"
        + "<element class='java.lang.Thread' method='run' filename='Thread.java' line='829'/></stacktrace>"
        + "<locked-synchronizers><locked class='java.util.concurrent.locks.ReentrantLock$NonfairSync' identity='7a8b'/></locked-synchronizers>"
        + "</thread>");
    assertTrue(html.contains("<div id=\"thread-info\">"), html);
    assertTrue(html.contains("<td>Jetty, request<small> (from the code the thread runs; context class loader: application)</small></td>"), html);
    assertTrue(html.contains("on <code>org.example.app.Repository@3c4d</code>, held by worker (71)"), html);
    assertTrue(html.contains("<td>12.3 s<small> (user 12.0 s, system 346 ms)</small></td>"), html);
    assertTrue(html.contains("<li class=\"\"><code>org.example.app.Repository.load(Repository.java:42)</code></li>"), html);
    assertTrue(html.contains("<br><small>holds <code>org.example.app.Service@5e6f</code></small>"), html);
    assertTrue(html.contains("<li class=\"jdk\"><code>java.lang.Thread.run(Thread.java:829)</code></li>"), html);
    assertTrue(html.contains("Only the top 3 of 300 frames are shown."), html);
    assertTrue(html.contains("<li><code>java.util.concurrent.locks.ReentrantLock$NonfairSync@7a8b</code></li>"), html);
    String ended = page("bzadmin-system-thread", "thread", "<no-thread id='70' reason='not-found'/>");
    assertTrue(ended.contains("Thread 70 has ended, or never existed."), ended);
  }

  static String page(String service, String name, String content) throws Exception {
    return page(service, name, content, "");
  }

  /** A page with the given 'view' parameter. */
  static String page(String service, String name, String content, String view) throws Exception {
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/x.html'/><path context='/ctx' info='/bzadmin/x' extension='.html'/>"
        + "<http-parameters><parameter name='view'>" + view + "</parameter></http-parameters>"
        + "<berlioz version='0.14.4' mode='test'/></header>"
        + "<content generator='org.pageseeder.bzadmin.X' name='" + name + "' target='main' status='ok'>"
        + content + "</content></root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}
