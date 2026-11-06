package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class AdminFilterTest {

  @ParameterizedTest
  @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "127.255.255.255", "::1", "[::1]", "0:0:0:0:0:0:0:1", "[0:0:0:0:0:0:0:1]"})
  void acceptsLoopbackAddresses(String address) {
    assertTrue(AdminFilter.isLoopbackLiteral(address), address);
  }

  /**
   * Other addresses, and names, which are never resolved (a host name could point to 127.0.0.1).
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "10.0.0.1", "192.168.1.10", "128.0.0.1", "0.0.0.0", "127.0.0.256", "999.0.0.1", "127.0.0",
      "127.0.0.1.1", "::", "::2", "fe80::1", "[::1", "localhost", "localhost.example.org", "127.0.0.1.nip.io", "admin.example.org"})
  void rejectsOtherAddresses(String address) {
    assertFalse(AdminFilter.isLoopbackLiteral(address), address);
  }

  /**
   * {@code Sec-Fetch-Site} decides when present, whatever the {@code Origin}: behind a proxy that
   * changes the host or the scheme, the container sees another origin than the browser.
   */
  @ParameterizedTest
  @CsvSource(nullValues = "-", value = {
      // Sec-Fetch-Site      Origin                        Same origin
      "same-origin,          -,                            true",
      "same-origin,          http://localhost:8999,        true",
      "same-origin,          http://localhost:9000,        true",
      "same-origin,          https://localhost:9000,       true",
      "cross-site,           http://localhost:8999,        false",
      "same-site,            http://localhost:8999,        false",
      "none,                 http://localhost:8999,        false",
      "cross-site,           https://attacker.example,     false",
      "cross-site,           -,                            false",
      "SAME-ORIGIN,          http://localhost:8999,        false",
      "'',                   http://localhost:8999,        false",
      // Without Sec-Fetch-Site, the origin of the container
      "-,                    -,                            true",
      "-,                    http://localhost:8999,        true",
      "-,                    HTTP://LOCALHOST:8999,        true",
      "-,                    http://localhost:9000,        false",
      "-,                    https://localhost:8999,       false",
      "-,                    https://attacker.example,     false",
      "-,                    'null',                       false"})
  void checksTheOriginOfOperations(@Nullable String site, @Nullable String origin, boolean expected) {
    assertEquals(expected, AdminFilter.isSameOrigin(request("XMLHttpRequest", site, origin)));
  }

  /**
   * The custom header is required, even when the browser confirms the origin.
   */
  @ParameterizedTest
  @CsvSource(nullValues = "-", value = {"-", "fetch", "xmlhttprequest"})
  void requiresTheCustomHeader(@Nullable String requestedWith) {
    assertFalse(AdminFilter.isSameOrigin(request(requestedWith, "same-origin", "http://localhost:8999")));
  }

  /**
   * @return a request received by a container at {@code http://localhost:8999}.
   */
  private static HttpServletRequest request(@Nullable String requestedWith, @Nullable String site, @Nullable String origin) {
    Map<String, @Nullable String> headers = new HashMap<>();
    headers.put("X-Requested-With", requestedWith);
    headers.put("Sec-Fetch-Site", site);
    headers.put("Origin", origin);
    return (HttpServletRequest) Proxy.newProxyInstance(AdminFilterTest.class.getClassLoader(),
        new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "getHeader": return headers.get(args[0]);
            case "getScheme": return "http";
            case "getServerName": return "localhost";
            case "getServerPort": return 8999;
            default: throw new UnsupportedOperationException(method.getName());
          }
        });
  }

}
