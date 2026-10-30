package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The admin operations against disposable fixtures (I3.2): log files in a directory of the test
 * task, the caches of the test host, generator statistics and system properties.
 */
final class AdminOperationsTest {

  private static @Nullable BerliozHost host;

  private static Path logs;

  private static Path outside;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
    logs = Paths.get(System.getProperty("bzadmin.test.logs"));
    outside = Files.createDirectories(logs.resolveSibling(logs.getFileName() + "-outside"));
    Files.writeString(outside.resolve("secret.log"), "fixture-secret-outside-log\n");
    Files.writeString(logs.resolve("short.log"), "one INFO\ntwo WARN\r\nthree\n");
    Files.writeString(logs.resolve("notes.txt"), "fixture-secret-not-a-log\n");
    Files.writeString(logs.resolve("long.log"), "x".repeat(100_000) + "\nend\n");
    List<String> lines = new ArrayList<>();
    for (int i = 1; i <= 120_000; i++) lines.add("line " + i + " INFO " + "y".repeat(40));
    Files.write(logs.resolve("big.log"), lines);
    Files.createSymbolicLink(logs.resolve("link.log"), outside.resolve("secret.log"));
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void listsTheLogFiles() throws Exception {
    Document xml = parse(host.get("/bzadmin/logs.src").body());
    assertEquals("detected", xpath(xml, "//log-files/@origin"), "The file appender of logback");
    for (String name : new String[]{"fixture.log", "short.log", "big.log"}) {
      assertEquals(LogIds.fixture(name), xpath(xml, "//log-file[@name='" + name + "']/@id"), name);
    }
    for (String name : new String[]{"notes.txt", "link.log", "secret.log"}) {
      assertEquals("0", xpath(xml, "count(//log-file[@name='" + name + "'])"), name);
    }
    String html = host.get("/bzadmin/logs.html?view=files").body();
    assertTrue(html.contains("<option value=\"" + LogIds.fixture("short.log") + "\">short.log"), "The Files view selects files by ID");
  }

  @Test
  void readsALogFile() throws Exception {
    HttpResponse<String> response = raw("short.log", "");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals("short.log", xpath(xml, "//log/@name"));
    assertEquals("3", xpath(xml, "count(//log/line)"));
    assertEquals("false", xpath(xml, "//log/@truncated"));
    assertEquals("1", xpath(xml, "//log/line[1]/@n"));
    assertEquals("WARN", xpath(xml, "//log/line[2]/@level"));
    assertEquals("two WARN", xpath(xml, "//log/line[2]"), "No carriage return");
    String id = LogIds.fixture("short.log");
    assertEquals(200, host.get("/bzadmin/logs/raw.html?id=" + id).statusCode());
    String json = host.get("/bzadmin/logs/raw.json?id=" + id).body();
    assertJson(json, "\"truncated\":false");
    assertJson(json, "\"lines\":[{\"position\":0,\"n\":1,\"level\":\"INFO\",\"text\":\"one INFO\"}");
  }

