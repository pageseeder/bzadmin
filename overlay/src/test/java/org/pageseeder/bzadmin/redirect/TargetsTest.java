package org.pageseeder.bzadmin.redirect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The redaction of targets, forwards to admin URLs, URL patterns of filter mappings and the bound on
 * values.
 */
final class TargetsTest {

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "/a                                  | /a",
      "/a?token=secret                     | /a?token=[redacted]",
      "/a?token=secret&q={q}&flag#frag     | /a?token=[redacted]&q={q}&flag#frag",
      "/a?x=1&y=2                          | /a?x=[redacted]&y=[redacted]",
      "/a?x={q}{r}                         | /a?x=[redacted]",
      "/a?x=                               | /a?x=[redacted]",
      "/a?                                 | /a?",
      "/a?x=1&                             | /a?x=[redacted]&",
      "/search{?q}                         | /search{?q}",
      "/search{?q}&x=1                     | /search{?q}&x=1",
      "/a{#f}?x=1                          | /a{#f}?x=[redacted]",
      "/a#f?x=1                            | /a#f?x=1",
      "https://user:pass@example.org/x?a=1 | https://example.org/x?a=[redacted]",
      "https://user@example.org/x          | https://example.org/x",
      "https://example.org/x?u=a@b         | https://example.org/x?u=[redacted]",
      "/{+path}                            | /{+path}"})
  void redactsTargets(String to, String expected) {
    assertEquals(expected, Targets.redact(to));
  }

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
  @CsvSource({
      "/*,                 /bzadmin/api/config/reload.xml, true",
      "/,                  /bzadmin/api/config/reload.xml, true",
      "/bzadmin/*,         /bzadmin/api/config/reload.xml, true",
      "/bzadmin/api/*,     /bzadmin/api/config/reload.xml, true",
      "/bzadmin,           /bzadmin/api/config/reload.xml, false",
      "/bzad/*,            /bzadmin/api/config/reload.xml, false",
      "*.xml,              /bzadmin/api/config/reload.xml, true",
      "*.html,             /bzadmin/api/config/reload.xml, false",
      "/xml/*,             /xml/bzadmin/api/config/reload, true",
      "/bzadmin/api/config/reload.xml, /bzadmin/api/config/reload.xml, true",
      "/fixture-redirect/*, /bzadmin/api/config/reload.xml, false"})
  void matchesFilterUrlPatterns(String pattern, String path, boolean expected) {
    assertEquals(expected, FilterRegistrations.matches(pattern, path));
  }

  @ParameterizedTest
  @CsvSource({"abc, 3, abc", "abcd, 3, abc…", "'', 3, ''"})
  void boundsValues(String value, int max, String expected) {
    assertEquals(expected, Targets.bound(value, max));
  }

}
