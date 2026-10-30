package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.jspecify.annotations.Nullable;

/**
 * What differs between the host examples (M1.3), beyond the policy tests that they share
 * ({@link AccessPolicyTest}, {@link OperationDispatchTest}): the credentials, plain HTTP, the
 * proxy and context path, and the audit of operations.
 */
final class HostIntegrationTest {

  private static final String DENIED = "Access to Berlioz administration is not configured for this request.";

  private static final String CHALLENGE = "Basic realm=\"Berlioz administration\", charset=\"UTF-8\"";

  private static String basic(String user, String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @return the log of the test hosts, where the audit is recorded.
   */
  private static String log() throws Exception {
    return Files.readString(Paths.get(System.getProperty("bzadmin.test.logs"), "fixture.log"));
  }

  /**
   * The fallback login, behind a TLS-terminating proxy.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FallbackLogin {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(HostExample.FALLBACK);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @Test
    void challengesOtherCredentials() throws Exception {
      String[] credentials = {
          basic("operator", "fixture-secret-password-viewer"),
          basic("viewer", "fixture-secret-password-operator"),
          basic("Operator", "fixture-secret-password-operator"),
          basic("operator", ""),
          basic("", ""),
          "Basic " + Base64.getEncoder().encodeToString("operator".getBytes(StandardCharsets.UTF_8)),
          "Basic not-base64!",
          "Bearer fixture-secret-password-operator",
          "Basic"};
      for (String authorization : credentials) {
        HttpResponse<String> response = this.host.as(null).get("/bzadmin/config.src", "Authorization", authorization);
        assertEquals(401, response.statusCode(), authorization);
        assertEquals(List.of(CHALLENGE), response.headers().allValues("WWW-Authenticate"), authorization);
      }
      assertEquals(200, this.host.as(null).get("/bzadmin/config.src", "Authorization", basic("operator", "fixture-secret-password-operator")).statusCode());
      // The scheme is case-insensitive
      String lowerCase = "basic " + basic("operator", "fixture-secret-password-operator").substring(6);
      assertEquals(200, this.host.as(null).get("/bzadmin/config.src", "Authorization", lowerCase).statusCode());
    }

    /**
     * Over plain HTTP from this machine, not through a proxy, the login works as over HTTPS.
     */
    @Test
    void allowsPlainHttpOnLoopback() throws Exception {
      BerliozHost local = this.host.direct();
      assertEquals(200, local.get("/bzadmin/config.src").statusCode());
      assertEquals(200, local.post("/bzadmin/cache/clear.src", "").statusCode());
      assertEquals(200, local.postRaw("/bzadmin/cache/clear.src", "", "X-Requested-With: XMLHttpRequest", "Host: localhost"));
      assertEquals(200, local.postRaw("/bzadmin/cache/clear.src", "", "X-Requested-With: XMLHttpRequest", "Host: [::1]"));
      HttpResponse<String> anonymous = local.as(null).get("/bzadmin/config.src");
      assertEquals(401, anonymous.statusCode());
      assertEquals(List.of(CHALLENGE), anonymous.headers().allValues("WWW-Authenticate"));
    }

    /**
     * Over plain HTTP through a proxy whose headers the container does not apply, or for a host
     * name other than {@code localhost}: the login is refused, even with the right credentials,
     * and never offered.
     */
    @Test
    void refusesPlainHttpFromElsewhere() throws Exception {
      String[][] proxied = {
          {"X-Forwarded-For", "203.0.113.5"}, {"X-Forwarded-For", "127.0.0.1"}, {"Forwarded", "for=203.0.113.5"},
          {"X-Real-IP", "203.0.113.5"}, {"X-Forwarded-Proto", "http"}};
      for (String user : new String[]{"operator", null}) {
        for (String[] header : proxied) {
          for (String format : new String[]{".src", ".html"}) {
            HttpResponse<String> response = this.host.direct().as(user).get("/bzadmin/config" + format, header);
            String label = user + " " + String.join(": ", header) + " " + format;
            assertEquals(403, response.statusCode(), label);
            assertTrue(response.headers().allValues("WWW-Authenticate").isEmpty(), label);
            assertTrue(response.body().contains(DENIED), label);
          }
        }
        for (String name : new String[]{"admin.example.org", "127.0.0.1.nip.io", "localhost.example.org", "10.0.0.1"}) {
          assertEquals(403, this.host.direct().as(user).postRaw("/bzadmin/cache/clear.src", "", "X-Requested-With: XMLHttpRequest",
              "Host: " + name), user + " " + name);
        }
      }
      assertTrue(log().contains("The fallback login of Berlioz administration is refused over plain HTTP, except from this machine"));
    }

    /**
     * The origin of the admin pages is the proxy's: the address of the container is another one.
     */
    @Test
    void checksTheOriginOfTheProxy() throws Exception {
      String path = "/bzadmin/cache/clear.src";
      String unknown = "provider=ehcache2&manager=__DEFAULT__&cache=no-such-cache";
      assertEquals(404, this.host.postRaw(path, unknown, "X-Requested-With: XMLHttpRequest", "Origin: " + HostExample.PROXY_ORIGIN));
      String backend = this.host.direct().origin();
      assertEquals(403, this.host.postRaw(path, unknown, "X-Requested-With: XMLHttpRequest", "Origin: " + backend));
    }

    /**
     * Failed logins are recorded, without the user name that was typed (it may be a password).
     */
    @Test
    void auditsFailedLogins() throws Exception {
      String typed = "fixture-secret-typed-in-the-user-field";
      assertEquals(401, this.host.as(null).get("/bzadmin/logs.src", "Authorization", basic(typed, "x")).statusCode());
      String log = log();
      assertTrue(log.contains("org.pageseeder.bzadmin.audit - Login failed: GET /bzadmin/logs.src"), log);
      assertFalse(log.contains(typed));
      assertFalse(log.contains("fixture-secret-password"));
    }

  }

