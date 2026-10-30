package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

/**
 * XSLT listing ({@code ListXsltFiles}), documentation ({@code GetXsltDocumentation}) and sources
 * ({@code GetXsltSource}) in the packaged host.
 */
final class XsltDocumentationTest {

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
  void documentsFilesystemStylesheet() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/xslt/doc.src?path=xslt/html/default.xsl");
    assertEquals(200, response.statusCode());
    assertTrue(response.body().contains("<stylesheet name=\"default.xsl\""), response.body());
  }

  @Test
  void listsFilesystemAndClasspathStylesheets() throws Exception {
    String body = host.get("/bzadmin/xslt.src").body();
    assertTrue(body.contains("path=\"xslt/html/default.xsl\" type=\"file\" source=\"file\""), body);
    assertTrue(body.contains("path=\"xslt/html/fixture.xsl\" type=\"file\" source=\"override\""), body);
    assertTrue(body.contains("path=\"xslt/html/bzadmin.xsl\" type=\"file\" source=\"classpath\""), body);
    assertTrue(body.contains("path=\"xslt/html/bzadmin/_functions.xsl\""), body);
    String html = host.get("/bzadmin/xslt.html").body();
    assertTrue(html.contains("?path=xslt%2Fhtml%2Fbzadmin.xsl"), "Link to the overlay stylesheet");
  }

  @Test
  void documentsClasspathStylesheetWithoutHostFile() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/xslt/doc.src?path=xslt/html/bzadmin.xsl");
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains(".jar!/META-INF/berlioz/xslt/html/bzadmin.xsl\""), response.body());
    assertTrue(response.body().contains("<stylesheet name=\"_functions.xsl\""), response.body());
    assertEquals(200, host.get("/bzadmin/xslt/doc.html?path=xslt/html/bzadmin.xsl").statusCode());
  }

  @Test
  void documentsHostFileThatOverridesTheClasspath() throws Exception {
    String body = host.get("/bzadmin/xslt/doc.src?path=xslt/html/fixture.xsl").body();
    assertTrue(body.contains("path=\"xslt/html/fixture.xsl\""), "The host file, not the JAR entry: " + body);
  }

  @Test
  void documentsFixtureChainThatBerliozRenders() throws Exception {
    assertEquals(200, host.get("/fixture/render.html").statusCode(), "Berlioz rendering");
    HttpResponse<String> response = host.get("/bzadmin/xslt/doc.src?path=xslt/html/fixture.xsl");
    assertEquals(200, response.statusCode(), response.body());
    for (String name : new String[]{"fixture.xsl", "main.xsl", "sibling.xsl", "nested.xsl", "leaf.xsl"}) {
      assertTrue(response.body().contains("<stylesheet name=\"" + name + "\""), name);
    }
    assertTrue(response.body().contains("name=\"fx:leaf\""), "Declarations of the deepest stylesheet");
    HttpResponse<String> html = host.get("/bzadmin/xslt/doc.html?path=xslt/html/fixture.xsl");
    assertEquals(200, html.statusCode());
    assertTrue(html.body().contains(".jar!/META-INF/berlioz/xslt/fixture/nested/leaf.xsl"), "JAR entry path shown");
  }

  @Test
  void reportsMissingImport() throws Exception {
    HttpResponse<String> src = host.get("/bzadmin/xslt/doc.src?path=xslt/errors/missing-import.xsl");
    assertEquals(422, src.statusCode(), src.body());
    assertTrue(src.body().contains("<xsltdoc-error path=\"xslt/errors/missing-import.xsl\" type=\"not-found\">"), src.body());
    HttpResponse<String> html = host.get("/bzadmin/xslt/doc.html?path=xslt/errors/missing-import.xsl");
    assertEquals(422, html.statusCode());
    assertTrue(html.body().contains("'xslt/errors/does-not-exist.xsl' was not found"), "Rendered in the page");
  }

  @Test
  void rejectsPathsOutsideTheXsltFolder() throws Exception {
    for (String path : new String[]{"config/services.xml", "xslt/../config/config-local.xml", "xslt/html/none.xsl",
        "xslt/../services.xml", "xslt", "xslt/html"}) {
      assertEquals(400, host.get("/bzadmin/xslt/doc.src?path=" + path).statusCode(), path);
      assertEquals(400, host.get("/bzadmin/xslt/source.src?path=" + path).statusCode(), path);
    }
  }

  @Test
  void returnsTheSourceOfAFilesystemStylesheet() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/xslt/source.src?path=xslt/html/fixture.xsl");
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("<xslt-source path=\"xslt/html/fixture.xsl\" name=\"fixture.xsl\" location=\"file\""), response.body());
    assertTrue(response.body().contains("&lt;xsl:"), "Source as text: " + response.body());
  }

  @Test
  void returnsTheSourceOfAClasspathStylesheet() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/xslt/source.src?path=xslt/html/bzadmin/_functions.xsl");
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("location=\"classpath\""), response.body());
    assertTrue(response.body().contains("truncated=\"false\""), response.body());
    assertTrue(response.body().contains("f:to-path"), response.body());
    HttpResponse<String> html = host.get("/bzadmin/xslt/source.html?path=xslt/html/bzadmin/_functions.xsl");
    assertEquals(200, html.statusCode());
    assertTrue(html.body().contains("<xml-source><pre><code>"), html.body());
  }

  @Test
  void returnsTheSourceOfAStylesheetThatCannotBeDocumented() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/xslt/source.src?path=xslt/errors/missing-import.xsl");
    assertEquals(200, response.statusCode(), response.body());
    assertTrue(response.body().contains("does-not-exist.xsl"), response.body());
  }

}
