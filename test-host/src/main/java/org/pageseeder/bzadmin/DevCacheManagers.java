package org.pageseeder.bzadmin;

import java.net.URI;
import java.util.List;
import java.util.OptionalLong;

import javax.cache.Caching;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import net.sf.ehcache.CacheManager;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.config.DefaultConfiguration;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.pageseeder.bzadmin.cache.JCacheManagers;

import org.jspecify.annotations.Nullable;

/**
 * Creates the cache managers of the application, as a host does, so that the cache pages have
 * caches to show: the admin pages never create one. In place of Bastille's
 * {@code StaticCachingFilter}, which created the EHCache 2 manager on first use.
 *
 * <ul>
 *   <li>EHCache 2, the default manager from {@code ehcache.xml} on the class path
 *     ({@code static-web-caching}, {@code PSML});</li>
 *   <li>JCache with EHCache 3, registered as {@code dev-ehcache3}: {@code static-files};</li>
 *   <li>JCache with Caffeine, registered as {@code dev-caffeine}: {@code static-files}.</li>
 * </ul>
 *
 * <p>The static file caches (at most 100 entries on the heap, no expiry, statistics enabled) are
 * those of {@link DevStaticCacheFilter}. Both JCache implementations are on the class path, so each
 * manager names its caching provider. Closed, unregistered and shut down when the web application
 * stops.
 */
public final class DevCacheManagers implements ServletContextListener {

  /** Creates the development-host cache lifecycle listener. */
  public DevCacheManagers() {
  }

  /** The identifier of the EHCache 3 manager in the admin pages. */
  public static final String EHCACHE3_ID = "dev-ehcache3";

  /** The identifier of the Caffeine manager in the admin pages. */
  public static final String CAFFEINE_ID = "dev-caffeine";

  /** The cache of static files of the EHCache 2 manager (in {@code ehcache.xml}). */
  public static final String EHCACHE2_STATIC = "static-web-caching";

  /** The cache of static files of the JCache managers. */
  public static final String JCACHE_STATIC = "static-files";

  private static final String EHCACHE3 = "org.ehcache.jsr107.EhcacheCachingProvider";

  private static final String CAFFEINE = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";

  /** The servlet context attribute with the stores of {@link DevStaticCacheFilter}. */
  private static final String STATIC_STORES = DevCacheManagers.class.getName() + ".static";

  private static final long STATIC_ENTRIES = 100;

  private @Nullable CacheManager ehcache;

  private javax.cache.@Nullable CacheManager ehcache3Manager;

  private javax.cache.@Nullable CacheManager caffeineManager;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    this.ehcache = CacheManager.create();

    ClassLoader loader = DevCacheManagers.class.getClassLoader();

    // EHCache 3 reads the URI of a manager as the location of its XML configuration: a programmatic one instead
    EhcacheCachingProvider ehcache3Provider = (EhcacheCachingProvider) Caching.getCachingProvider(EHCACHE3, loader);
    this.ehcache3Manager = ehcache3Provider.getCacheManager(URI.create("urn:x-bzadmin:dev:ehcache3"), new DefaultConfiguration(loader));
    this.ehcache3Manager.createCache(JCACHE_STATIC, Eh107Configuration.fromEhcacheCacheConfiguration(
        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class, ResourcePoolsBuilder.heap(STATIC_ENTRIES))));
    this.ehcache3Manager.enableStatistics(JCACHE_STATIC, true);
    JCacheManagers.register(EHCACHE3_ID, this.ehcache3Manager);

    this.caffeineManager = Caching.getCachingProvider(CAFFEINE, loader).getCacheManager(URI.create("urn:x-bzadmin:dev:caffeine"), loader);
    this.caffeineManager.createCache(JCACHE_STATIC, new CaffeineConfiguration<String, byte[]>()
        .setTypes(String.class, byte[].class)
        .setMaximumSize(OptionalLong.of(STATIC_ENTRIES))
        .setStatisticsEnabled(true));
    JCacheManagers.register(CAFFEINE_ID, this.caffeineManager);

    event.getServletContext().setAttribute(STATIC_STORES, List.of(
        DevStaticCacheFilter.Store.of(this.ehcache.getEhcache(EHCACHE2_STATIC)),
        DevStaticCacheFilter.Store.of(this.ehcache3Manager.getCache(JCACHE_STATIC, String.class, byte[].class)),
        DevStaticCacheFilter.Store.of(this.caffeineManager.getCache(JCACHE_STATIC, String.class, byte[].class))));
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    event.getServletContext().removeAttribute(STATIC_STORES);
    JCacheManagers.unregister(CAFFEINE_ID);
    if (this.caffeineManager != null) this.caffeineManager.close();
    JCacheManagers.unregister(EHCACHE3_ID);
    if (this.ehcache3Manager != null) this.ehcache3Manager.close();
    if (this.ehcache != null) this.ehcache.shutdown();
  }

  /**
   * @return the stores of the static file caches, none before the managers are created.
   */
  @SuppressWarnings("unchecked")
  static List<DevStaticCacheFilter.Store> staticStores(ServletContext context) {
    Object stores = context.getAttribute(STATIC_STORES);
    return stores != null ? (List<DevStaticCacheFilter.Store>) stores : List.of();
  }

}
