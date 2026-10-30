package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The access policy (M1.1, M1.2 for operations): without the admin filter, with the filter mapped to other URLs or
 * before the host's authentication, and without a grant, every admin route and format is denied
 * with no generator output or ETag; administrators, by {@code AdminAccess}, container role or the
 * fallback login, are allowed with their permissions, for every host example (M1.3).
 *
 * <p>Negative cases (M1.4): the JAR included by a host that knows nothing of it, the filter without
 * authentication, access asserted by parameters or headers, an {@code AdminAccess} without
 * permission, HEAD and the other methods, equivalent URLs and other formats, includes and error
 * pages.
 */
final class AccessPolicyTest {

  private static final String DENIED = "Access to Berlioz administration is not configured for this request.";

  private static final String SIGN_IN = "Sign in to use Berlioz administration.";

  private static final List<String> FORMATS = List.of(".src", ".xml", ".json", ".html");

  /** A cache of the host (the static caching filter's), named by provider, manager and name. */
  static final String CACHE = "provider=ehcache2&manager=__DEFAULT__&cache=static-web-caching";

  /**
   * The services of a handler, which write its output only, whatever the format (no envelope or
   * XSLT), with the query that would run it.
   */
  static final List<String> HANDLERS = List.of("/bzadmin/system/cpu/sample", "/bzadmin/system/runtime/sample",
      "/bzadmin/cache/info/sample?" + CACHE);

  /**
   * Every admin service: the GET routes without parameter, and the others with the parameters they
   * need, so that an allowed request would run its generators.
   */
  static Stream<Arguments> services() {
    Stream<Arguments> routes = AdminRoutes.routes().map(route -> Arguments.of("GET", route.get()[0], ""));
    Stream<Arguments> others = Stream.of(
        Arguments.of("GET", "/bzadmin/cache/info", CACHE),
        Arguments.of("GET", "/bzadmin/cache/entries", CACHE),
        Arguments.of("GET", "/bzadmin/xslt/doc", "path=xslt/html/bzadmin.xsl"),
        Arguments.of("GET", "/bzadmin/xslt/source", "path=xslt/html/bzadmin.xsl"),
        Arguments.of("GET", "/bzadmin/files/document", "path=/content/home.psml"),
        Arguments.of("GET", "/bzadmin/logs/raw", "id=" + LogIds.fixture("fixture.log")),
        Arguments.of("GET", "/bzadmin/service/match", "url=/bzadmin/config.html"),
        Arguments.of("GET", "/bzadmin/services/lookup", "url=/bzadmin/config"),
        Arguments.of("POST", "/bzadmin/config/reload", ""),
        Arguments.of("POST", "/bzadmin/services/reload", ""),
        Arguments.of("POST", "/bzadmin/xslt/cache/clear", ""),
        Arguments.of("POST", "/bzadmin/cache/clear", ""),
        Arguments.of("POST", "/bzadmin/cache/clear", CACHE),
        Arguments.of("POST", "/bzadmin/cache/entries/remove", CACHE + "&key=x"),
        Arguments.of("POST", "/bzadmin/logs/recent/threshold", "level=WARN"),
        Arguments.of("POST", "/bzadmin/application/statistics/reset", ""),
        Arguments.of("POST", "/bzadmin/cache/statistics", CACHE + "&enable=true"),
        Arguments.of("POST", "/bzadmin/cache/statistics/reset", CACHE));
    Stream<Arguments> handlers = HANDLERS.stream().map(path -> path.split("\\?", 2))
        .map(path -> Arguments.of("GET", path[0], path.length > 1 ? path[1] : ""));
    return Stream.of(routes, others, handlers).flatMap(s -> s);
  }

