package org.pageseeder.bzadmin.redirect;

/**
 * The two Berlioz filters whose configuration the Redirects view reports.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
enum FilterType {

  /** {@code RedirectFilter}: redirects (301 or 302). */
  REDIRECT("redirect", "org.pageseeder.berlioz.servlet.RedirectFilter", "redirect-mapping"),

  /** {@code RelocationFilter}: forwards, called relocations in Berlioz. */
  RELOCATION("relocation", "org.pageseeder.berlioz.servlet.RelocationFilter", "relocation-mapping");

  private final String id;

  private final String className;

  private final String root;

  FilterType(String id, String className, String root) {
    this.id = id;
    this.className = className;
    this.root = root;
  }

  /**
   * @return the type in the output, also the name of the rule elements: {@code redirect} or {@code relocation}.
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

  /**
   * @return the name of the root element of its configuration file.
   */
  String root() {
    return this.root;
  }

  /**
   * @return the path of the schema in the Berlioz JAR, which is not a public API.
   */
  String schemaResource() {
    return "/schema/" + this.root + "-1.0.xsd";
  }

}
