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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import org.jspecify.annotations.Nullable;

/**
 * The legacy host mapped by prefix (the {@code warPrefixHost} task): no kickstart, with Berlioz on
 * {@code /html/*} and {@code /xml/*} and the admin filter on the admin services of both.
 */
final class PrefixHostTest {

  private static final String SAMPLE = "/xml/bzadmin/system/cpu/sample";

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    String war = System.getProperty("bzadmin.war.prefix");
    assumeTrue(war != null, "No host mapped by prefix for this task");
    try (ZipFile zip = new ZipFile(war)) {
      for (ZipEntry entry : Collections.list(zip.entries())) {
        assertFalse(entry.getName().startsWith("WEB-INF/lib/pso-berlioz-kickstart-"), entry.getName());
      }
    }
    host = BerliozHost.startMappedByPrefix();
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @ParameterizedTest
  @MethodSource("org.pageseeder.bzadmin.AdminRoutes#routes")
  void servesAdminPages(String route, int status) throws Exception {
    HttpResponse<String> response = host.get("/html" + route);
    assertEquals(status, response.statusCode(), route + ": " + response.body());
    // Without a mapped error handler, errors are the container's, with its own directives
    assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"), route);
  }

  @Test
  void servesDirectServicesAsXml() throws Exception {
    HttpResponse<String> response = host.get(SAMPLE);
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/xml"));
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
    assertTrue(response.body().startsWith("<cpu-sample "), response.body());
  }

  @Test
  void servesTheXmlOfPagesWithoutTheirHeader() throws Exception {
    HttpResponse<String> response = host.get("/xml/bzadmin/config");
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("<root service=\"bzadmin-config\""), response.body());
    assertFalse(response.body().contains("<header>"), response.body());
  }

  /**
   * The operations reached through the URLs that the pages render.
   */
  @ParameterizedTest
  @CsvSource({
      "/html/bzadmin/config,   /xml/bzadmin/config/reload,     true,     'config, services, xslt, etags'",
      "/html/bzadmin/services, /xml/bzadmin/services/reload,   services, services",
      "/html/bzadmin/xslt,     /xml/bzadmin/xslt/cache/clear,  xslt,     xslt"})
  void reloadsThroughTheRenderedUrls(String page, String action, String scope, String applied) throws Exception {
    assertTrue(host.get(page).body().contains("action=\"" + action + "\""), page);
    HttpResponse<String> response = host.post(action, "berlioz-reload=" + scope);
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(applied, response.headers().firstValue("X-Berlioz-Reload").orElse(""));
    assertEquals("true", xpath(parse(response.body()), "//berlioz-control/@authorized"));
  }

  @Test
  void rendersLiveUpdatesAndOperationsOnTheXmlMapping() throws Exception {
    assertTrue(host.get("/html/bzadmin/system").body().contains("data-href=\"" + SAMPLE + "\""));
    assertTrue(host.get("/html/bzadmin/logs/recent").body().contains("action=\"/xml/bzadmin/logs/recent/threshold\""));
  }

  @Test
  void hasNoExtensionMapping() throws Exception {
    assertEquals(404, host.get("/bzadmin/config.html").statusCode());
  }

  @Test
  void deniesAccessOnBothPrefixes() throws Exception {
    assertEquals(403, host.as(null).get("/html/bzadmin/config").statusCode());
    assertEquals(403, host.as(null).get(SAMPLE).statusCode());
  }

  @Test
  void acceptsOperationsOnlyFromOperatorsAndTheAdminPages() throws Exception {
    String reload = "/xml/bzadmin/config/reload";
    assertEquals(403, host.as("viewer").post(reload, "berlioz-reload=true").statusCode());
    assertEquals(403, host.send("POST", reload, "berlioz-reload=true").statusCode());
  }

}
