package org.pageseeder.bzadmin.access;

import static org.pageseeder.bzadmin.berlioz.BerliozMappings.HTML_EXTENSION;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.HTML_PREFIX;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.XML_EXTENSION;
import static org.pageseeder.bzadmin.berlioz.BerliozMappings.XML_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;

import org.jspecify.annotations.Nullable;
import org.pageseeder.bzadmin.berlioz.BerliozMappings;

/**
 * Warns on the console at startup when Berlioz Admin is installed but cannot run.
 *
 * <p>The supported setups are Berlioz mapped to {@code *.html} and {@code *.xml}, or to
 * {@code /html/*} and {@code /xml/*}, with {@link AdminFilter} on their admin services.
 *
 * <p>The container runs this initializer whatever the web descriptor says, so it reports a filter
 * that was not declared; the URL patterns of a filter may not be known yet at that stage, so the
 * filter checks them when it starts ({@link #check(FilterConfig)}). Logging is not configured at
 * these stages and the host may have no logging backend, so warnings go to the console, as Berlioz
 * reports its own start. A check never prevents the application from starting.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class InstallationCheck implements ServletContainerInitializer {

  /** The context attribute telling that the servlet mappings were checked. */
  private static final String CHECKED = InstallationCheck.class.getName() + ".checked";

  private static final String SEE_ACCESS = " See the access documentation of Berlioz Admin.";

  private static final String NOT_DECLARED =
      "AdminFilter is not declared in the web descriptor, so every admin page is denied." + SEE_ACCESS;

  private static final String NOT_MAPPED =
      "Berlioz is not mapped to *.html and *.xml or to /html/* and /xml/*, so the admin pages cannot run.";

  @Override
  public void onStartup(@Nullable Set<Class<?>> classes, ServletContext context) {
    try {
      checkServlets(context);
      if (!isFilterDeclared(context)) warn(NOT_DECLARED);
    } catch (RuntimeException ex) {
      warn("Unable to check the installation: " + ex);
    }
  }

  /**
   * Checks the mappings of the admin filter when it starts.
   *
   * @param config The configuration of the admin filter.
   */
  static void check(FilterConfig config) {
    try {
      ServletContext context = config.getServletContext();
      checkServlets(context);
      FilterRegistration filter = context.getFilterRegistration(config.getFilterName());
      // A filter mapped to servlets by name covers what they serve
      if (filter == null || !filter.getServletNameMappings().isEmpty()) return;
      filterProblems(BerliozMappings.of(context), filter.getUrlPatternMappings()).forEach(InstallationCheck::warn);
    } catch (RuntimeException ex) {
      warn("Unable to check the mappings of AdminFilter: " + ex);
    }
  }

  /**
   * @param mappings The URL patterns of the servlets.
   * @return the problems preventing the admin pages or their operations from running.
   */
  static List<String> servletProblems(Set<String> mappings) {
    boolean extension = mappings.contains(HTML_EXTENSION);
    boolean prefix = mappings.contains(HTML_PREFIX);
    List<String> problems = new ArrayList<>();
    if (!extension && !prefix) problems.add(NOT_MAPPED);
    if (extension && !mappings.contains(XML_EXTENSION)) problems.add(missingXml(HTML_EXTENSION, XML_EXTENSION));
    if (prefix && !mappings.contains(XML_PREFIX)) problems.add(missingXml(HTML_PREFIX, XML_PREFIX));
    return problems;
  }

  /**
   * @param mappings The URL patterns of the servlets.
   * @param patterns The URL patterns of the admin filter.
   * @return the problems with the URLs covered by the admin filter.
   */
  static List<String> filterProblems(Set<String> mappings, Collection<String> patterns) {
    List<String> paths = new ArrayList<>();
    if (mappings.contains(HTML_EXTENSION) || mappings.contains(XML_EXTENSION)) paths.add("/bzadmin/");
    if (mappings.contains(HTML_PREFIX)) paths.add("/html/bzadmin/");
    if (mappings.contains(XML_PREFIX)) paths.add("/xml/bzadmin/");
    return paths.stream()
        .filter(path -> patterns.stream().noneMatch(pattern -> covers(pattern, path)))
        .map(path -> "AdminFilter is not mapped to " + path + "*, so these admin services are denied." + SEE_ACCESS)
        .collect(Collectors.toList());
  }

  private static String missingXml(String html, String xml) {
    return "Berlioz is mapped to " + html + " without " + xml + ", so operations and live updates of the admin pages cannot work.";
  }

  private static boolean covers(String pattern, String path) {
    return pattern.endsWith("/*") && path.startsWith(pattern.substring(0, pattern.length() - 1));
  }

  private static void checkServlets(ServletContext context) {
    if (context.getAttribute(CHECKED) != null) return;
    context.setAttribute(CHECKED, Boolean.TRUE);
    servletProblems(BerliozMappings.of(context)).forEach(InstallationCheck::warn);
  }

  private static boolean isFilterDeclared(ServletContext context) {
    return context.getFilterRegistrations().values().stream()
        .anyMatch(filter -> AdminFilter.class.getName().equals(filter.getClassName()));
  }

  @SuppressWarnings("java:S106") // Logging is not configured yet and the host may have no backend
  private static void warn(String problem) {
    System.err.println("[BERLIOZ_INIT] WARNING Berlioz Admin: " + problem);
  }

}