  /**
   * The same admin page by URLs that a filter mapping or a servlet path could see differently, and
   * in the other formats of the Berlioz servlets (and none, and those of Berlioz's error handler,
   * {@code .error} and {@code .auto}): never the admin data.
   */
  private static final List<String> EQUIVALENT_URLS = List.of(
      "/bzadmin/config", "/bzadmin/config.error", "/bzadmin/config.auto", "/bzadmin/config.txt", "/bzadmin/config.src;x=y",
      "/bzadmin//config.src", "//bzadmin/config.src", "/bzadmin/./config.src", "/fixture/../bzadmin/config.src",
      "/%62zadmin/config.src", "/bzadmin%2Fconfig.src", "/bzadmin;x=y/config.src", "/BZADMIN/config.src",
      "/bzadmin/config.src/", "/bzadmin/config.SRC", "/bzadmin/config.json;x=y", "/bzadmin/config.html;x=y");

  /**
   * Request parameters and headers that assert access, which only the host's authentication can
   * grant: the names of the attributes of access, verdict and Berlioz control authorisation, the
   * user headers of proxies, the Berlioz control key.
   */
  private static final String ASSERTED_PARAMETERS = "org.pageseeder.bzadmin.access.AdminAccess=operator"
      + "&org.pageseeder.bzadmin.access.AdminVerdict=OPERATE&org.pageseeder.berlioz.control.authorized=true";

  private static final String[] ASSERTED_HEADERS = {"X-Remote-User", "operator", "X-Forwarded-User", "operator",
      "Remote-User", "operator", "X-Auth-Request-User", "operator"};

  private static final String[] CONTROL_KEY = {"Authorization", "Berlioz fixture-secret-control-key"};

  /**
   * No admin data: not the global configuration (its property {@code fixture.colour}), the Berlioz
   * options or the admin page.
   */
  private static void assertNoAdminData(String body, String label) {
    assertFalse(body.contains("fixture.colour") || body.contains("\"colour\""), label);
    assertFalse(body.contains("berlioz.errors.handle"), label);
    assertFalse(body.contains("fixture-secret"), label);
    assertFalse(body.contains("page-bzadmin"), label);
  }

  private static HttpResponse<String> send(BerliozHost host, String method, String route, String format, String query) throws Exception {
    return "GET".equals(method)
        ? host.get(route + format + (query.isEmpty() ? "" : "?" + query))
        : host.post(route + format, query);
  }

  /**
   * Denied by the generators: 403, no ETag, the content of every generator empty, the denial page
   * in HTML; for a handler, nothing in any format but JSON, which has an empty object.
   */
  private static void assertDeniedByGenerators(HttpResponse<String> response, String format, String label) throws Exception {
    assertEquals(403, response.statusCode(), label);
    assertTrue(response.headers().firstValue("ETag").isEmpty(), "No ETag: " + label);
    String body = response.body();
    assertFalse(body.contains("fixture-secret"), label);
    String path = response.request().uri().getPath();
    if (HANDLERS.stream().anyMatch(handler -> path.endsWith(handler.split("\\?", 2)[0] + format))) {
      assertEquals(".json".equals(format) ? "{}" : "", body.trim(), "No handler output: " + label);
      return;
    }
    switch (format) {
      case ".src":
      case ".xml":
        Document xml = parse(body);
        assertTrue(Integer.parseInt(xpath(xml, "count(/root/content)")) > 0, label);
        assertEquals("0", xpath(xml, "count(/root/content[@status != 'forbidden'])"), label);
        assertEquals("0", xpath(xml, "count(/root/content/node())"), "No generator output: " + label);
        break;
      case ".json":
        // Only empty objects, named after the generators
        assertTrue(body.replaceAll("\\s+", "").matches("\\{(\"[-a-z]+\":\\{\\},?)*\\}"), "No generator output: " + label + ": " + body);
        break;
      default:
        assertTrue(body.contains(DENIED), label);
        assertFalse(body.contains("page-bzadmin"), label);
        assertFalse(body.contains("<script"), label);
    }
  }

