package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class FallbackLoginTest {

  private static final String CLIENT = "203.0.113.5";

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usernameChangeRejectsPreviouslyVerifiedCredentials(boolean hashed) {
    FallbackLogin login = new FallbackLogin();
    String password = configured("fixture-password", hashed);
    HttpServletRequest old = credentials("alice", "fixture-password");
    assertAccess(login.authenticate(old, "alice", password), "alice");
    assertAccess(login.authenticate(old, "alice", password), "alice");

    assertNull(login.authenticate(old, "bob", password).access);
    HttpServletRequest current = credentials("bob", "fixture-password");
    assertAccess(login.authenticate(current, "bob", password), "bob");
    assertAccess(login.authenticate(current, "bob", password), "bob");
    assertNull(login.authenticate(old, "bob", password).access);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void passwordChangeRejectsPreviouslyVerifiedCredentials(boolean hashed) {
    FallbackLogin login = new FallbackLogin();
    HttpServletRequest old = credentials("alice", "fixture-old-password");
    assertAccess(login.authenticate(old, "alice", configured("fixture-old-password", hashed)), "alice");

    String password = configured("fixture-new-password", hashed);
    assertNull(login.authenticate(old, "alice", password).access);
    HttpServletRequest current = credentials("alice", "fixture-new-password");
    assertAccess(login.authenticate(current, "alice", password), "alice");
    assertAccess(login.authenticate(current, "alice", password), "alice");
    assertNull(login.authenticate(old, "alice", password).access);
  }

  @Test
  void reportsWhetherTheRequestHasCredentials() {
    FallbackLogin login = new FallbackLogin();
    assertSame(FallbackLogin.Outcome.NONE, login.authenticate(request(null, CLIENT), "alice", "fixture-password"));
    assertSame(FallbackLogin.Outcome.NONE, login.authenticate(request("Bearer x", CLIENT), "alice", "fixture-password"));
    String noColon = "Basic " + Base64.getEncoder().encodeToString("alice".getBytes(StandardCharsets.UTF_8));
    assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(request(noColon, CLIENT), "alice", "fixture-password"));
    assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(credentials("alice", "wrong"), "alice", "fixture-password"));
  }

  /**
   * Beyond the limit of a client, credentials are not checked, even the right ones; other clients
   * are not affected, and credentials already verified are accepted from any client.
   */
  @Test
  void throttlesFailedChecksByClient() {
    AtomicLong now = new AtomicLong();
    FallbackLogin login = new FallbackLogin(new LoginThrottle(now::get, 1));
    String password = "fixture-password";
    for (int i = 0; i < LoginThrottle.BURST; i++) {
      assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(credentials("alice", "wrong-" + i), "alice", password));
    }
    FallbackLogin.Outcome throttled = login.authenticate(credentials("alice", password), "alice", password);
    assertNull(throttled.access);
    assertTrue(throttled.credentials);
    assertEquals(LoginThrottle.INTERVAL_SECONDS, throttled.retryAfter);

    assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(credentials("alice", "wrong", "198.51.100.8"), "alice", password));
    assertAccess(login.authenticate(credentials("alice", password, "198.51.100.8"), "alice", password), "alice");
    // Verified: no check
    assertAccess(login.authenticate(credentials("alice", password), "alice", password), "alice");
    assertEquals(LoginThrottle.INTERVAL_SECONDS, login.authenticate(credentials("alice", "wrong"), "alice", password).retryAfter);

    now.addAndGet(TimeUnit.SECONDS.toNanos(LoginThrottle.INTERVAL_SECONDS));
    assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(credentials("alice", "wrong"), "alice", password));
  }

  @Test
  void resetsTheClientAfterASuccessfulCheck() {
    AtomicLong now = new AtomicLong();
    FallbackLogin login = new FallbackLogin(new LoginThrottle(now::get, 1));
    for (int round = 0; round < 3; round++) {
      for (int i = 0; i < LoginThrottle.BURST - 1; i++) {
        assertSame(FallbackLogin.Outcome.FAILED, login.authenticate(credentials("alice", "wrong-" + i), "alice", "fixture-password-" + round));
      }
      assertAccess(login.authenticate(credentials("alice", "fixture-password-" + round), "alice", "fixture-password-" + round), "alice");
    }
  }

  private static String configured(String password, boolean hashed) {
    return hashed ? AdminPassword.hash(password.toCharArray(), AdminPassword.MIN_ITERATIONS) : password;
  }

  private static void assertAccess(FallbackLogin.Outcome outcome, String username) {
    AdminAccess access = outcome.access;
    assertNotNull(access);
    assertEquals(username, access.name());
    assertTrue(access.has(AdminPermission.OPERATE));
    assertTrue(outcome.credentials);
    assertEquals(0, outcome.retryAfter);
  }

  private static HttpServletRequest credentials(String username, String password) {
    return credentials(username, password, CLIENT);
  }

  private static HttpServletRequest credentials(String username, String password, String address) {
    return request("Basic " + Base64.getEncoder().encodeToString(
        (username + ":" + password).getBytes(StandardCharsets.UTF_8)), address);
  }

  private static HttpServletRequest request(@Nullable String authorization, String address) {
    return (HttpServletRequest) Proxy.newProxyInstance(FallbackLoginTest.class.getClassLoader(),
        new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
          if (method.getName().equals("getHeader") && "Authorization".equals(args[0])) return authorization;
          if (method.getName().equals("getRemoteAddr")) return address;
          throw new UnsupportedOperationException(method.getName());
        });
  }
}
