package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.http.HttpResponse;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import org.jspecify.annotations.Nullable;

/**
 * The overlay JAR deployed in a host application, at the root context.
 */
final class PackagedHostTest {

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
  void runsOnTheExpectedJavaVersion() {
    assertEquals(System.getProperty("bzadmin.java.expected"), Integer.toString(Runtime.version().feature()));
  }

  /**
   * Admin GET routes that need no parameter (baseline smoke matrix). The host has no
   * {@code WEB-INF/xslt/html/bzadmin.xsl}: Berlioz uses the group stylesheet of the overlay JAR.
   */
  @ParameterizedTest
  @MethodSource("org.pageseeder.bzadmin.AdminRoutes#routes")
  void servesAdminRoute(String route, int status) throws Exception {
    assertEquals(status, host.get(route + ".src").statusCode(), route + ".src");
    HttpResponse<String> html = host.get(route + ".html");
    assertEquals(status, html.statusCode(), route + ".html");
    if (status == 200) {
      String page = "page-" + html.body().replaceAll("(?s).*<body[^>]*class=\"[^\"]*page-(bzadmin[-a-z]*).*", "$1");
      assertTrue(page.startsWith("page-bzadmin"), "Rendered by the overlay stylesheet: " + route);
    }
  }

  /**
   * Every static file of the overlay JAR is served and used by the pages: no missing or stale
   * bundle, font or image.
   */
  @Test
  void servesTheStaticFilesOfThisVersion() throws Exception {
    assertEquals(StaticFiles.packaged(host), StaticFiles.load(host));
  }

  /**
   * The host's caching of static files (the wrapper's {@code StaticCacheControlFilter}, which
   * replaced Bastille's), with the container's validators.
   */
  @Test
  void cachesStaticFilesWithTheHostFilter() throws Exception {
    HttpResponse<String> script = host.get("/js/bzadmin-" + System.getProperty("bzadmin.version") + ".min.js");
    assertEquals(200, script.statusCode());
    assertEquals("max-age=86400, stale-while-revalidate=604800", script.headers().firstValue("Cache-Control").orElse(""));
    assertTrue(script.headers().firstValue("Last-Modified").isPresent() || script.headers().firstValue("ETag").isPresent(),
        "Validators: " + script.headers().map());
  }

  /**
   * Berlioz 0.14.3 metadata generators exposed by test-only services in the fixture JAR.
   */
  @ParameterizedTest
  @ValueSource(strings = {"berlioz-config", "error-handler-config", "global-config", "runtime-services"})
  void runsBerliozMetadataGenerator(String name) throws Exception {
    HttpResponse<String> response = host.get("/fixture/" + name + ".src");
    assertEquals(200, response.statusCode());
    Document xml = parse(response.body());
    assertEquals("ok", xpath(xml, "/root/content[@name='" + name + "']/@status"), response.body());
  }

  @Test
  void runtimeServicesIncludeFilesystemAndJarServices() throws Exception {
    Document xml = parse(host.get("/fixture/runtime-services.src").body());
    assertEquals("ready", xpath(xml, "//live-services/@load-status"));
    for (String id : new String[]{"home", "bzadmin-config", "fixture-render"}) {
      assertEquals(id, xpath(xml, "//live-services/service[@id='" + id + "'][1]/@id"), "Service " + id);
    }
  }

  /**
   * The filesystem group stylesheet takes precedence over the one in the fixture JAR, and imports
   * the fixture JAR stylesheet by classpath URI, which imports a sibling and includes a nested
   * stylesheet relatively.
   */
  @Test
  void rendersStylesheetsImportedFromJarByClasspathUri() throws Exception {
    HttpResponse<String> response = host.get("/fixture/render.html");
    assertEquals(200, response.statusCode(), response.body());
    for (String marker : new String[]{"fixture-main", "fixture-sibling", "fixture-nested", "fixture-leaf"}) {
      assertTrue(response.body().contains(marker), marker);
    }
    assertFalse(response.body().contains("fixture-classpath-group"), "Filesystem stylesheet overrides the classpath one");
  }

  static Document parse(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  static String xpath(Document xml, String expression) throws Exception {
    return (String) XPathFactory.newInstance().newXPath().evaluate(expression, xml, XPathConstants.STRING);
  }

}
