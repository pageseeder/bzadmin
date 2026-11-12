package org.pageseeder.bzadmin.redirect;

import java.util.List;

/**
 * The checks and output of rule values: forwards to admin URLs, and the bound on values. Berlioz
 * redacts the targets in its snapshot ({@code Redaction.redactUriPattern}).
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class Targets {

  /** The most characters of a value in the output, before {@code …}. */
  static final int MAX_VALUE = 2048;

  private Targets() {
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
   * @param value A value of a rule or a failure message.
   * @param max   The most characters.
   * @return the value, or its first characters followed by {@code …}.
   */
  static String bound(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max) + "…";
  }

}
