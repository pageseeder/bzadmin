package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.jspecify.annotations.Nullable;

/**
 * The overlay in a host deployed at a non-root context path: every link, form, script URL and
 * static file stays within the context.
 */
final class NonRootContextTest {

  /** An attribute whose value is a root-relative path to the overlay, outside the context. */
  private static final Pattern ROOT_RELATIVE = Pattern.compile("[-a-z]+=\"/(bzadmin|js|css)/[^\"]*\"");

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/admin-host");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @ParameterizedTest
  @MethodSource("org.pageseeder.bzadmin.AdminRoutes#routes")
  void servesAdminRouteWithinTheContext(String route, int status) throws Exception {
    assertEquals(status, host.get(route + ".src").statusCode(), route + ".src");
    HttpResponse<String> html = host.get(route + ".html");
    assertEquals(status, html.statusCode(), route + ".html");
    if (status != 200) return;
    Matcher outside = ROOT_RELATIVE.matcher(html.body());
    assertFalse(outside.find(), () -> "Root-relative path outside the context in " + route + ": " + outside.group());
  }

  @ParameterizedTest
  @ValueSource(strings = {"/bzadmin/config", "/bzadmin/services"})
  void servesMetadataPages(String route) throws Exception {
    for (String format : new String[]{".src", ".json", ".html"}) {
      HttpResponse<String> response = host.get(route + format);
      assertEquals(200, response.statusCode(), route + format);
      assertFalse(response.body().contains("fixture-secret"), route + format);
    }
    String html = host.get(route + ".html").body();
    assertTrue(html.contains("href=\"/admin-host/bzadmin/services.html\""), "Navigation within the context");
    assertTrue(html.contains("href=\"/admin-host/css/bzadmin-"), "Stylesheet within the context");
    assertTrue(html.contains("href=\"/admin-host/js/bzadmin-"), "Script preload within the context");
  }

  @Test
  void linksPsmlFilesWithinTheContext() throws Exception {
    String folder = host.get("/bzadmin/files.html?path=/content").body();
    assertTrue(folder.contains("href=\"/admin-host/bzadmin/files/document.html?root=psml&amp;path=%2Fcontent%2Fhome.psml\""), folder);
    assertTrue(folder.contains("data-folders=\"/admin-host/bzadmin/files/folders.html\""), "Tree loaded within the context");
    String document = host.get("/bzadmin/files/document.html?path=/content/home.psml").body();
    assertTrue(document.contains("href=\"/admin-host/bzadmin/files.html?root=psml&amp;path=%2Fcontent\""), document);
  }

  @Test
  void servesStaticFilesWithinTheContext() throws Exception {
    assertEquals(StaticFiles.packaged(host), StaticFiles.load(host));
  }

}
