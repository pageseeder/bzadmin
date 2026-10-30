package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * The dispatch of operations (M1.2): operations rejected by the CSRF check, and Berlioz control
 * parameters outside their admin service, have no side effect in any format, for HEAD and through
 * a forward; every response is {@code no-store} with the admin policy; no format exposes a secret.
 *
 * <p>Negative cases (M1.4): the generators of bzadmin in services outside the admin URLs, other
 * methods, reads with the parameters of operations, and an error page that is an operation.
 *
 * <p>The same cases for every host example (M1.3). Each in its own host, because it changes the
 * configuration: the configuration on disk has another colour and the services a broken file, so
 * that a reload would be visible.
 */
final class OperationDispatchTest {

  @Nested
  final class HostFilter extends Cases {

    HostFilter() {
      super(HostExample.FILTER);
    }

  }

  @Nested
  final class ContainerLogin extends Cases {

    ContainerLogin() {
      super(HostExample.CONTAINER);
    }

  }

  @Nested
  final class FallbackLogin extends Cases {

    FallbackLogin() {
      super(HostExample.FALLBACK);
    }

  }

  private static final String CROSS_SITE = "Berlioz administration operations are only accepted from its own pages.";

  private static final String CONTROL_REJECTED = "Berlioz control parameters are not accepted on this request.";

  private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; "
      + "base-uri 'none'; frame-ancestors 'none'; form-action 'self'";

  /** The control key in the configuration of the test host: it does not bypass the filter. */
  private static final String[] CONTROL = {"Authorization", "Berlioz fixture-secret-control-key"};

  private static final List<String> FORMATS = List.of(".src", ".xml", ".json", ".html");

  /** A cache of the host's second EHCache 2 manager (FixtureCacheManagers). */
  private static final String FIXTURE_CACHE = "provider=ehcache2&manager=fixture&cache=PSML";

  /** A JCache cache of the host, with statistics enabled. */
  private static final String JCACHE = "provider=jcache&manager=fixture-caffeine&cache=PSML";

  private static final String UNKNOWN_CACHE = "provider=ehcache2&manager=__DEFAULT__&cache=no-such-cache";

