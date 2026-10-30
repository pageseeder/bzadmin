package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * Berlioz 0.14 metadata on the Config and Services pages of the packaged host.
 *
 * <p>The test host has services from the overlay JAR, the fixture JAR, {@code services.xml} and
 * {@code services!fixture.xml}, and secrets in its global configuration (values starting with
 * "fixture-secret"). Generator parameters are not secrets and are shown as declared.
 */
final class BerliozMetadataTest {

  private static final String SECRET = "fixture-secret";

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/bzadmin/config", "/bzadmin/services", "/fixture/direct"})
  void neverExposesSecrets(String route) throws Exception {
    for (String format : new String[]{".src", ".xml", ".json", ".html"}) {
      HttpResponse<String> response = host.get(route + format);
      if (route.startsWith("/fixture") && format.equals(".html")) continue; // No stylesheet for direct output
      assertEquals(200, response.statusCode(), route + format);
      assertFalse(response.body().contains(SECRET), route + format + " exposes a secret");
    }
    for (String view : new String[]{"generator", "tree"}) {
      assertFalse(host.get(route + ".html?group-by=" + view).body().contains(SECRET), view);
    }
  }

  // Configuration -------------------------------------------------------------------------------

  @Test
  void configurationHasBerliozMetadata() throws Exception {
    Document xml = parse(host.get("/bzadmin/config.src").body());
    for (String name : new String[]{"global-config", "berlioz-options", "berlioz-config", "error-handler-config"}) {
      assertEquals("ok", xpath(xml, "/root/content[@name='" + name + "']/@status"), name);
    }
    assertEquals("[REDACTED]", xpath(xml, "//property[@name='berlioz.control.key']/@value"));
    assertEquals("blue", xpath(xml, "//property[@name='fixture.colour']/@value"));
    assertEquals("manual", xpath(xml, "//option[@property='berlioz.xslt.cache']/@default"));
    assertEquals("0.14.0", xpath(xml, "//option[@property='berlioz.errors.problem']/@deprecated"));
    assertEquals("1.0", xpath(xml, "//option[@property='berlioz.errors.problem']/@removal"));
    assertEquals("group", xpath(xml, "//berlioz-config/servlet[@name='BerliozHTML']/stylesheet/@allocation"));
    assertEquals("ErrorHandlerServlet", xpath(xml, "//error-handler-config/servlet/@name"));
  }

  @Test
  void configurationPageShowsPropertiesAndDeploymentTabs() throws Exception {
    // Berlioz only reports initialised servlets: BerliozSource is initialised by its first request
    host.get("/bzadmin/config.src");
    String html = host.get("/bzadmin/config.html").body();
    assertTrue(html.contains("<h2>Berlioz options</h2>"), "Options");
    assertEquals(3, count(html, ">deprecated</span>"), "Options removed in Berlioz 1.0");
    assertTrue(Pattern.compile("<th scope=\"row\" data-sort=\"berlioz\\.profile\">berlioz\\.profile <button[^>]*popovertarget=\"config-info-berlioz-profile\".*?</th>.*?>default</span>", Pattern.DOTALL).matcher(html).find(), "Unconfigured option");
    assertTrue(html.contains("<h2>Other properties</h2>") && html.contains("fixture.colour"), "Other properties");
    assertFalse(html.contains("<h2>Servlets</h2>"), "Deployment in its own tab");
    assertFalse(html.contains("bridge.api-url"), "Stale explanations removed");
    assertFalse(html.contains("/bzadmin/password.xml"), "Obfuscator removed");

    String deployment = host.get("/bzadmin/config.html?view=deployment").body();
    assertTrue(deployment.contains("class=\"choice active\" aria-current=\"page\">Deployment</a>"), "Deployment tab active");
    assertTrue(deployment.contains("<h2>Servlets</h2>") && deployment.contains("BerliozHTML") && deployment.contains("BerliozSource"), "Servlets");
    assertTrue(deployment.contains("<h2>Error handler</h2>") && deployment.contains("ErrorHandlerServlet"), "Error handler");
    assertFalse(deployment.contains("<h2>Berlioz options</h2>"), "Properties in their own tab");
    assertTrue(deployment.contains("<th scope=\"row\">XSLT processor</th><td>SAXON HE 12."), "Processor of Berlioz 0.14.5");
    assertTrue(deployment.contains("<th scope=\"row\">Servlet container</th><td>jetty/10."), "Container of Berlioz 0.14.5");
  }

  // Services ------------------------------------------------------------------------------------

