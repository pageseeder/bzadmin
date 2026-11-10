package org.pageseeder.bzadmin.redirect;

import static org.pageseeder.bzadmin.berlioz.BerliozMappings.HTML_PREFIX;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.XML_EXTENSION;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.XML_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

import org.jspecify.annotations.Nullable;
import org.pageseeder.bzadmin.berlioz.BerliozMappings;

/**
 * The registrations of the Berlioz redirect filters in a servlet context, and whether they cover the
 * Config page's reload operation.
 *
 * <p>The Servlet API exposes neither dispatcher types nor filter order, so they are not reported.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class FilterRegistrations {

  /** The reload operation, under the admin services. */
  static final String RELOAD = "/bzadmin/api/config/reload";

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

  /**
   * @param context The servlet context.
   * @return the paths of the reload operation for the Berlioz XML mappings of the context, empty if
   *   there is none, since the operation then cannot run.
   */
  static List<String> reloadPaths(ServletContext context) {
    Set<String> mappings = BerliozMappings.of(context);
    List<String> paths = new ArrayList<>();
    if (mappings.contains(XML_EXTENSION)) paths.add(RELOAD + ".xml");
    if (mappings.contains(XML_PREFIX)) paths.add("/xml" + RELOAD);
    return paths;
  }

  /**
   * @param registration The registration of a filter.
   * @param context      The servlet context.
   * @param paths        The paths of the reload operation.
   * @return whether the filter applies to a request to one of the paths, so that it reloads its rules;
   *   {@code true} when there is no path to check.
   */
  static boolean coversReload(Registration registration, ServletContext context, List<String> paths) {
    if (paths.isEmpty()) return true;
    for (String path : paths) {
      for (String pattern : registration.urlPatterns) {
        if (matches(pattern, path)) return true;
      }
    }
    // A filter mapped to a servlet covers the requests it serves
    for (String name : registration.servletNames) {
      ServletRegistration servlet = context.getServletRegistration(name);
      if (servlet == null) continue;
      Collection<String> mappings = nonNull(servlet.getMappings());
      if (mappings.contains(XML_EXTENSION) || mappings.contains(XML_PREFIX)) return true;
    }
    return false;
  }

  /**
   * Whether a URL pattern of a filter mapping matches a path, as the Servlet specification defines:
   * exact, path prefix ({@code /a/*}), extension ({@code *.xml}), or every path ({@code /*}, {@code /}).
   *
   * @param pattern The URL pattern.
   * @param path    The path, relative to the context.
   * @return whether the pattern matches.
   */
  static boolean matches(String pattern, String path) {
    if ("/*".equals(pattern) || "/".equals(pattern)) return true;
    if (pattern.endsWith("/*")) {
      String prefix = pattern.substring(0, pattern.length() - 2);
      return path.equals(prefix) || path.startsWith(prefix + "/");
    }
    if (pattern.startsWith("*.")) {
      String name = path.substring(path.lastIndexOf('/') + 1);
      return name.endsWith(pattern.substring(1));
    }
    return pattern.equals(path);
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
