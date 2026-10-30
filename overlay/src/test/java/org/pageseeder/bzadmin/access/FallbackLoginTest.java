package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class FallbackLoginTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usernameChangeRejectsPreviouslyVerifiedCredentials(boolean hashed) {
    FallbackLogin login = new FallbackLogin();
    String password = configured("fixture-password", hashed);
    HttpServletRequest old = credentials("alice", "fixture-password");
    assertAccess(login.authenticate(old, "alice", password), "alice");
    assertAccess(login.authenticate(old, "alice", password), "alice");

    assertNull(login.authenticate(old, "bob", password));
    HttpServletRequest current = credentials("bob", "fixture-password");
    assertAccess(login.authenticate(current, "bob", password), "bob");
    assertAccess(login.authenticate(current, "bob", password), "bob");
    assertNull(login.authenticate(old, "bob", password));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void passwordChangeRejectsPreviouslyVerifiedCredentials(boolean hashed) {
    FallbackLogin login = new FallbackLogin();
    HttpServletRequest old = credentials("alice", "fixture-old-password");
    assertAccess(login.authenticate(old, "alice", configured("fixture-old-password", hashed)), "alice");

    String password = configured("fixture-new-password", hashed);
    assertNull(login.authenticate(old, "alice", password));
    HttpServletRequest current = credentials("alice", "fixture-new-password");
    assertAccess(login.authenticate(current, "alice", password), "alice");
    assertAccess(login.authenticate(current, "alice", password), "alice");
    assertNull(login.authenticate(old, "alice", password));
  }

  private static String configured(String password, boolean hashed) {
    return hashed ? AdminPassword.hash(password.toCharArray(), AdminPassword.MIN_ITERATIONS) : password;
  }

  private static void assertAccess(AdminAccess access, String username) {
    assertNotNull(access);
    assertEquals(username, access.name());
    assertTrue(access.has(AdminPermission.OPERATE));
  }

  private static HttpServletRequest credentials(String username, String password) {
    String authorization = "Basic " + Base64.getEncoder().encodeToString(
        (username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return (HttpServletRequest) Proxy.newProxyInstance(FallbackLoginTest.class.getClassLoader(),
        new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
          if (method.getName().equals("getHeader") && "Authorization".equals(args[0])) return authorization;
          throw new UnsupportedOperationException(method.getName());
        });
  }
}
