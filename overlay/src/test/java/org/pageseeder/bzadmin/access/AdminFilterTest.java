package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
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

}
