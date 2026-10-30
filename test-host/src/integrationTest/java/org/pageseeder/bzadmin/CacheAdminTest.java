package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.jspecify.annotations.Nullable;

/**
 * The cache pages (M4.4) on the host's cache managers ({@code FixtureCacheManagers}): a cache named
 * {@code PSML} in the default EHCache 2 manager, in the EHCache 2 manager {@code fixture} and in
 * the Caffeine JCache manager {@code fixture-caffeine}; and the native Caffeine cache {@code pages}
 * of the manager {@code fixture-native}. Operations reach the cache they name;
 * unavailable statistics are shown as unavailable, not zero; only typed keys are listed and removed.
 *
 * <p>The operations that empty the fixture caches run last.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class CacheAdminTest {

  private static final String DEFAULT = "provider=ehcache2&manager=__DEFAULT__&cache=PSML";

  private static final String FIXTURE = "provider=ehcache2&manager=fixture&cache=PSML";

  private static final String JCACHE = "provider=jcache&manager=fixture-caffeine&cache=PSML";

  private static final String JCACHE_LONGS = "provider=jcache&manager=fixture-caffeine&cache=long-keys";

  private static final String NATIVE = "provider=caffeine&manager=fixture-native&cache=pages";

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
  @Order(1)
  void listsEachProviderAndManager() throws Exception {
    Document xml = parse(host.get("/bzadmin/cache.src").body());
    assertEquals("ehcache2 jcache caffeine", ids(xml, "//cache-provider/@id"));
    assertTrue(xpath(xml, "//cache-provider[@id = 'ehcache2']/@label").startsWith("EHCache 2.11"));
    assertEquals("__DEFAULT__ fixture", ids(xml, "//cache-provider[@id = 'ehcache2']/cache-manager/@id"));
    assertEquals("fixture-caffeine", ids(xml, "//cache-provider[@id = 'jcache']/cache-manager/@id"));
    assertEquals("fixture-native", ids(xml, "//cache-provider[@id = 'caffeine']/cache-manager/@id"));
    assertTrue(xpath(xml, "//cache-provider[@id = 'caffeine']/@label").startsWith("Caffeine 3."));
    assertEquals("3", xpath(xml, "count(//cache[@name = 'PSML'])"));
    assertTrue(xpath(xml, "//cache-manager[@id = 'fixture-caffeine']/@label").endsWith("(CaffeineCachingProvider)"));
    // The fixture caches are limited in entries or unbounded: counted, not sized in bytes
    assertTrue(Integer.parseInt(xpath(xml, "/root/content/cache-providers/cache-capacity/resource[@type = 'heap']/@unsized")) >= 5, xpath(xml, "//cache-capacity/resource/@unsized"));
    assertTrue(host.get("/bzadmin/cache.html").body().contains("<h2>Memory and disk</h2>"));

    // Caffeine counts its entries (through unwrap); its fixture caches have no maximum
    String jcache = "//cache-manager[@id = 'fixture-caffeine']/cache[@name = 'PSML']";
    assertEquals("1", xpath(xml, "count(" + jcache + "/statistics/metric[@name = 'entries'])"));
    assertEquals("heap", xpath(xml, jcache + "/tier/@type"));
    assertEquals("unbounded", xpath(xml, jcache + "/warning/@code"));
    assertEquals("true", xpath(xml, jcache + "/statistics/@enabled"));
    assertEquals("local", xpath(xml, jcache + "/@scope"));
    assertEquals("configuration statistics clear statistics-toggle statistics-reset remove-entry", xpath(xml, jcache + "/@capabilities"));
    String ehcache = "//cache-manager[@id = 'fixture']/cache[@name = 'PSML']";
    assertEquals("4", xpath(xml, ehcache + "/statistics/metric[@name = 'entries']/@value"));
    assertEquals("configuration statistics clear entries remove-entry", xpath(xml, ehcache + "/@capabilities"));
    // Native Caffeine: statistics recorded when built (no toggle), keys listed
    String caffeine = "//cache-manager[@id = 'fixture-native']/cache[@name = 'pages']";
    assertEquals("configuration statistics clear statistics-reset entries remove-entry", xpath(xml, caffeine + "/@capabilities"));
    assertEquals("true", xpath(xml, caffeine + "/statistics/@enabled"));
    assertEquals("1000", xpath(xml, caffeine + "/statistics/@entry-limit"));
    assertEquals("0", xpath(xml, "count(" + caffeine + "/statistics/metric[@name = 'puts'])"), "Not counted by Caffeine");

    String html = host.get("/bzadmin/cache.html").body();
    assertTrue(html.contains("href=\"/bzadmin/cache/info.html?" + JCACHE.replace("&", "&amp;") + "\""), html);
    assertTrue(html.contains("href=\"/bzadmin/cache/entries.html?" + FIXTURE.replace("&", "&amp;") + "\""), html);
    assertTrue(html.contains("href=\"/bzadmin/cache/entries.html?" + NATIVE.replace("&", "&amp;") + "\""), html);
    assertFalse(html.contains("href=\"/bzadmin/cache/entries.html?" + JCACHE.replace("&", "&amp;") + "\""), "JCache keys are not listed");
    assertTrue(html.contains("<td class=\"muted\" data-sort=\"-1\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></td>"), html);
    assertTrue(html.contains("data-manager=\"fixture-caffeine\" data-cache=\"PSML\""), html);

    String json = host.get("/bzadmin/cache.json").body();
    assertTrue(json.contains("\"providers\":[{\"id\":\"ehcache2\""), json);
    assertTrue(json.contains("\"capabilities\":[\"configuration\",\"statistics\",\"clear\",\"statistics-toggle\",\"statistics-reset\",\"remove-entry\"]"), json);
  }

  @Test
  @Order(1)
  void showsOneCache() throws Exception {
    Document xml = parse(host.get("/bzadmin/cache/info.src?" + JCACHE).body());
    assertEquals("fixture-caffeine", xpath(xml, "//cache-info/@manager"));
    assertEquals("java.lang.String", xpath(xml, "//cache-info/cache/configuration/property[@name = 'keyType']/@value"));
    assertEquals("ms", xpath(xml, "//cache-info/cache/statistics/metric[@name = 'average-put-time']/@unit"), "The fixture's put is timed");
    assertEquals("0", xpath(xml, "count(//cache-info/cache/statistics/metric[@name = 'average-get-time'])"), "No get: no average, not zero");
    String html = host.get("/bzadmin/cache/info.html?" + JCACHE).body();
    assertTrue(html.contains("<form class=\"remove-entry\">"), "Remove by key: the keys of a JCache cache are not listed");
    assertTrue(html.contains("data-provider=\"jcache\" data-manager=\"fixture-caffeine\" data-cache=\"PSML\""), html);
    assertTrue(html.contains("Disable statistics"), html);
    assertFalse(html.contains("Enable statistics"), "Enabled: only the button that changes it");
    assertTrue(html.contains("Reset statistics"), html);
    assertTrue(html.matches("(?s).*<th>Average put time</th><td>[0-9.]+ [µm]s</td>.*"), html);
    assertTrue(html.contains("Clear entries"), html);

    String ehcache = host.get("/bzadmin/cache/info.html?" + FIXTURE).body();
    assertTrue(ehcache.contains("View keys"), ehcache);
    assertFalse(ehcache.contains("toggle-statistics"), "EHCache 2.11 statistics are always collected");
    assertTrue(ehcache.contains("Always on"), ehcache);
    assertFalse(ehcache.contains("Reset statistics"), "EHCache 2 statistics are never reset");
    assertTrue(ehcache.contains("Entry limit</th><td>1000"), ehcache);
    // The key metrics: the entries against the entry limit, and the hit ratio
    assertTrue(ehcache.matches("(?s).*<dl class=\"key-metrics\"><div class=\"key-metric\"><dt><span id=\"[^\"]+\">Entries</span></dt>.*"), ehcache);
    assertTrue(ehcache.matches("(?s).*<meter min=\"0\" max=\"1\" high=\"0.9\" optimum=\"0\" value=\"[0-9.E-]+\" aria-labelledby=\"[^\"]+\"></meter></dd><dd class=\"key-metric-detail\">of 1,000 \\(.*"), ehcache);
    assertTrue(ehcache.contains("<h3>Storage tiers</h3>"), ehcache);
    assertTrue(ehcache.contains("1000 entries"), ehcache);
    assertTrue(html.contains("No limit"), "The Caffeine cache has no maximum");
    assertTrue(html.contains("Unbounded: </strong>"), html);
    // The recent activity reads the XML of the same cache, from the handler (no envelope)
    assertTrue(ehcache.contains("<div class=\"cache-rates\" hidden data-source=\"/bzadmin/cache/info/sample.xml?" + FIXTURE.replace("&", "&amp;") + "\""), ehcache);
    HttpResponse<String> source = host.get("/bzadmin/cache/info/sample.xml?" + FIXTURE);
    assertEquals(200, source.statusCode());
    assertEquals("no-store", source.headers().firstValue("Cache-Control").orElse(""));
    Document info = parse(source.body());
    assertEquals("cache-info", info.getDocumentElement().getNodeName());
    assertEquals("1000", xpath(info, "/cache-info/cache/statistics/@entry-limit"));
    assertEquals("1", xpath(info, "count(//cache-info/cache/statistics/metric[@name = 'hits'])"));
  }

  @Test
  @Order(1)
  void listsTypedKeysOnly() throws Exception {
    Document xml = parse(host.get("/bzadmin/cache/entries.src?" + FIXTURE).body());
    assertEquals("3", xpath(xml, "count(//entries/entry)"));
    assertEquals("string", xpath(xml, "//entries/entry[@key = 'fixture-key']/@key-type"));
    assertEquals("long", xpath(xml, "//entries/entry[@key = '42']/@key-type"));
    assertEquals("integer", xpath(xml, "//entries/entry[@key = '7']/@key-type"));
    assertEquals("java.util.UUID", xpath(xml, "//entries/unsupported-keys/@class"));
    assertEquals("1", xpath(xml, "//entries/unsupported-keys/@count"));
    assertEquals("false", xpath(xml, "//entries/@more"));
    String html = host.get("/bzadmin/cache/entries.html?" + FIXTURE).body();
    assertTrue(html.contains("data-key=\"42\" data-key-type=\"long\""), html);
    assertTrue(html.contains("<code>java.util.UUID</code> × 1"), html);
  }

  /**
   * The keys of a native Caffeine cache are listed without access: no hit or miss recorded.
   */
  @Test
  @Order(1)
  void listsNativeCaffeineKeysWithoutAccess() throws Exception {
    Document xml = parse(host.get("/bzadmin/cache/entries.src?" + NATIVE).body());
    assertEquals("string", xpath(xml, "//entries/entry[@key = 'fixture-key']/@key-type"));
    assertEquals("1", xpath(xml, "count(//entries/entry)"));
    Document info = parse(host.get("/bzadmin/cache/info.src?" + NATIVE).body());
    assertEquals("0", xpath(info, "//cache-info/cache/statistics/metric[@name = 'hits']/@value"));
    assertEquals("0", xpath(info, "//cache-info/cache/statistics/metric[@name = 'misses']/@value"));
    String html = host.get("/bzadmin/cache/info.html?" + NATIVE).body();
    assertTrue(html.contains("Reset statistics"), html);
    assertFalse(html.contains("toggle-statistics"), "Recorded or not when the cache is built");
  }

  @Test
  @Order(1)
  void reportsProblems() throws Exception {
    assertProblem(host.get("/bzadmin/cache/entries.src?" + JCACHE), 501, "unsupported");
    assertProblem(host.post("/bzadmin/cache/statistics.src", FIXTURE + "&enable=false"), 501, "unsupported");
    assertProblem(host.post("/bzadmin/cache/statistics/reset.src", FIXTURE), 501, "unsupported");
    assertProblem(host.post("/bzadmin/cache/statistics/reset.src", "provider=jcache&manager=fixture-caffeine"), 400, "missing-parameter");
    assertProblem(host.post("/bzadmin/cache/entries/remove.src", JCACHE_LONGS + "&key=42"), 400, "invalid-key");
    assertProblem(host.post("/bzadmin/cache/entries/remove.src", JCACHE_LONGS + "&key=42&key-type=integer"), 400, "invalid-key");
    assertProblem(host.post("/bzadmin/cache/entries/remove.src", FIXTURE + "&key=x&key-type=uuid"), 400, "invalid-key");
    assertProblem(host.post("/bzadmin/cache/entries/remove.src", FIXTURE + "&key=x&key-type=long"), 400, "invalid-key");
    assertProblem(host.post("/bzadmin/cache/entries/remove.src", FIXTURE), 400, "invalid-key");
    assertProblem(host.get("/bzadmin/cache/info.src?provider=jcache&manager=fixture&cache=PSML"), 404, "unknown-manager");
    assertProblem(host.get("/bzadmin/cache/info.src?provider=ehcache2&manager=fixture-caffeine&cache=PSML"), 404, "unknown-manager");
    assertProblem(host.get("/bzadmin/cache/info.src?provider=ehcache2&manager=fixture&cache=psml"), 404, "unknown-cache");
    assertProblem(host.get("/bzadmin/cache/info.src?provider=ehcache2&cache=PSML"), 400, "missing-parameter");
    // The same statuses from the handler
    assertProblem(host.get("/bzadmin/cache/info/sample.xml?provider=ehcache2&manager=fixture&cache=psml"), 404, "unknown-cache");
    assertProblem(host.get("/bzadmin/cache/info/sample.xml?provider=ehcache2&cache=PSML"), 400, "missing-parameter");
    assertEquals(400, host.post("/bzadmin/cache/statistics.src", JCACHE).statusCode(), "enable is required");
    String html = host.get("/bzadmin/cache/info.html?provider=ehcache2&manager=fixture&cache=psml").body();
    assertTrue(html.contains("No such cache in this cache manager."), html);
  }

  @Test
  @Order(1)
  void togglesJCacheStatistics() throws Exception {
    HttpResponse<String> disabled = host.post("/bzadmin/cache/statistics.src", JCACHE_LONGS + "&enable=false");
    assertEquals(200, disabled.statusCode(), disabled.body());
    assertEquals("false", xpath(parse(disabled.body()), "//cache-statistics/@enabled"));
    Document info = parse(host.get("/bzadmin/cache/info.src?" + JCACHE_LONGS).body());
    assertEquals("false", xpath(info, "//cache/statistics/@enabled"));
    assertEquals("0", xpath(info, "count(//cache/statistics/metric[@name != 'entries'])"), "Counters unavailable, not zero");
    assertEquals("1", xpath(info, "count(//cache/statistics/metric[@name = 'entries'])"), "Caffeine still counts its entries");
    assertTrue(host.get("/bzadmin/cache/info.html?" + JCACHE_LONGS).body().contains("Enable statistics"));
    assertEquals(200, host.post("/bzadmin/cache/statistics.src", JCACHE_LONGS + "&enable=true").statusCode());
    assertEquals("true", xpath(parse(host.get("/bzadmin/cache/info.src?" + JCACHE_LONGS).body()), "//cache/statistics/@enabled"));
  }

  @Test
  @Order(1)
  void resetsJCacheStatistics() throws Exception {
    HttpResponse<String> reset = host.post("/bzadmin/cache/statistics/reset.src", JCACHE_LONGS);
    assertEquals(200, reset.statusCode(), reset.body());
    assertEquals("long-keys", xpath(parse(reset.body()), "//reset-cache-statistics/@name"));
    Document info = parse(host.get("/bzadmin/cache/info.src?" + JCACHE_LONGS).body());
    assertEquals("0", xpath(info, "//cache/statistics/metric[@name = 'puts']/@value"), "The fixture's put no longer counted");
    assertEquals("0", xpath(info, "//cache/statistics/metric[@name = 'hits']/@value"));
    assertEquals("0", xpath(info, "count(//cache/statistics/metric[@name = 'average-put-time'])"), "No put since the reset: no average, not zero");
    assertEquals("true", xpath(info, "//cache/statistics/@enabled"), "Still collected");
  }

  /**
   * The same cache name in three managers of two providers: each operation reaches only the cache it
   * names.
   */
  @Test
  @Order(2)
  void reachesTheIntendedCache() throws Exception {
    // Typed keys: the long 42 is not the integer or the text 42
    assertEquals("false", removed(FIXTURE + "&key=42&key-type=integer"));
    assertEquals("false", removed(FIXTURE + "&key=42"));
    assertEquals("true", removed(FIXTURE + "&key=42&key-type=long"));

    assertEquals("true", removed(JCACHE + "&key=fixture-key"));
    assertEquals("false", removed(JCACHE + "&key=fixture-key"));
    assertTrue(keys(FIXTURE).contains("fixture-key"));
    assertTrue(keys(DEFAULT).contains("fixture-key"));

    assertEquals(200, host.post("/bzadmin/cache/clear.src", FIXTURE).statusCode());
    assertEquals("", keys(FIXTURE));
    assertTrue(keys(DEFAULT).contains("fixture-key"));
    assertTrue(keys("provider=ehcache2&manager=fixture&cache=clear-me").contains("fixture-key"));
    assertEquals("true", removed(JCACHE_LONGS + "&key=42&key-type=long"));
  }

  @Test
  @Order(3)
  void clearsEveryCache() throws Exception {
    HttpResponse<String> response = host.post("/bzadmin/cache/clear.src", "");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    for (String[] cache : new String[][] {{"ehcache2", "__DEFAULT__", "PSML"}, {"ehcache2", "fixture", "clear-me"}, {"jcache", "fixture-caffeine", "long-keys"}}) {
      String path = "//clear-cache/cache[@provider = '" + cache[0] + "' and @manager = '" + cache[1] + "' and @name = '" + cache[2] + "']";
      assertEquals("1", xpath(xml, "count(" + path + ")"), path);
    }
    assertEquals("", keys(DEFAULT));
    assertEquals("", keys("provider=ehcache2&manager=fixture&cache=clear-me"));
  }

  private static String removed(String form) throws Exception {
    HttpResponse<String> response = host.post("/bzadmin/cache/entries/remove.src", form);
    assertEquals(200, response.statusCode(), form + ": " + response.body());
    return xpath(parse(response.body()), "//remove-cache-entry/@removed");
  }

  private static String keys(String cache) throws Exception {
    return ids(parse(host.get("/bzadmin/cache/entries.src?" + cache).body()), "//entries/entry/@key");
  }

  private static String ids(Document xml, String path) throws Exception {
    NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath().evaluate(path, xml, XPathConstants.NODESET);
    List<String> values = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) values.add(nodes.item(i).getNodeValue());
    return String.join(" ", values);
  }

  private static void assertProblem(HttpResponse<String> response, int status, String problem) throws Exception {
    assertEquals(status, response.statusCode(), response.uri() + ": " + response.body());
    assertEquals(problem, xpath(parse(response.body()), "//no-cache/@problem"), response.uri().toString());
  }

}
