package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The application summary of the fully configured test host (logback, EHCache 2 and JCache, the
 * container roles of {@code config-test.xml}) and its JVM summary. The minimal host is in
 * {@link MinimalHostTest}.
 */
final class DiagnosticsTest {

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void summarisesTheVersions() throws Exception {
    Document xml = summary(host);
    assertEquals(System.getProperty("bzadmin.version"), xpath(xml, "//component[@name='bzadmin']/@version"));
    assertTrue(xpath(xml, "//component[@name='berlioz']/@version").startsWith("0.14."), "Berlioz from its manifest");
    String java = xpath(xml, "//component[@name='java']/@version");
    assertTrue(java.startsWith(System.getProperty("bzadmin.java.expected")), java);
    assertEquals("net.sf.saxon.TransformerFactoryImpl", xpath(xml, "//component[@name='xslt']/@factory"));
    assertTrue(xpath(xml, "//component[@name='xslt']/@version").startsWith("HE 12."), "As Saxon identifies itself");
    assertEquals("Saxonica", xpath(xml, "//component[@name='xslt']/@vendor"));
    assertEquals("3.0", xpath(xml, "//component[@name='xslt']/@xslt-version"));
    assertTrue(xpath(xml, "//component[@name='container']/@product").startsWith("jetty/10."));
    assertEquals("4.0", xpath(xml, "//component[@name='container']/@servlet-api"));
    assertEquals("test", xpath(xml, "/root/content[@name='summary']/application-summary/@mode"));
  }

  @Test
  void summarisesTheIntegrations() throws Exception {
    Document xml = summary(host);
    assertEquals("logback", xpath(xml, "//application-summary/logging/@backend"));
    assertEquals("true", xpath(xml, "//application-summary/logging/@recent-events"));
    assertEquals("2", xpath(xml, "//application-summary/cache-provider[@id='ehcache2']/@managers"), "Default and fixture");
    assertEquals("1", xpath(xml, "//application-summary/cache-provider[@id='jcache']/@managers"), "fixture-caffeine");
    assertEquals("1", xpath(xml, "//application-summary/cache-provider[@id='caffeine']/@managers"), "fixture-native");
    assertEquals("true", xpath(xml, "//application-summary/access/@roles"));
    assertEquals("false", xpath(xml, "//application-summary/access/@fallback-login"));
    assertEquals("ready", xpath(xml, "//application-summary/status/@services"));
    assertEquals("loaded", xpath(xml, "//application-summary/status/@configuration"));
  }

  @Test
  void showsTheSummaryInEveryFormat() throws Exception {
    for (String path : new String[]{"/bzadmin/application", "/bzadmin/application/summary"}) {
      for (String format : new String[]{".src", ".xml", ".json", ".html"}) {
        HttpResponse<String> response = host.get(path + format);
        assertEquals(200, response.statusCode(), path + format);
        assertFalse(response.body().contains("fixture-secret"), path + format);
      }
    }
    String json = host.get("/bzadmin/application/summary.json").body();
    assertTrue(json.contains("\"components\":[{\"name\":\"bzadmin\""), json);
    assertTrue(json.contains("\"cacheProviders\":["), json);
    String html = host.get("/bzadmin/application.html").body();
    assertTrue(html.contains("<section id=\"summary\""), html);
    assertTrue(html.contains("Host access attribute, container roles"), html);
  }