  /**
   * The lines added since a previous response, from its cursor; the end of the file again when the
   * file was replaced.
   */
  @Test
  void readsTheLinesAddedSinceACursor() throws Exception {
    Path follow = logs.resolve("follow.log");
    Files.writeString(follow, "one\ntwo");
    Document first = parse(raw("follow.log", "").body());
    assertEquals("true", xpath(first, "//log/line[2]/@incomplete"));
    String cursor = xpath(first, "//log/@cursor");
    Document none = parse(raw("follow.log", "&cursor=" + encode(cursor)).body());
    assertEquals("1", xpath(none, "count(//log/line)"), "The incomplete line again");
    Files.writeString(follow, " INFO\nthree\n", StandardOpenOption.APPEND);
    Document next = parse(raw("follow.log", "&cursor=" + encode(cursor)).body());
    assertEquals("false", xpath(next, "//log/@reset"));
    assertEquals("2", xpath(next, "count(//log/line)"));
    assertEquals("two INFO", xpath(next, "//log/line[1]"));
    assertEquals("three", xpath(next, "//log/line[2]"));
    assertEquals("2", xpath(next, "//log/line[1]/@n"));
    String html = host.get("/bzadmin/logs/raw.html?id=" + LogIds.fixture("follow.log") + "&cursor=" + encode(cursor)).body();
    assertTrue(html.contains("name=\"cursor\" value=\"" + xpath(next, "//log/@cursor") + "\""), "The page has the next cursor");
    // Rotated
    Files.move(follow, logs.resolve("follow.log.1"), StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(follow, "new\n");
    Document rotated = parse(raw("follow.log", "&cursor=" + encode(xpath(next, "//log/@cursor"))).body());
    assertEquals("true", xpath(rotated, "//log/@reset"));
    assertEquals("1", xpath(rotated, "count(//log/line)"));
    assertEquals("new", xpath(rotated, "//log/line[1]"));
    // Not a cursor: the end of the file
    Document invalid = parse(raw("follow.log", "&cursor=../../x").body());
    assertEquals("true", xpath(invalid, "//log/@reset"));
  }

  /**
   * The lines before the first line of a previous response, from its {@code before} cursor.
   */
  @Test
  void readsTheLinesBefore() throws Exception {
    Files.writeString(logs.resolve("earlier.log"), "one\ntwo\nthree\nfour\n");
    Document last = parse(raw("earlier.log", "&lines=1").body());
    assertEquals("four", xpath(last, "//log/line[1]"));
    assertEquals("14", xpath(last, "//log/line[1]/@position"));
    String before = xpath(last, "//log/@before");
    Document earlier = parse(raw("earlier.log", "&lines=2&before=" + encode(before)).body());
    assertEquals("false", xpath(earlier, "//log/@reset"));
    assertEquals("2", xpath(earlier, "count(//log/line)"));
    assertEquals("two", xpath(earlier, "//log/line[1]"));
    assertEquals("2", xpath(earlier, "//log/line[1]/@n"));
    Document first = parse(raw("earlier.log", "&before=" + encode(xpath(earlier, "//log/@before"))).body());
    assertEquals("one", xpath(first, "//log/line[1]"));
    assertEquals("", xpath(first, "//log/@before"), "The first line of the file");
    // Not a cursor: the end of the file
    Document invalid = parse(raw("earlier.log", "&before=x").body());
    assertEquals("true", xpath(invalid, "//log/@reset"));
    assertEquals("4", xpath(invalid, "count(//log/line)"));
  }

  @Test
  void boundsTheLinesRead() throws Exception {
    Document xml = parse(raw("big.log", "&lines=2147483647").body());
    assertEquals("5000", xpath(xml, "count(//log/line)"));
    assertEquals("true", xpath(xml, "//log/@truncated"));
    assertEquals("", xpath(xml, "//log/line[1]/@n"), "Line numbers unknown: only the end of the file is read");
    assertTrue(xpath(xml, "//log/line[last()]").startsWith("line 120000 "));
    Document defaults = parse(raw("short.log", "&lines=0").body());
    assertEquals("1", xpath(defaults, "count(//log/line)"), "At least one line");
  }

  @Test
  void boundsTheLineLength() throws Exception {
    Document xml = parse(raw("long.log", "").body());
    assertEquals("8193", xpath(xml, "string-length(//log/line[1])"));
    assertEquals("end", xpath(xml, "//log/line[2]"));
  }

  /**
   * Reads of a large file at the same time: each is bounded, and at most a few run at once (the
   * others wait, or are refused with 503).
   */
  @Test
  void boundsConcurrentReads() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(16);
    try {
      List<Future<HttpResponse<String>>> responses = new ArrayList<>();
      for (int i = 0; i < 16; i++) responses.add(executor.submit(() -> raw("big.log", "&lines=5000")));
      int ok = 0;
      for (Future<HttpResponse<String>> future : responses) {
        HttpResponse<String> response = future.get(60, TimeUnit.SECONDS);
        if (response.statusCode() == 503) {
          assertEquals("busy", xpath(parse(response.body()), "//no-log/@reason"));
          continue;
        }
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("5000", xpath(parse(response.body()), "count(//log/line)"));
        ok++;
      }
      assertTrue(ok >= 4, ok + " reads");
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Only the {@code .log} files listed in a log directory, by ID: no name or path from the request
   * reaches the file system.
   */
  @ParameterizedTest
  @ValueSource(strings = {"outside", "notes.txt", "link.log", "link-target", "missing.log", "../short.log", "/etc/hosts",
      "etc-hosts", "SHORT", "short.log"})
  void readsOnlyListedLogFiles(String name) throws Exception {
    String id;
    switch (name) {
      case "outside": id = LogIds.of(outside.toRealPath().resolve("secret.log")); break;
      case "link-target": id = LogIds.of(logs.resolve("link.log").toRealPath()); break;
      case "etc-hosts": id = LogIds.of(Paths.get("/etc/hosts")); break;
      case "SHORT": id = LogIds.fixture("short.log").toUpperCase(); break;
      case "notes.txt": case "link.log": case "missing.log": id = LogIds.fixture(name); break;
      default: id = name;
    }
    HttpResponse<String> response = host.get("/bzadmin/logs/raw.src?id=" + encode(id));
    assertEquals(404, response.statusCode(), name);
    assertFalse(response.body().contains("fixture-secret"), name);
    assertEquals(id, xpath(parse(response.body()), "//no-log/@id"));
    HttpResponse<String> html = host.get("/bzadmin/logs/raw.html?id=" + encode(id));
    assertEquals(404, html.statusCode(), name);
    assertFalse(html.body().contains("fixture-secret"), name);
    assertTrue(html.body().contains("No such log file"), name);
  }

  /**
   * By name, as typed in a URL: only compared with the names of the files found, never a path.
   */
  @Test
  void readsALogFileByName() throws Exception {
    for (String name : new String[]{"short.log", logs.getFileName() + "/short.log"}) {
      HttpResponse<String> response = host.get("/bzadmin/logs/raw.src?name=" + encode(name));
      assertEquals(200, response.statusCode(), name);
      Document xml = parse(response.body());
      assertEquals(LogIds.fixture("short.log"), xpath(xml, "//log/@id"), name);
      assertEquals("3", xpath(xml, "count(//log/line)"), name);
    }
    String html = host.get("/bzadmin/logs/raw.html?name=short.log").body();
    assertTrue(html.contains("href=\"/bzadmin/logs/raw.html?name=short.log\""), "A link by name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"../test-logs-outside/secret.log", "secret.log", "notes.txt", "link.log", "missing.log",
      "./short.log", "/etc/hosts", "SHORT.LOG", "other/short.log", "short.log/", "%2e%2e%2fsecret.log"})
  void readsOnlyListedLogFilesByName(String name) throws Exception {
    String path = name.startsWith("../") ? "../" + logs.getFileName() + "-outside/secret.log" : name;
    HttpResponse<String> response = host.get("/bzadmin/logs/raw.src?name=" + encode(path));
    assertEquals(404, response.statusCode(), path);
    assertFalse(response.body().contains("fixture-secret"), path);
    assertEquals(path, xpath(parse(response.body()), "//no-log/@name"));
    HttpResponse<String> html = host.get("/bzadmin/logs/raw.html?name=" + encode(path));
    assertEquals(404, html.statusCode(), path);
    assertFalse(html.body().contains("fixture-secret"), path);
  }

  @Test
  void requiresALogIdOrName() throws Exception {
    assertEquals(400, host.get("/bzadmin/logs/raw.src").statusCode());
  }

  @Test
  void setsTheRecentEventsThreshold() throws Exception {
    try {
      HttpResponse<String> response = host.post("/bzadmin/logs/recent/threshold.src", "threshold=WARN");
      assertEquals(200, response.statusCode(), response.body());
      assertEquals("WARN", xpath(parse(response.body()), "//recent-logs-threshold/@level"));
      assertEquals(400, host.post("/bzadmin/logs/recent/threshold.src", "threshold=BOGUS").statusCode());
    } finally {
      host.post("/bzadmin/logs/recent/threshold.src", "threshold=INFO");
    }
  }

  /**
   * The recent events of the logback adapter: the audit of the threshold operation is captured
   * (logged once the response is sent, so it may take a moment).
   */
  @Test
  void capturesRecentEvents() throws Exception {
    assertEquals(200, host.post("/bzadmin/logs/recent/threshold.src", "threshold=INFO").statusCode());
    String audit = "count(//event[@logger='org.pageseeder.bzadmin.audit'][@level='INFO']"
        + "[contains(@message, 'POST /bzadmin/logs/recent/threshold.src user=operator status=200')])";
    Document xml = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      xml = parse(host.get("/bzadmin/logs/recent.src").body());
      if (!"0".equals(xpath(xml, audit))) break;
      Thread.sleep(100);
    }
    assertNotEquals("0", xpath(xml, audit), "The audit of the operation");
    assertEquals("logback", xpath(xml, "//recent-logs/@backend"));
    assertFalse(xpath(xml, "//recent-logs/@version").isEmpty(), "The version of logback");
    assertEquals("INFO", xpath(xml, "//recent-logs/@threshold"));
    assertEquals("INFO", xpath(xml, "//recent-logs/@root-level"), "The level of logback-test.xml");
    assertEquals("256", xpath(xml, "//recent-logs/@capacity"));
    assertTrue(Long.parseLong(xpath(xml, "//recent-logs/@captured")) >= Long.parseLong(xpath(xml, "count(//event)")));
    assertJson(host.get("/bzadmin/logs/recent.json").body(), "\"backend\":\"logback\",\"version\":\"" + xpath(xml, "//recent-logs/@version") + "\",\"threshold\":\"INFO\"");
    assertJson(host.get("/bzadmin/logs.json").body(), "\"origin\":\"detected\"");
    String html = host.get("/bzadmin/logs/recent.html").body();
    assertTrue(html.contains("<span class=\"input-group-label\">Capture threshold</span>"), html);
    assertTrue(html.contains("<option value=\"INFO\" selected>Info (default)</option>"), html);
  }

  @Test
  void redactsSensitiveSystemProperties() throws Exception {
    for (String format : new String[]{".src", ".json", ".html"}) {
      HttpResponse<String> response = host.get("/bzadmin/system/properties" + format);
      assertEquals(200, response.statusCode(), format);
      assertFalse(response.body().contains("fixture-secret"), format);
      assertTrue(response.body().contains("fixture.database.password"), format);
    }
    assertJson(host.get("/bzadmin/system/properties.json").body(),
        "\"name\":\"fixture.database.password\",\"value\":\"[REDACTED]\"");
    Document xml = parse(host.get("/bzadmin/system/properties.src").body());
    assertEquals("[REDACTED]", xpath(xml, "//property[@name='fixture.database.password']/@value"));
    assertEquals(System.getProperty("java.version"), xpath(xml, "//property[@name='java.version']/@value"));
  }

  /**
   * Only a POST resets the statistics: GET requests ignore {@code reset=true}. The statistics are
   * those of the generators in the services, so of the guarded ones.
   */
  @Test
  void resetsStatisticsOnlyWithPost() throws Exception {
    String listLibraries = "//statistic[@generator='org.pageseeder.bzadmin.guard.berlioz.system.ListLibraries']";
    host.get("/bzadmin/application.src");
    host.get("/bzadmin/application.src?reset=true");
    host.get("/bzadmin/application/statistics.src?reset=true");
    assertEquals("1", xpath(parse(host.get("/bzadmin/application/statistics.src").body()), "count(" + listLibraries + ")"));
    HttpResponse<String> reset = host.post("/bzadmin/application/statistics/reset.src", "");
    assertEquals(200, reset.statusCode(), reset.body());
    assertEquals("0", xpath(parse(host.get("/bzadmin/application/statistics.src").body()), "count(" + listLibraries + ")"));
  }

  @Test
  void managesCacheEntries() throws Exception {
    String cache = AccessPolicyTest.CACHE;
    // The static caching filter of the host (FixtureStaticCache) records the files it serves
    assertEquals(200, host.get("/css/fixture.css").statusCode());
    Document entries = parse(host.get("/bzadmin/cache/entries.src?" + cache + "&limit=1000").body());
    String key = xpath(entries, "//entries/entry[contains(@key, 'fixture.css')][1]/@key");
    assertFalse(key.isEmpty(), "Cached stylesheet");
    assertEquals(200, host.get("/bzadmin/cache/info.src?" + cache).statusCode());
    assertEquals(200, host.get("/bzadmin/cache/entries.src?" + cache + "&offset=2147483647&limit=2147483647").statusCode());

    HttpResponse<String> removed = host.post("/bzadmin/cache/entries/remove.src", cache + "&key=" + encode(key));
    assertEquals(200, removed.statusCode(), removed.body());
    assertEquals("true", xpath(parse(removed.body()), "//remove-cache-entry/@removed"));

    assertEquals(200, host.get("/css/fixture.css").statusCode());
    assertEquals(200, host.post("/bzadmin/cache/clear.src", cache).statusCode());
    assertEquals("0", xpath(parse(host.get("/bzadmin/cache/entries.src?" + cache).body()), "count(//entries/entry)"));
    assertEquals(200, host.post("/bzadmin/cache/clear.src", "").statusCode());
    assertJson(host.post("/bzadmin/cache/clear.json", "").body(), "\"caches\":[{\"provider\":\"");
  }

  @Test
  void listsXsltFilesInJson() throws Exception {
    String json = host.get("/bzadmin/xslt.json").body();
    assertJson(json, "\"name\":\"bzadmin.xsl\",\"path\":\"xslt/html/bzadmin.xsl\",\"type\":\"file\",\"source\":\"classpath\"");
    assertJson(json, "\"type\":\"folder\",\"files\":[");
  }

  /**
   * Unknown caches have a defined response, not a 500.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/bzadmin/cache/info.src", "/bzadmin/cache/entries.src"})
  void readsUnknownCache(String path) throws Exception {
    assertEquals(404, host.get(path + "?provider=ehcache2&manager=__DEFAULT__&cache=no-such-cache").statusCode(), path);
    assertEquals(404, host.get(path + "?provider=ehcache2&manager=no-such-manager&cache=PSML").statusCode(), path);
    assertEquals(404, host.get(path + "?provider=no-such-provider&manager=__DEFAULT__&cache=PSML").statusCode(), path);
    assertEquals(400, host.get(path + "?cache=PSML").statusCode(), path);
  }

  @Test
  void changesUnknownCache() throws Exception {
    String unknown = "provider=ehcache2&manager=__DEFAULT__&cache=no-such-cache";
    assertEquals(404, host.post("/bzadmin/cache/entries/remove.src", unknown + "&key=x").statusCode());
    assertEquals(404, host.post("/bzadmin/cache/clear.src", unknown).statusCode());
    assertEquals(400, host.post("/bzadmin/cache/clear.src", "cache=no-such-cache").statusCode());
    HttpResponse<String> json = host.post("/bzadmin/cache/clear.json", unknown);
    assertEquals(404, json.statusCode());
    assertJson(json.body(), "\"problem\":\"unknown-cache\"");
  }

  /**
   * Operations that change state are POST only: Berlioz finds no GET service (404, not 405).
   */
  @ParameterizedTest
  @ValueSource(strings = {
      "/bzadmin/cache/clear", "/bzadmin/cache/entries/remove?key=x", "/bzadmin/cache/statistics?enable=true",
      "/bzadmin/cache/statistics/reset",
      "/bzadmin/logs/recent/threshold?threshold=WARN", "/bzadmin/application/statistics/reset",
      "/bzadmin/config/reload", "/bzadmin/services/reload", "/bzadmin/xslt/cache/clear"})
  void rejectsGetForOperations(String path) throws Exception {
    int query = path.indexOf('?');
    String target = query < 0 ? path + ".src" : path.substring(0, query) + ".src" + path.substring(query);
    assertEquals(404, host.get(target).statusCode(), target);
  }

  private static HttpResponse<String> raw(String name, String query) throws Exception {
    return host.get("/bzadmin/logs/raw.src?id=" + LogIds.fixture(name) + query);
  }

  /**
   * Asserts that the JSON, without whitespace between tokens, contains the fragment.
   */
  private static void assertJson(String json, String fragment) {
    String compact = json.replaceAll("\\s*([{}\\[\\],:])\\s*", "$1");
    assertTrue(compact.contains(fragment), () -> fragment + " in " + (compact.length() > 2000 ? compact.substring(0, 2000) + "…" : compact));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

}