  /**
   * The cases, on the host of an example.
   */
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  abstract class Cases {

    private final HostExample example;

    private @Nullable BerliozHost host;

    /** The state that the operations would change. */
    private String initial;

    Cases(HostExample example) {
      this.example = example;
    }

    @BeforeAll
    void start() throws Exception {
      host = BerliozHost.start(this.example);
      // Berlioz loads the services on the first request
      assertEquals(200, host.get("/bzadmin/services.src").statusCode());
      Path config = host.webInf().resolve("config/" + this.example.config());
      Files.writeString(config, Files.readString(config).replace("colour=\"blue\"", "colour=\"green\""));
      Files.writeString(host.webInf().resolve("config/services!broken.xml"), "<services group=\"broken\"><service id=\"broken\"");
    }

    @AfterAll
    void stop() throws Exception {
      if (host != null) host.close();
    }

    /**
     * Caches a file and records generator statistics, which the operations would remove.
     */
    @BeforeEach
    void prepare() throws Exception {
      assertEquals(200, host.get("/css/fixture.css").statusCode());
      host.get("/bzadmin/application.src");
      initial = state();
      assertTrue(initial.contains("colour=blue"), initial);
      assertTrue(initial.contains("services=ready"), initial);
      assertTrue(initial.contains("cached=true"), initial);
      assertTrue(initial.contains("cache-statistics=true"), initial);
      assertTrue(initial.contains("statistics=1"), initial);
    }

    /**
     * Every operation, with the parameters that make it change the state.
     */
    Stream<Arguments> operations() {
      return Stream.of(
          Arguments.of("/bzadmin/cache/clear", ""),
          Arguments.of("/bzadmin/cache/clear", AccessPolicyTest.CACHE),
          Arguments.of("/bzadmin/cache/entries/remove", AccessPolicyTest.CACHE + "&key=GET_/css/fixture.css"),
          Arguments.of("/bzadmin/cache/statistics", JCACHE + "&enable=false"),
          Arguments.of("/bzadmin/cache/statistics/reset", JCACHE),
          Arguments.of("/bzadmin/logs/recent/threshold", "threshold=ERROR"),
          Arguments.of("/bzadmin/application/statistics/reset", ""),
          Arguments.of("/bzadmin/config/reload", "berlioz-reload=true"),
          Arguments.of("/bzadmin/services/reload", "berlioz-reload=services"),
          Arguments.of("/bzadmin/xslt/cache/clear", "berlioz-reload=xslt"));
    }

    /**
     * Requests that cross-site pages can send, or that do not come from the admin pages.
     */
    @ParameterizedTest
    @MethodSource("operations")
    void rejectsOperationsWithoutTheHeader(String path, String form) throws Exception {
      List<String[]> requests = List.of(
          new String[0],
          new String[]{"X-Requested-With", "fetch"},
          new String[]{"X-Requested-With", "XMLHttpRequest", "Sec-Fetch-Site", "cross-site"},
          new String[]{"X-Requested-With", "XMLHttpRequest", "Sec-Fetch-Site", "same-site"},
          new String[]{"X-Requested-With", "XMLHttpRequest", "Sec-Fetch-Site", "none"},
          new String[]{"Sec-Fetch-Site", "same-origin"});
      for (String format : FORMATS) {
        for (String[] headers : requests) {
          HttpResponse<String> response = host.send("POST", path + format, form, headers);
          String label = path + format + " " + String.join(" ", headers);
          assertEquals(403, response.statusCode(), label);
          assertTrue(response.body().contains(CROSS_SITE), label);
          assertSecured(response, label);
        }
        assertEquals(state(), initial, path + format);
      }
    }

    @ParameterizedTest
    @MethodSource("operations")
    void rejectsOperationsFromOtherOrigins(String path, String form) throws Exception {
      String origin = host.origin();
      String otherScheme = origin.startsWith("https:") ? "http" + origin.substring(5) : "https" + origin.substring(4);
      String otherPort = origin.matches(".*:[0-9]+") ? origin.substring(0, origin.lastIndexOf(':')) : origin + ":8443";
      for (String other : new String[]{"http://attacker.example", "null", otherScheme, otherPort}) {
        for (String format : FORMATS) {
          int status = host.postRaw(path + format, form, "X-Requested-With: XMLHttpRequest", "Origin: " + other);
          assertEquals(403, status, other + " " + path + format);
        }
      }
      assertEquals(state(), initial, path);
    }

    /**
     * Operations from the admin pages reach the generators: an unknown cache is not found.
     */
    @Test
    void acceptsOperationsFromTheSameOrigin() throws Exception {
      String path = "/bzadmin/cache/clear.src";
      assertEquals(404, host.send("POST", path, UNKNOWN_CACHE, "X-Requested-With", "XMLHttpRequest").statusCode());
      assertEquals(404, host.send("POST", path, UNKNOWN_CACHE, "X-Requested-With", "XMLHttpRequest", "Sec-Fetch-Site", "same-origin").statusCode());
      assertEquals(404, host.postRaw(path, UNKNOWN_CACHE, "X-Requested-With: XMLHttpRequest", "Origin: " + host.origin()));
      assertEquals(404, host.postRaw(path, UNKNOWN_CACHE, "X-Requested-With: XMLHttpRequest", "Sec-Fetch-Site: same-origin",
          "Origin: " + host.origin()));
    }

    /**
     * Control parameters are rejected on every other request, even with the control key.
     */
    @ParameterizedTest
    @ValueSource(strings = {"berlioz-reload", "reload-services", "clear-xsl-cache", "reset-etags", "berlioz-profile"})
    void rejectsControlParametersOnReads(String parameter) throws Exception {
      for (String route : new String[]{"/bzadmin/config", "/bzadmin/services", "/bzadmin/xslt", "/bzadmin/cache"}) {
        for (String format : FORMATS) {
          for (String value : new String[]{"true", ""}) {
            String path = route + format + "?" + parameter + "=" + value;
            HttpResponse<String> response = host.get(path, control());
            assertEquals(400, response.statusCode(), path);
            assertTrue(response.body().contains(CONTROL_REJECTED), path);
            assertSecured(response, path);
            assertEquals(400, host.send("HEAD", path, null, control()).statusCode(), "HEAD " + path);
          }
        }
      }
      assertEquals(state(), initial, parameter);
    }

    @ParameterizedTest
    @MethodSource("misplacedControls")
    void rejectsControlParametersOnOtherOperations(String path, String form) throws Exception {
      for (String format : FORMATS) {
        int query = path.indexOf('?');
        String target = query < 0 ? path + format : path.substring(0, query) + format + path.substring(query);
        HttpResponse<String> response = host.post(target, form, control());
        assertEquals(400, response.statusCode(), target + " " + form);
        assertTrue(response.body().contains(CONTROL_REJECTED), target);
      }
      assertEquals(state(), initial, path + " " + form);
    }

    Stream<Arguments> misplacedControls() {
      return Stream.of(
          Arguments.of("/bzadmin/cache/clear", "berlioz-reload=true"),
          Arguments.of("/bzadmin/application/statistics/reset", "berlioz-reload=services"),
          // Other scopes than those of the service
          Arguments.of("/bzadmin/services/reload", "berlioz-reload=true"),
          Arguments.of("/bzadmin/services/reload", "berlioz-reload=services,xslt"),
          Arguments.of("/bzadmin/xslt/cache/clear", "berlioz-reload=services"),
          Arguments.of("/bzadmin/config/reload", "berlioz-reload=config"),
          Arguments.of("/bzadmin/xslt/cache/clear", "berlioz-reload=xslt,unknown"),
          Arguments.of("/bzadmin/xslt/cache/clear", "berlioz-reload="),
          // The deprecated aliases, even on the service of their scope
          Arguments.of("/bzadmin/services/reload", "reload-services=true"),
          Arguments.of("/bzadmin/xslt/cache/clear", "clear-xsl-cache=true"),
          Arguments.of("/bzadmin/config/reload", "reset-etags=true"),
          // Other control parameters, or more than one value
          Arguments.of("/bzadmin/config/reload", "berlioz-reload=true&berlioz-profile=true"),
          Arguments.of("/bzadmin/services/reload", "berlioz-reload=services&reset-etags=true"),
          Arguments.of("/bzadmin/services/reload?berlioz-reload=services", "berlioz-reload=services"),
          Arguments.of("/bzadmin/services/reload", "berlioz-reload=services&berlioz-reload=services"),
          Arguments.of("/bzadmin/no-such-service", "berlioz-reload=xslt"));
    }

    /**
     * A host servlet that forwards to the admin services: the filter applies to the forward.
     */
    @Test
    void appliesThePolicyToForwards() throws Exception {
      assertEquals(200, host.get("/fixture-forward/config.src").statusCode());
      assertEquals(this.example.deniedStatus(null), host.as(null).get("/fixture-forward/config.src").statusCode());
      for (String viewer : this.example.viewers()) {
        assertEquals(403, host.as(viewer).post("/fixture-forward/cache/clear.src", "").statusCode(), viewer);
      }
      HttpResponse<String> crossSite = host.send("POST", "/fixture-forward/cache/clear.src", "");
      assertEquals(403, crossSite.statusCode());
      assertTrue(crossSite.body().contains(CROSS_SITE));
      assertEquals(400, host.get("/fixture-forward/config.src?berlioz-reload=true", control()).statusCode());
      assertEquals(400, host.post("/fixture-forward/cache/clear.src", "berlioz-reload=true", control()).statusCode());
      assertEquals(state(), initial);
    }

    /**
     * An error page that is an admin operation (error code 451 of the test host): the filter applies
     * the policy to the error dispatch, which keeps the method of the request.
     */
    @Test
    void appliesThePolicyToErrorDispatches() throws Exception {
      // A cross-site form posted to a host URL that fails
      assertEquals(403, host.send("POST", "/fixture-error/451", "").statusCode());
      assertEquals(403, host.postRaw("/fixture-error/451", "", "X-Requested-With: XMLHttpRequest", "Origin: http://attacker.example"));
      assertTrue(host.as(null).post("/fixture-error/451", "").statusCode() >= 400);
      for (String viewer : this.example.viewers()) {
        assertEquals(403, host.as(viewer).post("/fixture-error/451", "").statusCode(), viewer);
      }
      // A read cannot run an operation
      assertTrue(host.get("/fixture-error/451").statusCode() >= 400);
      // The container does not give the parameters of the request to the error page: no control
      // parameter is applied (Jetty 10)
      for (String user : new String[]{null, "user", this.example.operators().get(0)}) {
        BerliozHost other = host.as(user);
        other.post("/fixture-error/451?berlioz-reload=true", "berlioz-reload=true", "Sec-Fetch-Site", "cross-site");
        other.get("/fixture-error/418?berlioz-reload=true&reload-services=true", control());
      }
      assertEquals(state(), initial);
    }

    /**
     * The generators of bzadmin in services outside the admin URLs, where the filter does not run:
     * denied to administrators, from the admin pages, with no output and no side effect.
     */
    @Test
    void deniesDirectInvocation() throws Exception {
      for (String format : FORMATS) {
        String label = "/fixture/admin/read" + format;
        HttpResponse<String> read = host.get(label);
        assertEquals(403, read.statusCode(), label);
        assertFalse(read.body().contains("colour"), label);
        assertFalse(read.body().contains("fixture-secret"), label);
        if (format.equals(".src") || format.equals(".xml")) {
          Document xml = parse(read.body());
          assertEquals("12", xpath(xml, "count(/root/content[@status = 'forbidden'])"), label);
          assertEquals("0", xpath(xml, "count(/root/content/node())"), "No generator output: " + label);
        }
        label = "/fixture/admin/operate" + format;
        HttpResponse<String> operation = host.post(label, "threshold=ERROR");
        assertEquals(403, operation.statusCode(), label);
      }
      for (String format : new String[]{".xml", ".json"}) {
        HttpResponse<String> direct = host.get("/fixture/admin/direct" + format);
        assertEquals(403, direct.statusCode(), format);
        assertFalse(direct.body().contains("colour"), format);
      }
      assertEquals(state(), initial);
    }

    /**
     * Operations by other methods, and reads with the parameters of operations: no side effect,
     * even for administrators from the admin pages. The services of operations are POST only, and
     * reads pin the parameters of operations.
     */
    @Test
    void changesNothingByOtherMethods() throws Exception {
      for (Arguments arguments : operations().toArray(Arguments[]::new)) {
        String path = (String) arguments.get()[0];
        String form = (String) arguments.get()[1];
        for (String format : FORMATS) {
          for (String method : new String[]{"PUT", "DELETE", "PATCH"}) {
            HttpResponse<String> response = host.send(method, path + format, form, "X-Requested-With", "XMLHttpRequest");
            assertTrue(response.statusCode() >= 400, method + " " + path + format + ": " + response.statusCode());
          }
          // Without the control parameter, which reads reject
          String query = form.startsWith("berlioz-") || form.startsWith("reload-") || form.startsWith("clear-") ? "" : "?" + form;
          assertEquals(404, host.get(path + format + query).statusCode(), "GET " + path + format);
          assertEquals(404, host.send("HEAD", path + format + query, null).statusCode(), "HEAD " + path + format);
        }
      }
      for (String format : FORMATS) {
        for (String path : new String[]{"/bzadmin/application", "/bzadmin/application/statistics"}) {
          assertEquals(200, host.get(path + format + "?reset=true").statusCode(), path + format);
        }
        assertEquals(200, host.get("/bzadmin/cache/entries" + format + "?" + FIXTURE_CACHE + "&key=fixture-key&enable=false").statusCode());
        assertEquals(200, host.get("/bzadmin/cache/info" + format + "?" + JCACHE + "&key=fixture-key&enable=false").statusCode());
        assertEquals(200, host.get("/bzadmin/logs" + format + "?threshold=ERROR&level=ERROR").statusCode());
      }
      assertEquals(state(), initial);
    }

    /**
     * HEAD requests are reads: allowed to administrators, denied to others, never stored.
     */
    @Test
    void handlesHeadRequests() throws Exception {
      for (String format : FORMATS) {
        HttpResponse<String> allowed = host.send("HEAD", "/bzadmin/config" + format, null);
        assertEquals(200, allowed.statusCode(), format);
        assertSecured(allowed, format);
        HttpResponse<String> denied = host.as(null).send("HEAD", "/bzadmin/config" + format, null);
        assertEquals(this.example.deniedStatus(null), denied.statusCode(), format);
        if (this.example.deniedByFilter(null)) assertSecured(denied, format);
        assertEquals("", denied.body(), format);
      }
    }

    /**
     * Every response is {@code no-store} and has the admin policy: allowed reads (including a service
     * with its own cache control and an ETag), operations, denials and rejections.
     *
     * <p>Errors that Berlioz sends to the error page of the container ({@code berlioz.errors.handle}
     * is false in the test host) have the headers of that page, outside the admin URLs: the container
     * resets the response for it. Here, Berlioz's {@code ErrorHandlerServlet} ({@code no-store}) or
     * the {@code error} service of the host ({@code no-cache}).
     */
    @Test
    void neverStoresResponses() throws Exception {
      List<HttpResponse<String>> responses = new ArrayList<>();
      List<HttpResponse<String>> errors = new ArrayList<>();
      for (String format : FORMATS) {
        responses.add(host.get("/bzadmin/config" + format));
        responses.add(host.get("/bzadmin/files" + format));
        responses.add(host.get("/bzadmin/xslt/doc" + format + "?path=xslt/errors/missing-import.xsl"));
        // Anonymous requests to the container example are challenged by the container
        if (this.example.deniedByFilter(null)) responses.add(host.as(null).get("/bzadmin/config" + format));
        for (String viewer : this.example.viewers()) responses.add(host.as(viewer).post("/bzadmin/cache/clear" + format, ""));
        responses.add(host.post("/bzadmin/cache/clear" + format, ""));
        errors.add(host.get("/bzadmin/logs/raw" + format));
        errors.add(host.get("/bzadmin/logs/raw" + format + "?id=" + LogIds.fixture("missing.log")));
        errors.add(host.get("/bzadmin/no-such-page" + format));
        errors.add(host.post("/bzadmin/cache/clear" + format, UNKNOWN_CACHE));
      }
      // A service with its own cache control and an ETag (an AJAX lookup, without HTML page)
      responses.add(host.get("/bzadmin/service/match.src?url=/bzadmin/config.html"));
      for (HttpResponse<String> response : responses) {
        assertSecured(response, response.request().method() + " " + response.uri() + " " + response.statusCode());
      }
      for (HttpResponse<String> response : errors) {
        assertTrue(response.statusCode() >= 400, response.uri().toString());
        String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
        assertTrue(cacheControl.equals("no-store") || cacheControl.equals("no-cache"), response.uri() + ": " + cacheControl);
      }
      assertTrue(host.get("/bzadmin/service/match.src?url=/bzadmin/config.html").headers().firstValue("ETag").isPresent(), "ETag kept");
      HttpResponse<String> html = host.get("/bzadmin/config.html");
      assertEquals(2, html.headers().allValues("Content-Security-Policy").size(), "The policy of the host and the admin policy");
    }

    /**
     * No format exposes a secret of the test host, including errors and with the control key in the
     * request (or the BASIC credentials, which use the same header): the properties, the password
     * hash, the credentials.
     */
    @Test
    void exposesNoSecret() throws Exception {
      List<String> paths = new ArrayList<>();
      AdminRoutes.routes().forEach(route -> paths.add((String) route.get()[0]));
      paths.addAll(AccessPolicyTest.HANDLERS);
      paths.addAll(List.of("/bzadmin/logs/raw?id=" + LogIds.fixture("fixture.log"), "/bzadmin/logs/raw?id=" + LogIds.fixture("missing.log"),
          "/bzadmin/xslt/doc?path=xslt/errors/missing-import.xsl", "/bzadmin/cache/entries?" + AccessPolicyTest.CACHE,
          "/bzadmin/service/match?url=/bzadmin/config.html", "/bzadmin/no-such-page", "/bzadmin/config?view=deployment"));
      for (String path : paths) {
        for (String format : FORMATS) {
          int query = path.indexOf('?');
          String target = query < 0 ? path + format : path.substring(0, query) + format + path.substring(query);
          assertNoSecret(host.get(target, control()).body(), target);
          assertNoSecret(host.get(target + (query < 0 ? "?" : "&") + "berlioz-reload=true", control()).body(), target);
        }
      }
      for (String format : FORMATS) {
        assertNoSecret(host.post("/bzadmin/config/reload" + format, "", control()).body(), format);
      }
    }

    private void assertNoSecret(String body, String label) {
      assertFalse(body.contains("fixture-secret"), label);
      assertFalse(body.contains("$pbkdf2-sha256$"), label);
      assertFalse(body.contains(HostExample.basic("operator").substring(6)), label);
    }

    /**
     * @return the header with the control key, which BASIC credentials leave no room for.
     */
    private String[] control() {
      return this.example.sendsControlKey() ? CONTROL : new String[0];
    }

    /**
     * Not stored, with the admin policy.
     */
    private void assertSecured(HttpResponse<String> response, String label) {
      assertEquals(List.of("no-store"), response.headers().allValues("Cache-Control"), label);
      assertTrue(response.headers().allValues("Content-Security-Policy").contains(CSP), label);
    }

    /**
     * @return the state that the operations change, as a string.
     */
    private String state() throws Exception {
      Document config = parse(host.get("/bzadmin/config.src").body());
      Document services = parse(host.get("/bzadmin/services.src").body());
      Document elements = parse(host.get("/bzadmin/cache/entries.src?" + AccessPolicyTest.CACHE + "&limit=1000").body());
      Document cache = parse(host.get("/bzadmin/cache.src").body());
      Document logs = parse(host.get("/bzadmin/logs.src").body());
      Document statistics = parse(host.get("/bzadmin/application/statistics.src").body());
      return "colour=" + xpath(config, "//properties/property[@name='fixture.colour']/@value")
          + " services=" + xpath(services, "//live-services/@load-status")
          + " cached=" + xpath(elements, "boolean(//entries/entry[@key = 'GET_/css/fixture.css'])")
          + " cache-statistics=" + xpath(cache, "//cache-manager[@id = 'fixture-caffeine']/cache[@name = 'PSML']/statistics/@enabled")
          + " cache-puts=" + xpath(cache, "//cache-manager[@id = 'fixture-caffeine']/cache[@name = 'PSML']/statistics/metric[@name = 'puts']/@value")
          + " threshold=" + xpath(logs, "//recent-logs/@threshold")
          + " statistics=" + xpath(statistics, "count(//statistic[@generator='org.pageseeder.bzadmin.guard.berlioz.system.ListLibraries'])");
    }

  }

}
