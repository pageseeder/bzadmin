package org.pageseeder.bzadmin.redirect;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The checks and output of rule values: redaction of targets, forwards to admin URLs, and the bound
 * on values.
 *
 * <p>Values are URI patterns, whose template expressions ({@code {q}}, {@code {?q}}, {@code {#f}}) can
 * contain {@code ?}, {@code &} and {@code #}: the delimiters are only looked for outside braces.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class Targets {

  /** The most characters of a value in the output, before {@code …}. */
  static final int MAX_VALUE = 2048;

  /** What replaces the value of a query parameter. */
  static final String REDACTED = "[redacted]";

  /** The user information of an absolute URL: scheme, '://', userinfo, '@'. */
  private static final Pattern USER_INFO = Pattern.compile("^([A-Za-z][A-Za-z0-9+.-]*://)[^/?#@{}]*@");

  /** A value that is only one template expression. */
  private static final Pattern EXPRESSION = Pattern.compile("\\{[^{}]*\\}");

  private Targets() {
  }

  /**
   * Redacts a target: the value of every query parameter, except a single template expression, and
   * the user information of an absolute URL; the fragment is kept.
   *
   * @param to The target pattern.
   * @return the redacted target.
   */
  static String redact(String to) {
    String target = USER_INFO.matcher(to).replaceFirst("$1");
    int fragment = indexOutsideBraces(target, '#', 0);
    String beforeFragment = fragment >= 0 ? target.substring(0, fragment) : target;
    String afterFragment = fragment >= 0 ? target.substring(fragment) : "";
    int query = indexOutsideBraces(beforeFragment, '?', 0);
    if (query < 0) return target;
    StringBuilder out = new StringBuilder(beforeFragment.substring(0, query + 1));
    String parameters = beforeFragment.substring(query + 1);
    int start = 0;
    while (start <= parameters.length()) {
      int end = indexOutsideBraces(parameters, '&', start);
      if (end < 0) end = parameters.length();
      out.append(redactParameter(parameters.substring(start, end)));
      if (end < parameters.length()) out.append('&');
      start = end + 1;
    }
    return out.append(afterFragment).toString();
  }

  private static String redactParameter(String parameter) {
    int equals = indexOutsideBraces(parameter, '=', 0);
    if (equals < 0) return parameter;
    String value = parameter.substring(equals + 1);
    if (EXPRESSION.matcher(value).matches()) return parameter;
    return parameter.substring(0, equals + 1) + REDACTED;
  }

  /**
   * Whether a relocation forwards to an admin URL: the literal start of its target, before any
   * template expression, is an admin path followed by {@code /} or {@code .}, or the whole target
   * is an admin path. Relocation targets are dispatched relative to the context.
   *
   * @param to         The target pattern of a relocation.
   * @param adminPaths The admin paths, e.g. {@code /bzadmin}.
   * @return whether the forward reaches the admin services.
   */
  static boolean forwardsToAdmin(String to, List<String> adminPaths) {
    int expression = to.indexOf('{');
    String literal = expression >= 0 ? to.substring(0, expression) : to;
    for (String path : adminPaths) {
      if (literal.startsWith(path + "/") || literal.startsWith(path + ".")) return true;
      if (expression < 0 && literal.equals(path)) return true;
    }
    return false;
  }

  /**
   * @param value A value of the file or a parser message.
   * @param max   The most characters.
   * @return the value, or its first characters followed by {@code …}.
   */
  static String bound(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max) + "…";
  }

  private static int indexOutsideBraces(String value, char c, int from) {
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '{') depth++;
      else if (ch == '}' && depth > 0) depth--;
      else if (ch == c && depth == 0 && i >= from) return i;
    }
    return -1;
  }

}
