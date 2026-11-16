package org.pageseeder.bzadmin.redirect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.furi.URIPattern;

/**
 * The redirects whose target the same filter redirects again ({@code docs/design/redirects.md},
 * "Chains and loops").
 *
 * <p>Only fixed targets are followed: a path without template expression, starting with {@code /}
 * but not {@code //}, that the filter's URL patterns cover. Berlioz matches the request URI, without
 * the query, against the rules in file order, so each such rule leads to the first rule its target
 * matches. A rule leads to a loop when following these links comes back to a rule already followed.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class Chains {

  /** No rule: the target is not followed, or no rule matches it. */
  static final int NONE = -1;

  /** For each rule, the index of the first rule its target matches, or {@link #NONE}. */
  private final int[] next;

  /** For each rule, whether following its target never ends. */
  private final boolean[] loops;

  private Chains(int[] next, boolean[] loops) {
    this.next = next;
    this.loops = loops;
  }

  /**
   * @param index The index of a rule, in file order.
   * @return the index of the first rule that its target matches, or {@link #NONE}.
   */
  int next(int index) {
    return this.next[index];
  }

  /**
   * @param index The index of a rule, in file order.
   * @return whether following the redirects from this rule comes back to a rule already followed.
   */
  boolean loops(int index) {
    return this.loops[index];
  }

  /**
   * @param from        The {@code from} patterns of the loaded rules, in file order.
   * @param to          Their targets, in the same order.
   * @param contextPath The context path, empty for the root context.
   * @param urlPatterns The URL patterns of the filter.
   * @return the links between the rules.
   */
  static Chains of(List<String> from, List<String> to, String contextPath, Collection<String> urlPatterns) {
    int size = from.size();
    List<@Nullable URIPattern> patterns = new ArrayList<>(size);
    for (String f : from) patterns.add(pattern(f));
    int[] next = new int[size];
    Arrays.fill(next, NONE);
    for (int i = 0; i < size && i < to.size(); i++) {
      @Nullable String path = fixedPath(to.get(i));
      if (path == null || !covers(contextPath, urlPatterns, path)) continue;
      for (int j = 0; j < size; j++) {
        URIPattern pattern = patterns.get(j);
        if (pattern != null && pattern.match(path)) {
          next[i] = j;
          break;
        }
      }
    }
    return new Chains(next, loops(next));
  }

  /**
   * Each rule links to one rule at most, so the rules that never end are those on a cycle or leading
   * into one. Each rule is visited once.
   */
  private static boolean[] loops(int[] next) {
    final int unknown = 0;
    final int visiting = 1;
    final int ends = 2;
    final int loops = 3;
    int[] state = new int[next.length];
    List<Integer> path = new ArrayList<>();
    for (int start = 0; start < next.length; start++) {
      path.clear();
      int current = start;
      while (current != NONE && state[current] == unknown) {
        state[current] = visiting;
        path.add(current);
        current = next[current];
      }
      // A rule of this path again (a cycle), or a rule known to loop
      boolean loop = current != NONE && (state[current] == visiting || state[current] == loops);
      for (int index : path) state[index] = loop ? loops : ends;
    }
    boolean[] result = new boolean[next.length];
    for (int i = 0; i < next.length; i++) result[i] = state[i] == loops;
    return result;
  }

  private static @Nullable URIPattern pattern(String from) {
    try {
      return new URIPattern(from);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * @param to The target of a redirect.
   * @return the path that the browser requests next, or {@code null} when it depends on the request
   *   or is not on this server: a template expression before the query, an absolute URL, a path that
   *   does not start with {@code /} or starts with {@code //}.
   */
  static @Nullable String fixedPath(String to) {
    int end = to.length();
    int query = to.indexOf('?');
    if (query >= 0) end = query;
    int fragment = to.indexOf('#');
    if (fragment >= 0 && fragment < end) end = fragment;
    String path = to.substring(0, end);
    if (!path.startsWith("/") || path.startsWith("//") || path.indexOf('{') >= 0) return null;
    return path;
  }

  /**
   * Whether the request to this path passes through a filter with these URL patterns, as the
   * container matches them: exact, {@code /prefix/*}, {@code *.extension} or {@code /*}.
   *
   * @param contextPath The context path, empty for the root context.
   * @param urlPatterns The URL patterns of the filter.
   * @param path        The path of the request, including the context path.
   * @return whether one of the patterns covers the path.
   */
  static boolean covers(String contextPath, Collection<String> urlPatterns, String path) {
    if (!path.startsWith(contextPath)) return false;
    String rest = path.substring(contextPath.length());
    if (!rest.startsWith("/")) return false;
    for (String pattern : urlPatterns) {
      if (matches(pattern, rest)) return true;
    }
    return false;
  }

  /**
   * Whether this URL pattern matches the path, as the container matches them.
   *
   * @param pattern The URL pattern of the filter.
   * @param rest    The path of the request, without the context path.
   * @return whether the pattern covers the path.
   */
  private static boolean matches(String pattern, String rest) {
    if (pattern.equals("/*") || pattern.equals(rest)) return true;
    if (pattern.endsWith("/*")) {
      String prefix = pattern.substring(0, pattern.length() - 2);
      return rest.equals(prefix) || rest.startsWith(prefix + "/");
    }
    if (pattern.startsWith("*.")) {
      String name = rest.substring(rest.lastIndexOf('/') + 1);
      return name.endsWith(pattern.substring(1));
    }
    return false;
  }

}