  @Test
  void summarisesTheJvm() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/system/summary.src");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    String summary = "/root/content[@name='system-summary']/system-summary";
    assertEquals(String.valueOf(Runtime.getRuntime().availableProcessors()), xpath(xml, summary + "/@processors"));
    assertTrue(Long.parseLong(xpath(xml, summary + "/runtime/@uptime-ms")) > 0);
    assertEquals("2", xpath(xml, "count(" + summary + "/memory-area)"));
    assertTrue(Long.parseLong(xpath(xml, summary + "/memory-area[@type='heap']/@used")) > 0);
    assertTrue(Integer.parseInt(xpath(xml, "count(" + summary + "/memory-pool)")) > 0);
    assertTrue(Integer.parseInt(xpath(xml, "count(" + summary + "/garbage-collector)")) > 0);
    assertTrue(Integer.parseInt(xpath(xml, summary + "/threads/@live")) > 0);
    // HotSpot provides the process CPU time
    assertFalse(xpath(xml, summary + "/cpu/@process-time-ms").isEmpty());
    // Bounded: no thread names or stack traces
    assertEquals("0", xpath(xml, "count(//thread)"));
    assertEquals(200, host.get("/bzadmin/system/summary.json").statusCode());
  }

  @Test
  void listsTheThreadsOnDemand() throws Exception {
    assertTrue(host.get("/bzadmin/system.html").body().contains("<section class=\"system-metrics\" id=\"system-metrics\" data-href=\"/bzadmin/system.html?view=cpu\"><dl class=\"key-metrics\">"));
    String system = host.get("/bzadmin/system.html?view=threads").body();
    assertTrue(system.contains("<dt>Deadlocked"), system);
    assertTrue(system.contains("> Show threads</button>"), system);
    assertFalse(system.contains("threads-table"), "Not listed with the page");
    assertEquals("0", xpath(parse(host.get("/bzadmin/system.src").body()), "count(//content[@name='threads'])"));
    String threads = host.get("/bzadmin/system/threads.html").body();
    assertTrue(threads.contains("<div id=\"threads-details\">"), threads);
    assertTrue(threads.contains("class=\"basic-table threads-table is-panel\""), threads);
  }

  @Test
  void guessesTheOriginOfEachThreadWithoutStacks() throws Exception {
    Document xml = parse(host.get("/bzadmin/system/threads.xml").body());
    int listed = Integer.parseInt(xpath(xml, "//threads/@listed"));
    assertTrue(listed > 0);
    assertEquals(String.valueOf(listed), xpath(xml, "count(//threads/thread/origin[@component != ''][@role][@basis][@loader])"));
    assertEquals("0", xpath(xml, "count(//stacktrace)"), "Bounded: no stack in the list");
    // Jetty serves this request, for the application
    assertEquals("Jetty", xpath(xml, "//thread[@current = 'true']/origin/@component"));
    assertEquals("request", xpath(xml, "//thread[@current = 'true']/origin/@role"));
    assertEquals("application", xpath(xml, "//thread[@current = 'true']/origin/@loader"));
    assertEquals("org.pageseeder.bzadmin.system.ListThreads", xpath(xml, "//thread[@current = 'true']/activity/@class"));
    assertEquals("JVM", xpath(xml, "//thread[@name = 'Reference Handler']/origin/@component"));
    assertEquals(200, host.get("/bzadmin/system/threads.json").statusCode());
  }

  @Test
  void showsTheStackOfAThread() throws Exception {
    Document threads = parse(host.get("/bzadmin/system/threads.xml").body());
    String id = xpath(threads, "//thread[@name = 'Reference Handler']/@id");
    Document xml = parse(host.get("/bzadmin/system/thread.xml?id=" + id).body());
    assertEquals("Reference Handler", xpath(xml, "//thread/@name"));
    assertTrue(Integer.parseInt(xpath(xml, "count(//thread/stacktrace/element)")) > 0);
    assertTrue(host.get("/bzadmin/system/thread.html?id=" + id).body().contains("<div id=\"thread-info\">"));
    assertEquals(404, host.get("/bzadmin/system/thread.xml?id=" + Long.MAX_VALUE).statusCode());
    assertEquals(400, host.get("/bzadmin/system/thread.xml?id=x").statusCode());
  }

  @Test
  void pollsTheLiveValuesWithoutCaching() throws Exception {
    for (String path : new String[]{"/bzadmin/system/runtime/sample.xml", "/bzadmin/system/cpu/sample.xml", "/bzadmin/system/summary.xml"}) {
      HttpResponse<String> response = host.get(path);
      assertEquals(200, response.statusCode(), path);
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""), path);
    }
    Document cpu = parse(host.get("/bzadmin/system/cpu/sample.xml").body());
    assertFalse(xpath(cpu, "/cpu-sample/@processors").isEmpty());
    assertFalse(xpath(cpu, "/cpu-sample/@uptime-ms").isEmpty());
    Document runtime = parse(host.get("/bzadmin/system/runtime/sample.xml").body());
    for (String name : new String[]{"free", "total", "max"}) {
      assertFalse(xpath(runtime, "/runtime/memory/@" + name).isEmpty(), name);
    }
  }

  /**
   * The live samples are the output of their handler only, without envelope or XSLT, whatever the
   * extension; the other services keep the envelope.
   */
  @Test
  void writesTheSamplesDirectly() throws Exception {
    for (String format : new String[]{".xml", ".src", ".html"}) {
      HttpResponse<String> cpu = host.get("/bzadmin/system/cpu/sample" + format);
      assertEquals(200, cpu.statusCode(), format);
      assertTrue(cpu.headers().firstValue("Content-Type").orElse("").startsWith("application/xml"), format);
      assertEquals("cpu-sample", parse(cpu.body()).getDocumentElement().getNodeName(), format);
      assertEquals("runtime", parse(host.get("/bzadmin/system/runtime/sample" + format).body()).getDocumentElement().getNodeName(), format);
    }
    String json = host.get("/bzadmin/system/cpu/sample.json").body();
    assertTrue(json.startsWith("{\"processors\":"), json);
    assertEquals("root", parse(host.get("/bzadmin/system/cpu.xml").body()).getDocumentElement().getNodeName());
  }

  static Document summary(BerliozHost host) throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/application.src");
    assertEquals(200, response.statusCode(), response.body());
    return parse(response.body());
  }

}
