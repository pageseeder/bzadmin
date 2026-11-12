package org.pageseeder.bzadmin.redirect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Forwards to admin URLs and the bound on values.
 */
final class TargetsTest {

  @ParameterizedTest
  @CsvSource({
      "/bzadmin,                false, true",
      "/bzadmin/,               false, true",
      "/bzadmin/system.html,    false, true",
      "/bzadmin.html,           false, true",
      "/bzadmin/{+path},        false, true",
      "/bzadmin{x},             false, false",
      "/{+path},                false, false",
      "/bzadmins/x,             false, false",
      "/a/bzadmin/x,            false, false",
      "/html/bzadmin/config,    false, false",
      "/html/bzadmin/config,    true,  true",
      "/xml/bzadmin/api/x,      true,  true"})
  void reportsForwardsToAdmin(String to, boolean prefix, boolean expected) {
    List<String> paths = prefix ? List.of("/bzadmin", "/html/bzadmin", "/xml/bzadmin") : List.of("/bzadmin");
    assertEquals(expected, Targets.forwardsToAdmin(to, paths));
  }

  @ParameterizedTest
  @CsvSource({"abc, 3, abc", "abcd, 3, abc…", "'', 3, ''"})
  void boundsValues(String value, int max, String expected) {
    assertEquals(expected, Targets.bound(value, max));
  }

}