  @Test
  void runtimeServicesIncludeAllSourcesAndMetadata() throws Exception {
    Document xml = parse(host.get("/bzadmin/services.src").body());
    String services = "/root/content[@name='runtime-services']/live-services";
    assertEquals("ready", xpath(xml, services + "/@load-status"));
    assertEquals("home", xpath(xml, services + "/service[@id='home']/@id"), "Filesystem service");
    assertEquals("bzadmin", xpath(xml, services + "/service[@id='bzadmin-services']/@group"), "Overlay JAR service");
    assertEquals("get post", methods(xml), "Same id for two methods");
    assertEquals("fixture-flag", xpath(xml, services + "/service[@id='fixture-echo'][@method='get']/@flags"));
    assertEquals("true", xpath(xml, services + "/service[@id='fixture-direct']/@direct"));
    assertEquals("xml,json", xpath(xml, services + "/service[@id='fixture-direct']/@supported"));
    assertEquals("fixture-sort-key", xpath(xml, services + "/service[@id='fixture-echo']//parameter[@name='sort-key']/@value"), "Not redacted");
    assertEquals("/fixture/visible", xpath(xml, services + "/service[@id='fixture-echo']//parameter[@name='path']/@value"));
    assertEquals("fixture", xpath(xml, services + "/service[@id='fixture-echo'][@method='post']/@group"), "Filesystem module service");
    // Load diagnostics of Berlioz 0.14.5
    assertEquals("0", xpath(xml, "count(" + services + "/service[not(@source)])"), "Every service names its file");
    assertTrue(Integer.parseInt(xpath(xml, "count(" + services + "/source[@kind='classpath'])")) >= 2, "Overlay and fixture JARs");
    assertEquals("1", xpath(xml, "count(" + services + "/source[@kind='filesystem'][@name=" + services + "/service[@id='home']/@source])"), "Filesystem file of home");
    assertFalse(xpath(xml, services + "/@loaded").isEmpty(), "Time of the last load");
    assertEquals("0", xpath(xml, "count(" + services + "/load-failure)"));
  }

  @Test
  void servicesPageListsTheServiceFilesInLoadOrder() throws Exception {
    String html = host.get("/bzadmin/services.html").body();
    assertTrue(html.contains("<h2>Service configuration</h2>"), html);
    assertTrue(html.contains("<td>JAR overlay</td>") && html.contains("<td>Application</td>"), html);
    assertTrue(html.contains("<p>Declared in <code>"), "In each service dialog");
  }

  @Test
  void servicesLookupFindsTheServiceForEachMethod() throws Exception {
    String match = "/root/content[@name='service-match']/matching-service";
    Document get = parse(host.get("/bzadmin/services/lookup.src?url=/fixture/echo").body());
    assertEquals("fixture-echo", xpath(get, match + "/service/@id"));
    assertEquals("get", xpath(get, match + "/service/@method"));
    assertEquals("fixture-flag", xpath(get, match + "/service/@flags"), "Overlay JAR service");
    Document post = parse(host.get("/bzadmin/services/lookup.src?url=/fixture/echo&method=POST").body());
    assertEquals("fixture", xpath(post, match + "/service/@group"), "Filesystem module service");
    Document none = parse(host.get("/bzadmin/services/lookup.src?url=/fixture/echo.html").body());
    assertEquals("false", xpath(none, match + "/@matched"), "Path with extension");

    HttpResponse<String> page = host.get("/bzadmin/services/lookup.html?url=/fixture/echo&method=POST");
    assertEquals(200, page.statusCode());
    assertTrue(page.body().contains("<h2>Matching result</h2>") && page.body().contains("fixture-echo</code>"), "Matching service");
    assertTrue(page.body().contains("<li class=\"active\"><a href=\"/bzadmin/services.html\" aria-current=\"page\">Services</a>"), "Services page active");
    assertTrue(page.body().contains("class=\"choice active\" aria-current=\"page\">Lookup</a>"), "Lookup view");

    HttpResponse<String> view = host.get("/bzadmin/services.html?group-by=lookup");
    assertEquals(200, view.statusCode());
    assertTrue(view.body().contains("<form id=\"service-lookup\" action=\"/bzadmin/services/lookup.html\""), "Lookup form");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "generator", "tree"})
  void servicesPageHasOneDialogPerService(String view) throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/services.html?group-by=" + view);
    assertEquals(200, response.statusCode());
    String html = response.body();
    List<String> dialogs = matches(html, "<dialog id=\"([^\"]+)\"");
    assertEquals(dialogs.size(), new HashSet<>(dialogs).size(), "Unique dialog ids");
    Set<String> targets = new HashSet<>(matches(html, "commandfor=\"([^\"]+)\""));
    assertTrue(dialogs.containsAll(targets), "Every button opens a dialog");
    assertEquals(2, matches(html, "<h2 class=\"dialog-title\" id=\"([^\"]+)\">fixture-echo \\[").size(), "One dialog per method");
    assertTrue(html.contains(">direct</span>") && html.contains(">fixture-flag</span>"));
    if (view.isEmpty()) assertTrue(html.contains("<h2>bzadmin services"), "JAR services listed");
  }

  // Helpers -------------------------------------------------------------------------------------

  private static String methods(Document xml) throws Exception {
    return xpath(xml, "concat(//service[@id='fixture-echo'][1]/@method, ' ', //service[@id='fixture-echo'][2]/@method)");
  }

  private static int count(String text, String part) {
    int count = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) count++;
    return count;
  }

  private static List<String> matches(String text, String regex) {
    List<String> values = new ArrayList<>();
    Matcher matcher = Pattern.compile(regex).matcher(text);
    while (matcher.find()) values.add(matcher.group(1));
    return values;
  }

}
