package org.pageseeder.bzadmin.redirect;

import static org.pageseeder.bzadmin.berlioz.BerliozMappings.HTML_PREFIX;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.XML_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

import org.jspecify.annotations.Nullable;
import org.pageseeder.bzadmin.berlioz.BerliozMappings;

/**
 * The registrations of the Berlioz redirect filters in a servlet context, and the admin paths that
 * a relocation should not forward to.
 *
 * <p>The Servlet API exposes neither dispatcher types nor filter order, so they are not reported.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class FilterRegistrations {

  private FilterRegistrations() {
  }

  /**
   * A registration of one of the filters.
   */
  static final class Registration {

    final FilterType type;

    final String name;

    final @Nullable String config;

    final List<String> urlPatterns;

    final List<String> servletNames;

    Registration(FilterType type, String name, @Nullable String config, Collection<String> urlPatterns,
        Collection<String> servletNames) {
      this.type = type;
      this.name = name;
      this.config = config;
      this.urlPatterns = List.copyOf(urlPatterns);
      this.servletNames = List.copyOf(servletNames);
    }

  }

  /**
   * @param context The servlet context.
   * @return the registrations of both filters, sorted by type, then name.
   */
  static List<Registration> of(ServletContext context) {
    List<Registration> registrations = new ArrayList<>();
    for (Map.Entry<String, ? extends FilterRegistration> entry : context.getFilterRegistrations().entrySet()) {
      FilterRegistration filter = entry.getValue();
      for (FilterType type : FilterType.values()) {
        if (type.className().equals(filter.getClassName())) {
          registrations.add(new Registration(type, entry.getKey(), filter.getInitParameter("config"),
              nonNull(filter.getUrlPatternMappings()), nonNull(filter.getServletNameMappings())));
        }
      }
    }
    registrations.sort(Comparator.comparing((Registration r) -> r.type).thenComparing(r -> r.name));
    return registrations;
  }

  private static Collection<String> nonNull(@Nullable Collection<String> values) {
    return values != null ? values : List.of();
  }

  /**
   * @param context The servlet context.
   * @return the paths of the admin services, relative to the context: {@code /bzadmin}, and
   *   {@code /html/bzadmin} or {@code /xml/bzadmin} when Berlioz is mapped by prefix.
   */
  static List<String> adminPaths(ServletContext context) {
    Set<String> mappings = BerliozMappings.of(context);
    List<String> paths = new ArrayList<>();
    paths.add("/bzadmin");
    if (mappings.contains(HTML_PREFIX)) paths.add("/html/bzadmin");
    if (mappings.contains(XML_PREFIX)) paths.add("/xml/bzadmin");
    return paths;
  }

}
