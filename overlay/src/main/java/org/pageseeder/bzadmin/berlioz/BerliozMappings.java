package org.pageseeder.bzadmin.berlioz;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

/**
 * The URL patterns Berlioz is mapped to: by extension ({@code *.html} and {@code *.xml}) or by
 * prefix ({@code /html/*} and {@code /xml/*}).
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class BerliozMappings {

  /** The extension mapping of the HTML pages. */
  public static final String HTML_EXTENSION = "*.html";

  /** The extension mapping of the XML output. */
  public static final String XML_EXTENSION = "*.xml";

  /** The prefix mapping of the HTML pages. */
  public static final String HTML_PREFIX = "/html/*";

  /** The prefix mapping of the XML output. */
  public static final String XML_PREFIX = "/xml/*";

  private BerliozMappings() {
  }

  /**
   * @param context The servlet context.
   * @return the URL patterns of all the servlets of the context.
   */
  public static Set<String> of(ServletContext context) {
    Set<String> mappings = new HashSet<>();
    for (ServletRegistration servlet : context.getServletRegistrations().values()) {
      // Some containers return null rather than an empty collection
      Collection<String> patterns = servlet.getMappings();
      if (patterns != null) mappings.addAll(patterns);
    }
    return mappings;
  }

}