  /**
   * Denied by the filter: 403 with the denial message only, never stored.
   */
  private static void assertDeniedByFilter(HttpResponse<String> response, String label) {
    assertEquals(403, response.statusCode(), label);
    assertTrue(response.headers().firstValue("ETag").isEmpty(), "No ETag: " + label);
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""), label);
    assertTrue(response.body().contains(DENIED), label);
    assertFalse(response.body().contains("page-bzadmin"), label);
  }

  /**
   * Challenged for BASIC credentials (401), never stored: by the fallback login of the filter with
   * its message, or by the container, before the filter.
   *
   * <p>For a POST, the container's error page (Berlioz's {@code ErrorHandlerServlet}, whose error
   * service only allows GET and HEAD) replaces the container's response without its challenge.
   */
  private static void assertChallenged(HttpResponse<String> response, boolean byFilter, String label) {
    assertEquals(401, response.statusCode(), label);
    assertTrue(response.headers().firstValue("ETag").isEmpty(), "No ETag: " + label);
    assertFalse(response.body().contains("page-bzadmin"), label);
    assertFalse(response.body().contains("fixture-secret"), label);
    List<String> challenges = response.headers().allValues("WWW-Authenticate");
    if (byFilter) {
      assertEquals(List.of("Basic realm=\"Berlioz administration\", charset=\"UTF-8\""), challenges, label);
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""), label);
      assertTrue(response.body().contains(SIGN_IN), label);
    } else if ("GET".equals(response.request().method())) {
      // Jetty writes the scheme in lower case
      assertEquals(List.of("basic realm=\"berlioz administration\""), challenges.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toList()), label);
    }
  }

  /**
   * The host sets {@code AdminAccess}, but no filter processes the admin requests.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class WithoutFilter {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-no-filter.xml");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesEveryFormat(String method, String route, String query) throws Exception {
      for (String format : FORMATS) {
        assertDeniedByGenerators(send(this.host, method, route, format, query), format, method + " " + route + format);
      }
    }

    @Test
    void servesOtherServices() throws Exception {
      assertEquals(200, this.host.get("/fixture/global-config.src").statusCode());
    }

  }

  /**
   * The JAR included by a host that knows nothing of it: no authentication, no filter, no global
   * configuration. Requests that assert access are denied too.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class AccidentalInclusion {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-accidental.xml");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesEveryFormat(String method, String route, String query) throws Exception {
      for (String format : FORMATS) {
        String label = method + " " + route + format;
        assertDeniedByGenerators(send(this.host, method, route, format, query), format, label);
        String asserted = query.isEmpty() ? ASSERTED_PARAMETERS : query + "&" + ASSERTED_PARAMETERS;
        assertDeniedByGenerators(send(this.host, method, route, format, asserted), format, "Asserted: " + label);
        if ("GET".equals(method)) {
          HttpResponse<String> head = this.host.send("HEAD", route + format + (query.isEmpty() ? "" : "?" + query), null, ASSERTED_HEADERS);
          assertEquals(403, head.statusCode(), "HEAD " + label);
          assertEquals("", head.body(), "HEAD " + label);
        }
      }
    }

    @Test
    void exposesNoAdminDataAtEquivalentUrls() throws Exception {
      for (String url : EQUIVALENT_URLS) {
        BerliozHost.RawResponse response = this.host.raw("GET", url, null, "X-Remote-User: operator");
        // Berlioz's error handler answers .error and .auto at any URL, with no error here
        if (!url.matches(".*\\.(error|auto)")) assertTrue(response.status >= 400, url + ": " + response.status);
        assertNoAdminData(response.body, url);
      }
    }

    @Test
    void servesOtherServices() throws Exception {
      assertEquals(200, this.host.get("/fixture/global-config.src").statusCode());
    }

  }

  /**
   * The filter mapped to the admin services, but no authentication: no {@code AdminAccess}, no
   * user for the admin roles of the configuration, no fallback login. Requests that assert access,
   * and BASIC credentials, are denied with no challenge.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FilterWithoutAuthentication {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-filter-only.xml");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesEveryFormat(String method, String route, String query) throws Exception {
      String asserted = query.isEmpty() ? ASSERTED_PARAMETERS : query + "&" + ASSERTED_PARAMETERS;
      for (String format : FORMATS) {
        String label = method + " " + route + format;
        assertDeniedByFilter(send(this.host, method, route, format, query), label);
        assertDeniedByFilter(send(this.host, method, route, format, asserted), "Asserted: " + label);
        String target = route + format + ("GET".equals(method) && !query.isEmpty() ? "?" + query : "");
        String form = "GET".equals(method) ? null : query;
        for (String[] headers : new String[][]{ASSERTED_HEADERS, CONTROL_KEY, {"Authorization", HostExample.basic("operator")}}) {
          HttpResponse<String> response = this.host.send(method, target, form, headers);
          assertDeniedByFilter(response, headers[0] + ": " + label);
          assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty(), label);
        }
      }
    }

  }

  /**
   * The filter is mapped to URLs other than those of the admin services.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FilterOnOtherUrls {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-wrong-path.xml");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesEveryFormat(String method, String route, String query) throws Exception {
      for (String format : FORMATS) {
        assertDeniedByGenerators(send(this.host, method, route, format, query), format, method + " " + route + format);
      }
    }

  }

  /**
   * The filter is mapped before the host's authentication, so it never sees the grant.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FilterBeforeAuthentication {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start("/", "web-wrong-order.xml");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesEveryFormat(String method, String route, String query) throws Exception {
      for (String format : FORMATS) {
        assertDeniedByFilter(send(this.host, method, route, format, query), method + " " + route + format);
      }
    }

  }

  /**
   * The filter mapped to the admin services, after the host's own authentication filter, which
   * sets {@code AdminAccess}.
   */
  @Nested
  final class HostFilter extends FilterAfterAuthentication {

    HostFilter() {
      super(HostExample.FILTER);
    }

    /**
     * The host's {@code AdminAccess} is the only source of access when it is set: without
     * permission, it denies a user in the admin roles.
     */
    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesAccessWithoutPermission(String method, String route, String query) throws Exception {
      for (String format : FORMATS) {
        assertDeniedByFilter(send(host().as("revoked"), method, route, format, query), "revoked: " + method + " " + route + format);
      }
    }

    /**
     * BASIC credentials without fallback login: denied, with no challenge.
     */
    @Test
    void ignoresBasicCredentials() throws Exception {
      HttpResponse<String> response = host().as(null).get("/bzadmin/config.src", "Authorization", HostExample.basic("operator"));
      assertDeniedByFilter(response, "BASIC");
      assertTrue(response.headers().firstValue("WWW-Authenticate").isEmpty());
    }

  }

  /**
   * The filter mapped to the admin services, with container BASIC authentication over HTTPS and
   * the admin container roles.
   */
  @Nested
  final class ContainerLogin extends FilterAfterAuthentication {

    ContainerLogin() {
      super(HostExample.CONTAINER);
    }

  }

  /**
   * The filter mapped to the admin services, with its fallback login, behind a TLS-terminating
   * proxy.
   */
  @Nested
  final class FallbackLogin extends FilterAfterAuthentication {

    FallbackLogin() {
      super(HostExample.FALLBACK);
    }

  }

  /**
   * The filter mapped to the admin services, after the host's authentication: the same policy for
   * every host example (M1.3).
   */
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  abstract class FilterAfterAuthentication {

    private final HostExample example;

    private @Nullable BerliozHost host;

    FilterAfterAuthentication(HostExample example) {
      this.example = example;
    }

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(this.example);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    BerliozHost host() {
      return this.host;
    }

    /**
     * Anonymous users, authenticated users who are not administrators, and unknown users: denied by
     * the filter, or challenged by the container or the fallback login.
     */
    @ParameterizedTest
    @MethodSource("org.pageseeder.bzadmin.AccessPolicyTest#services")
    void deniesWithoutGrant(String method, String route, String query) throws Exception {
      for (String user : new String[]{null, "user", "unknown"}) {
        for (String format : FORMATS) {
          String label = user + ": " + method + " " + route + format;
          HttpResponse<String> response = send(this.host.as(user), method, route, format, query);
          int status = this.example.deniedStatus(user);
          if (status == 403) {
            assertDeniedByFilter(response, label);
          } else {
            assertChallenged(response, this.example.deniedByFilter(user), label);
          }
          if ("GET".equals(method)) {
            HttpResponse<String> head = this.host.as(user).send("HEAD", route + format + (query.isEmpty() ? "" : "?" + query), null);
            assertEquals(status, head.statusCode(), "HEAD " + label);
            assertEquals("", head.body(), "HEAD " + label);
          }
        }
      }
    }

    /**
     * The other methods: denied or challenged like the others, before the check of operations.
     */
    @ParameterizedTest
    @ValueSource(strings = {"PUT", "DELETE", "PATCH", "OPTIONS"})
    void deniesOtherMethodsWithoutGrant(String method) throws Exception {
      for (String user : new String[]{null, "user", "unknown"}) {
        for (String path : new String[]{"/bzadmin/config.src", "/bzadmin/cache/clear.src", "/bzadmin/config/reload.json"}) {
          String label = user + ": " + method + " " + path;
          HttpResponse<String> response = this.host.as(user).send(method, path, "", "X-Requested-With", "XMLHttpRequest");
          assertEquals(this.example.deniedStatus(user), response.statusCode(), label);
          assertNoAdminData(response.body(), label);
        }
      }
    }

    /**
     * The same admin page by equivalent URLs, other formats, or with parameters and headers that
     * assert access: denied (or not found), with no admin data.
     */
    @Test
    void deniesEquivalentUrlsWithoutGrant() throws Exception {
      for (String user : new String[]{null, "user"}) {
        BerliozHost other = this.host.as(user);
        for (String url : EQUIVALENT_URLS) {
          BerliozHost.RawResponse response = other.raw("GET", url, null);
          assertTrue(response.status >= 400, user + ": " + url + ": " + response.status);
          assertNoAdminData(response.body, user + ": " + url);
        }
        String[] headers = new String[ASSERTED_HEADERS.length / 2];
        for (int i = 0; i < headers.length; i++) headers[i] = ASSERTED_HEADERS[2 * i] + ": " + ASSERTED_HEADERS[2 * i + 1];
        for (String format : FORMATS) {
          String url = "/bzadmin/config" + format + "?" + ASSERTED_PARAMETERS;
          BerliozHost.RawResponse response = other.raw("GET", url, null, headers);
          assertEquals(this.example.deniedStatus(user), response.status, user + ": " + url);
          assertNoAdminData(response.body, user + ": " + url);
        }
      }
      // The control key is not an admin grant
      if (this.example.sendsControlKey()) {
        HttpResponse<String> response = this.host.as(null).get("/bzadmin/config.src", CONTROL_KEY);
        assertDeniedByFilter(response, "Control key");
      }
    }

    /**
     * A host page that includes an admin service: the filter does not process includes, so the
     * generators deny them, even to administrators.
     */
    @Test
    void deniesIncludes() throws Exception {
      for (String user : new String[]{null, "user", this.example.operators().get(0)}) {
        HttpResponse<String> response = this.host.as(user).get("/fixture-include/bzadmin/config");
        String label = user + ": include";
        assertNoAdminData(response.body(), label);
        Document xml = parse(response.body());
        assertEquals("bzadmin-config", xpath(xml, "/root/@service"), label);
        assertEquals("0", xpath(xml, "count(/root/content[@status != 'forbidden'])"), label);
        assertEquals("0", xpath(xml, "count(/root/content/node())"), "No generator output: " + label);
      }
    }

    /**
     * An error page that is an admin page (error code 418 of the test host): the filter processes
     * error dispatches, so users without a grant get no admin data.
     */
    @Test
    void deniesErrorDispatchesWithoutGrant() throws Exception {
      for (String user : new String[]{null, "user", "unknown"}) {
        HttpResponse<String> response = this.host.as(user).get("/fixture-error/418");
        assertTrue(response.statusCode() == 401 || response.statusCode() == 403, user + ": " + response.statusCode());
        assertNoAdminData(response.body(), user + ": error page");
      }
    }

    @Test
    void allowsAdministratorsToView() throws Exception {
      List<String> users = new ArrayList<>(this.example.viewers());
      users.addAll(this.example.operators());
      for (String user : users) {
        BerliozHost admin = this.host.as(user);
        for (Arguments route : AdminRoutes.routes().toArray(Arguments[]::new)) {
          String path = (String) route.get()[0];
          int status = (Integer) route.get()[1];
          assertEquals(status, admin.get(path + ".src").statusCode(), user + ": " + path + ".src");
          HttpResponse<String> html = admin.get(path + ".html");
          assertEquals(status, html.statusCode(), user + ": " + path + ".html");
          assertFalse(html.body().contains(DENIED), user + ": " + path + ".html");
        }
      }
    }

    @Test
    void deniesOperationsToViewers() throws Exception {
      assumeFalse(this.example.viewers().isEmpty(), "No viewer: the fallback login grants the permission to operate");
      String[][] operations = {
          {"POST", "/bzadmin/cache/clear", ""},
          {"POST", "/bzadmin/cache/clear", CACHE},
          {"POST", "/bzadmin/cache/entries/remove", CACHE + "&key=x"},
          {"POST", "/bzadmin/logs/recent/threshold", "level=WARN"},
          {"POST", "/bzadmin/application/statistics/reset", ""},
          {"POST", "/bzadmin/cache/statistics", CACHE + "&enable=true"},
          {"POST", "/bzadmin/cache/statistics/reset", CACHE},
          {"POST", "/bzadmin/config/reload", "berlioz-reload=true"},
          {"POST", "/bzadmin/services/reload", "berlioz-reload=services"},
          {"POST", "/bzadmin/xslt/cache/clear", "berlioz-reload=xslt"}};
      for (String user : this.example.viewers()) {
        BerliozHost viewer = this.host.as(user);
        for (String[] operation : operations) {
          for (String format : FORMATS) {
            assertDeniedByFilter(send(viewer, operation[0], operation[1], format, operation[2]), user + ": " + operation[1] + format);
          }
        }
      }
    }

    @Test
    void allowsOperationsToOperators() throws Exception {
      for (String user : this.example.operators()) {
        HttpResponse<String> response = this.host.as(user).post("/bzadmin/cache/clear.src", "");
        assertEquals(200, response.statusCode(), user + ": " + response.body());
        assertEquals("ok", xpath(parse(response.body()), "/root/content[@name='cache-clear']/@status"), user);
      }
    }

    /**
     * A cacheable admin service has an ETag when allowed: denied ones have none.
     */
    @Test
    void hasETagWhenAllowed() throws Exception {
      assertTrue(this.host.get("/bzadmin/service/match.src?url=/bzadmin/config.html").headers().firstValue("ETag").isPresent());
    }

    /**
     * The pages show the class of the generators of Berlioz, not of the guards.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/bzadmin/services.html", "/bzadmin/application.html?view=statistics"})
    void showsTheDelegateClasses(String page) throws Exception {
      // The statistics list the generators of earlier requests
      this.host.get(page);
      String html = this.host.get(page).body();
      assertFalse(html.contains("bzadmin.guard"), page);
      assertTrue(html.contains("<span class=\"generator-origin\">[berlioz]</span>.ListLibraries"), page);
      assertTrue(html.contains("<span class=\"generator-origin\">[bzadmin]</span>.GetApplicationSummary"), page);
    }

    @Test
    void servesOtherServicesToAnyone() throws Exception {
      assertEquals(200, this.host.as(null).get("/fixture/global-config.src").statusCode());
    }

  }

}
