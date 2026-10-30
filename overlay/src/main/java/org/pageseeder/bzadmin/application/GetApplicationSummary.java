package org.pageseeder.bzadmin.application;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.BerliozOption;
import org.pageseeder.berlioz.GlobalSettings;
import org.pageseeder.berlioz.content.Environment;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.content.ServiceLoader;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.xslt.XsltCacheMode;
import org.pageseeder.berlioz.xslt.XsltProcessorInfo;
import org.pageseeder.bzadmin.access.AdminFilter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.cache.CacheAdminManager;
import org.pageseeder.bzadmin.cache.CacheAdminProvider;
import org.pageseeder.bzadmin.cache.CacheRegistry;
import org.pageseeder.bzadmin.log.LogFiles;
import org.pageseeder.bzadmin.log.LoggingBackends;

/**
 * A summary of the application: the versions of its components, the optional integrations that
 * the admin pages detected, and the status of the last load of the configuration and services.
 *
 * <pre>{@code
 * <application-summary mode="test">
 *   <component name="bzadmin" version="0.14.0"/>
 *   <component name="berlioz" version="0.14.5"/>
 *   <component name="java" product="OpenJDK 64-Bit Server VM" vendor="Eclipse Adoptium" version="11.0.32+1"/>
 *   <component name="xslt" product="SAXON" version="HE 12.10" vendor="Saxonica" xslt-version="3.0" factory="net.sf.saxon.TransformerFactoryImpl"/>
 *   <component name="container" product="jetty/10.0.26" servlet-api="4.0"/>
 *   <logging backend="logback" recent-events="true" log-files="backend"/>
 *   <cache-provider id="ehcache2" label="EHCache 2.11.0.3.31" managers="2"/>
 *   <access roles="true" fallback-login="false"/>
 *   <status services="ready" configuration="loaded" properties="12" xslt-cache="manual"/>
 * </application-summary>
 * }</pre>
 *
 * <p>Only versions and names: no path, environment variable or configured value, and nothing that
 * says how access is configured beyond which sources are set. A version or name that cannot be
 * determined is absent, never guessed; an integration the host does not have is absent too (no
 * {@code cache-provider}) or says so ({@code recent-events="false"}), and is not an error.
 *
 * <p>The XSLT processor is the one Berlioz uses, as it identifies itself ({@link XsltProcessorInfo}:
 * the {@code xsl:product-name}, {@code xsl:product-version}, {@code xsl:vendor} and
 * {@code xsl:version} system properties); the container is the one Berlioz reports
 * ({@link Environment#getServerInfo()}).
 *
 * <p>{@code log-files} is {@code property} when the host lists its log files in
 * {@value LogFiles#PROPERTY}, otherwise {@code backend} (the directories of the logging framework).
 * {@code configuration} is {@code none} without a configuration file, {@code empty} when it has no
 * property (including a configuration that failed to load), and {@code loaded} otherwise.
 *
 * <p>In JSON: {@code {"mode": "test", "components": [{"name": "bzadmin", ...}], "logging": {...},
 * "cacheProviders": [{"id": "ehcache2", ...}], "access": {...}, "status": {...}}} (names in camel case).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetApplicationSummary implements Generator {

  /** The resource with the version of the overlay, set by the build. */
  static final String VERSION_RESOURCE = "/org/pageseeder/bzadmin/bzadmin.properties";

  private static final String COMPONENT = "component";

  private static final String PRODUCT = "product";

  private static final String VERSION = "version";

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    out.startObject("application-summary");
    out.field("mode", GlobalSettings.getMode());
    out.startArray("components", ContextOption.JSON_ONLY);
    component(out, "bzadmin", null, Components.BZADMIN);
    component(out, "berlioz", null, known(GlobalSettings.getVersion()));
    java(out);
    xslt(out);
    container(out, req.getEnvironment());
    out.endArray();
    logging(out);
    caches(out);
    access(out);
    status(out);
    out.endObject();
    return Response.ok();
  }

  private static void component(OutputWriter out, String name, @Nullable String product, @Nullable String version) {
    out.startObject(COMPONENT);
    out.field("name", name);
    if (product != null) out.field(PRODUCT, product);
    if (version != null) out.field(VERSION, version);
    out.endObject();
  }

  private static void java(OutputWriter out) {
    out.startObject(COMPONENT);
    out.field("name", "java");
    @Nullable String product = System.getProperty("java.vm.name");
    @Nullable String vendor = System.getProperty("java.vendor");
    if (product != null) out.field(PRODUCT, product);
    if (vendor != null) out.field("vendor", vendor);
    out.field(VERSION, Runtime.version().toString());
    out.endObject();
  }

  static void xslt(OutputWriter out) {
    XsltProcessorInfo processor = XsltProcessorInfo.current();
    @Nullable String factory = processor.factory();
    if (factory == null) return;
    out.startObject(COMPONENT);
    out.field("name", "xslt");
    out.optionalField(PRODUCT, processor.productName());
    out.optionalField(VERSION, processor.productVersion());
    out.optionalField("vendor", processor.vendor());
    out.optionalField("xslt-version", processor.xsltVersion());
    out.field("factory", factory);
    out.endObject();
  }

  static void container(OutputWriter out, Environment env) {
    @Nullable String product = env.getServerInfo();
    if (product == null) return;
    out.startObject(COMPONENT);
    out.field("name", "container");
    out.field(PRODUCT, product);
    out.optionalField("servlet-api", env.getServletApiVersion());
    out.endObject();
  }

  private static void logging(OutputWriter out) {
    out.startObject("logging");
    out.field("backend", LoggingBackends.name());
    out.field("recent-events", LoggingBackends.capturesRecentEvents());
    out.field("log-files", isSet(LogFiles.PROPERTY) ? "property" : "backend");
    out.endObject();
  }

  private static void caches(OutputWriter out) {
    out.startArray("cache-providers", ContextOption.JSON_ONLY);
    for (CacheAdminProvider provider : CacheRegistry.global().providers()) {
      out.startObject("cache-provider");
      out.field("id", provider.id());
      out.field("label", provider.label());
      try {
        List<CacheAdminManager> managers = provider.managers();
        out.field("managers", managers.size());
      } catch (RuntimeException ex) {
        // Reported with the details by the cache page
        out.field("failed", ex.getClass().getName());
      }
      out.endObject();
    }
    out.endArray();
  }

  private static void access(OutputWriter out) {
    out.startObject("access");
    out.field("roles", isSet(AdminFilter.ROLE_VIEW) || isSet(AdminFilter.ROLE_OPERATE));
    out.field("fallback-login", isSet(AdminFilter.USERNAME));
    out.endObject();
  }

  private static void status(OutputWriter out) {
    out.startObject("status");
    out.field("services", ServiceLoader.getInstance().getLoadStatus());
    int properties = GlobalSettings.countProperties();
    out.field("configuration", configuration(GlobalSettings.getPropertiesFile(), properties));
    out.field("properties", properties);
    out.field("xslt-cache", XsltCacheMode.from(GlobalSettings.get(BerliozOption.XSLT_CACHE)).toString());
    out.endObject();
  }

  private static String configuration(@Nullable File file, int properties) {
    if (file == null || !file.exists()) return "none";
    return properties == 0 ? "empty" : "loaded";
  }

  private static boolean isSet(String property) {
    @Nullable String value = GlobalSettings.get(property);
    return value != null && !value.trim().isEmpty();
  }

  private static @Nullable String known(@Nullable String version) {
    return version == null || version.isEmpty() || "unknown".equals(version) ? null : version;
  }

  /**
   * The components that do not change while the application runs, detected once.
   */
  static final class Components {

    static final @Nullable String BZADMIN = bzadminVersion();

    private Components() {
    }

    private static @Nullable String bzadminVersion() {
      try (@Nullable InputStream in = GetApplicationSummary.class.getResourceAsStream(VERSION_RESOURCE)) {
        if (in == null) return null;
        Properties properties = new Properties();
        properties.load(in);
        @Nullable String version = properties.getProperty(VERSION);
        // Not replaced when the resources were not processed by the build
        return version == null || version.startsWith("@") ? null : version;
      } catch (IOException ex) {
        return null;
      }
    }
  }

}
