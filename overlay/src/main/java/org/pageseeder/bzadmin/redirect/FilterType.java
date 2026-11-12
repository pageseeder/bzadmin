package org.pageseeder.bzadmin.redirect;

/**
 * The two Berlioz filters whose configuration the Redirects view reports.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
enum FilterType {

  /** {@code RedirectFilter}: redirects (301, 302, 307 or 308). */
  REDIRECT("redirect", "org.pageseeder.berlioz.servlet.RedirectFilter"),

  /** {@code RelocationFilter}: forwards, called relocations in Berlioz. */
  RELOCATION("relocation", "org.pageseeder.berlioz.servlet.RelocationFilter");

  private final String id;

  private final String className;

  FilterType(String id, String className) {
    this.id = id;
    this.className = className;
  }

  /**
   * @return the type in the output, as in Berlioz's snapshot: {@code redirect} or {@code relocation}.
   */
  String id() {
    return this.id;
  }

  /**
   * @return the class name of the filter, compared without loading it.
   */
  String className() {
    return this.className;
  }

}
