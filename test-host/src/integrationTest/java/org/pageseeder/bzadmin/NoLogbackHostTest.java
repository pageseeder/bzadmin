package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The test host without logback (the {@code warNoLogbackHost} task): logback is an optional adapter
 * of the overlay, so the admin pages work, without recent events or detected log files.
 */
final class NoLogbackHostTest {

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    String war = System.getProperty("bzadmin.war.no-logback");
    assumeTrue(war != null, "No host without logback for this task");
    try (ZipFile zip = new ZipFile(war)) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        assertFalse(entry.getName().startsWith("WEB-INF/lib/logback-"), entry.getName());
      }
      assertTrue(zip.stream().anyMatch(entry -> entry.getName().startsWith("WEB-INF/lib/bzadmin-")), "The overlay");
    }
    host = BerliozHost.startWithoutLogback();
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void showsTheLogsPageWithoutRecentEvents() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/logs.src");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals("none", xpath(xml, "//log-files/@origin"), "No file appender detected");
    assertEquals("0", xpath(xml, "count(//recent-logs)"));
    String backend = xpath(xml, "//no-recent-logs/@backend");
    assertFalse(backend.isEmpty());
    assertFalse(backend.contains("logback"), backend);

    String html = host.get("/bzadmin/logs.html").body();
    assertTrue(html.contains("Recent events need logback: the logging framework of this application is <code>" + backend + "</code>"), html);
    String files = host.get("/bzadmin/logs.html?view=files").body();
    assertTrue(files.contains("list their directories or files in the global property <code>berlioz.admin.logs</code>"), files);
    assertEquals(200, host.get("/bzadmin/logs/recent.html").statusCode());
    assertEquals(200, host.get("/bzadmin/logs/recent.json").statusCode());
    assertEquals(200, host.get("/bzadmin/logs.json").statusCode());
  }

  @Test
  void reportsTheThresholdAsNotImplemented() throws Exception {
    HttpResponse<String> response = host.post("/bzadmin/logs/recent/threshold.src", "threshold=WARN");
    assertEquals(501, response.statusCode(), response.body());
    assertFalse(xpath(parse(response.body()), "//no-recent-logs/@backend").isEmpty());
    assertEquals(400, host.post("/bzadmin/logs/recent/threshold.src", "threshold=BOGUS").statusCode());
  }

  @Test
  void servesTheOtherPages() throws Exception {
    for (String path : new String[]{"/bzadmin/config.html", "/bzadmin/services.html", "/bzadmin/system.html", "/bzadmin/files.html"}) {
      assertEquals(200, host.get(path).statusCode(), path);
    }
    assertEquals(404, host.get("/bzadmin/logs/raw.src?name=fixture.log").statusCode());
  }

}
