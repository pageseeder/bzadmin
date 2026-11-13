package org.pageseeder.bzadmin.redirect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Redirects whose fixed target the same filter redirects again: chains, loops, and the targets that
 * are not followed.
 */
final class ChainsTest {

  private static final List<String> ALL = List.of("/*");

  @Test
  void followsTheFirstRuleThatMatches() {
    Chains chains = Chains.of(
        List.of("/a", "/b", "/{+path}", "/c"),
        List.of("/b", "/c", "/end", "/end"), "", ALL);
    // The target of /a matches /b; that of /b matches /{+path} before /c
    assertEquals(1, chains.next(0));
    assertEquals(2, chains.next(1));
    // /end matches /{+path}, whose target is /end again
    assertEquals(2, chains.next(2));
    assertTrue(chains.loops(2), "Redirects to itself");
    assertTrue(chains.loops(0), "Leads into a loop");
    assertTrue(chains.loops(3));
  }

  @Test
  void tellsChainsFromLoops() {
    Chains chains = Chains.of(
        List.of("/a", "/b", "/c", "/d", "/e"),
        List.of("/b", "/home", "/d", "/c", "/e?x=1"), "", ALL);
    assertEquals(1, chains.next(0));
    assertFalse(chains.loops(0), "Ends at /b, whose target no rule matches");
    assertEquals(Chains.NONE, chains.next(1));
    assertFalse(chains.loops(1));
    // /c and /d redirect to each other
    assertEquals(3, chains.next(2));
    assertEquals(2, chains.next(3));
    assertTrue(chains.loops(2));
    assertTrue(chains.loops(3));
    // The query does not take part in the match
    assertEquals(4, chains.next(4));
    assertTrue(chains.loops(4));
  }

  @Test
  void ignoresTargetsThatAreNotFixed() {
    Chains chains = Chains.of(
        List.of("/a/{x}", "/b", "/c", "/d"),
        List.of("/a/{x}", "https://example.org/b", "//example.org/c", "/d{?q}"), "", ALL);
    for (int i = 0; i < 4; i++) assertEquals(Chains.NONE, chains.next(i), "Rule " + i);
  }

  @Test
  void followsOnlyTargetsThatTheFilterCovers() {
    Chains chains = Chains.of(List.of("/old/a", "/{+path}"), List.of("/new/a", "/old/a"), "", List.of("/old/*"));
    assertEquals(Chains.NONE, chains.next(0), "/new/a does not pass through the filter");
    assertEquals(0, chains.next(1));
    assertFalse(chains.loops(1));
    // Mapped by servlet name only
    assertEquals(Chains.NONE, Chains.of(List.of("/a"), List.of("/a"), "", List.of()).next(0));
  }

  @Test
  void includesTheContextPath() {
    Chains chains = Chains.of(List.of("/app/a", "/app/b"), List.of("/app/b", "/other/b"), "/app", ALL);
    assertEquals(1, chains.next(0));
    assertEquals(Chains.NONE, chains.next(1), "Another context");
  }

  @Test
  void followsEveryRuleOnce() {
    // A chain through every rule, the last one redirecting to the first
    int size = 2000;
    List<String> from = new ArrayList<>();
    List<String> to = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      from.add("/r/" + i);
      to.add("/r/" + ((i + 1) % size));
    }
    Chains chains = Chains.of(from, to, "", ALL);
    for (int i = 0; i < size; i++) assertTrue(chains.loops(i));
  }

  @ParameterizedTest
  @CsvSource({
      "/home.html?ref=x,     /home.html",
      "/home.html#top,       /home.html",
      "/a?b#c,               /a",
      "/,                    /",
      "/x?id={id},           /x",
      "/x/{+path},           ''",
      "/x{?q},               ''",
      "https://host/x,       ''",
      "//host/x,             ''",
      "x,                    ''",
      "'',                   ''"})
  void readsTheFixedPath(String to, String expected) {
    String path = Chains.fixedPath(to);
    assertEquals(expected, path == null ? "" : path);
  }

  @ParameterizedTest
  @CsvSource({
      "'',     /*,      /a/b,      true",
      "'',     /a/*,    /a,        true",
      "'',     /a/*,    /a/b,      true",
      "'',     /a/*,    /ab,       false",
      "'',     *.html,  /a/b.html, true",
      "'',     *.html,  /a.html/b, false",
      "'',     /a,      /a,        true",
      "'',     /a,      /a/b,      false",
      "'',     /,       /a,        false",
      "/app,   /*,      /app/a,    true",
      "/app,   /*,      /apple/a,  false",
      "/app,   /*,      /a,        false"})
  void matchesTheUrlPatterns(String contextPath, String pattern, String path, boolean expected) {
    assertEquals(expected, Chains.covers(contextPath, List.of(pattern), path));
  }

}