  /**
   * The password of the fallback login, changed in the configuration and reloaded: a plain password
   * (local development), or a value meant as a hash that is not a valid one.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FallbackPassword {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(HostExample.FALLBACK);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    /**
     * Writes the password in the configuration, and reloads it as the administrator.
     */
    private void configure(String password, String authorization) throws Exception {
      Path config = this.host.webInf().resolve("config/" + HostExample.FALLBACK.config());
      Files.writeString(config, Files.readString(config).replaceAll("password=\"[^\"]*\"", "password=\"" + password.replace("$", "\\$") + "\""));
      HttpResponse<String> reload = this.host.as(null).post("/bzadmin/config/reload.src", "berlioz-reload=true", "Authorization", authorization);
      assertEquals(200, reload.statusCode(), reload.body());
    }

    @Test
    void acceptsPlainOrHashedPasswords() throws Exception {
      String hashed = basic("operator", "fixture-secret-password-operator");
      String plain = basic("operator", "fixture-secret-plain-password");
      assertEquals(200, this.host.as(null).get("/bzadmin/config.src", "Authorization", hashed).statusCode());
      configure("fixture-secret-plain-password", hashed);
      assertEquals(200, this.host.as(null).get("/bzadmin/config.src", "Authorization", plain).statusCode());
      assertEquals(401, this.host.as(null).get("/bzadmin/config.src", "Authorization", hashed).statusCode());
      assertEquals(401, this.host.as(null).get("/bzadmin/config.src", "Authorization", basic("operator", "fixture-secret-plain-passwor")).statusCode());
      // Still only over HTTPS, or on loopback
      assertEquals(403, this.host.direct().as(null).get("/bzadmin/config.src", "Authorization", plain, "X-Forwarded-For", "203.0.113.5").statusCode());
      String page = this.host.as(null).get("/bzadmin/config.html", "Authorization", plain).body();
      assertFalse(page.contains("fixture-secret"), "The password is hidden");
      assertTrue(log().contains("The password of the fallback login of Berlioz administration (berlioz.admin.password) is not hashed"));

      // Meant as a hash: denied, even with the value as password
      String bcrypt = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
      configure(bcrypt, plain);
      for (String authorization : new String[]{plain, basic("operator", bcrypt), null}) {
        HttpResponse<String> response = authorization == null
            ? this.host.as(null).get("/bzadmin/config.src")
            : this.host.as(null).get("/bzadmin/config.src", "Authorization", authorization);
        assertEquals(403, response.statusCode(), authorization);
        assertTrue(response.headers().allValues("WWW-Authenticate").isEmpty(), authorization);
      }
      assertTrue(log().contains("(berlioz.admin.password) is not a valid PBKDF2 hash"));
    }

  }

  /**
   * The fallback login behind a proxy, in a non-root context.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class FallbackLoginInContext {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(HostExample.FALLBACK, "/host");
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @Test
    void servesTheAdministrators() throws Exception {
      HttpResponse<String> page = this.host.get("/bzadmin/config.html");
      assertEquals(200, page.statusCode());
      assertTrue(page.body().contains("/host/bzadmin/"), "Links in the context");
      assertEquals(401, this.host.as(null).get("/bzadmin/config.html").statusCode());
      assertEquals(403, this.host.direct().get("/bzadmin/config.html", "X-Forwarded-For", "203.0.113.5").statusCode());
      assertEquals(200, this.host.post("/bzadmin/cache/clear.src", "", "Sec-Fetch-Site", "same-origin").statusCode());
      assertEquals(200, this.host.postRaw("/bzadmin/cache/clear.src", "", "X-Requested-With: XMLHttpRequest", "Origin: " + HostExample.PROXY_ORIGIN));
      assertEquals(403, this.host.postRaw("/bzadmin/cache/clear.src", "", "X-Requested-With: XMLHttpRequest", "Origin: " + HostExample.PROXY_ORIGIN + "/host"));
    }

  }

  /**
   * Container BASIC authentication over HTTPS.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class ContainerLogin {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(HostExample.CONTAINER);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    /**
     * Over plain HTTP, the container redirects to HTTPS before authenticating.
     */
    @Test
    void redirectsPlainHttp() throws Exception {
      for (String user : new String[]{"operator", null}) {
        for (String path : new String[]{"/bzadmin/config.src", "/fixture-forward/config.src"}) {
          HttpResponse<String> response = this.host.plain().as(user).get(path);
          // Jetty 10: 303 See Other
          assertEquals(3, response.statusCode() / 100, user + " " + path);
          assertTrue(response.headers().firstValue("Location").orElse("").startsWith(this.host.origin() + path), user + " " + path);
          assertFalse(response.body().contains("page-bzadmin"), user + " " + path);
        }
        assertEquals(3, this.host.plain().as(user).post("/bzadmin/cache/clear.src", "").statusCode() / 100, user);
      }
    }

    @Test
    void challengesOtherCredentials() throws Exception {
      for (String authorization : new String[]{basic("operator", "fixture-secret-password-viewer"), basic("nobody", "x")}) {
        assertEquals(401, this.host.as(null).get("/bzadmin/config.src", "Authorization", authorization).statusCode(), authorization);
      }
    }

  }

  /**
   * The audit of operations, on the host whose filter sets {@code AdminAccess}: who, what, the
   * status; no parameter value.
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  final class Audit {

    private @Nullable BerliozHost host;

    @BeforeAll
    void start() throws Exception {
      this.host = BerliozHost.start(HostExample.FILTER);
    }

    @AfterAll
    void stop() throws Exception {
      if (this.host != null) this.host.close();
    }

    @Test
    void recordsOperations() throws Exception {
      String key = "fixture-secret-cache-key";
      assertEquals(200, this.host.post("/bzadmin/cache/entries/remove.json", AccessPolicyTest.CACHE + "&key=" + key).statusCode());
      assertEquals(403, this.host.as("viewer").post("/bzadmin/cache/clear.xml", "").statusCode());
      assertEquals(403, this.host.send("POST", "/bzadmin/logs/recent/threshold.src", "threshold=ERROR").statusCode());
      assertEquals(403, this.host.as(null).post("/bzadmin/application/statistics/reset.src", "").statusCode());
      assertEquals(400, this.host.get("/bzadmin/config.src?berlioz-reload=true").statusCode());
      assertEquals(404, this.host.post("/bzadmin/cache/clear.src?key=" + key, "provider=ehcache2&manager=__DEFAULT__&cache=no-such-cache").statusCode());
      assertEquals(200, this.host.get("/bzadmin/config.src").statusCode());
      String log = log();
      for (String entry : new String[]{
          "INFO  org.pageseeder.bzadmin.audit - POST /bzadmin/cache/entries/remove.json user=operator status=200",
          "WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/cache/clear.xml user=viewer status=403",
          "WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/logs/recent/threshold.src user=operator status=403",
          "WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/application/statistics/reset.src user=- status=403",
          "WARN  org.pageseeder.bzadmin.audit - GET /bzadmin/config.src user=operator status=400",
          "WARN  org.pageseeder.bzadmin.audit - POST /bzadmin/cache/clear.src user=operator status=404"}) {
        assertTrue(log.contains(entry), entry);
      }
      assertFalse(log.contains(key), "No parameter value");
      // Reads are not recorded
      assertFalse(log.contains("GET /bzadmin/config.src user=operator status=200"));
    }

  }

}
