package org.pageseeder.bzadmin.fixture;

import java.net.URI;
import java.util.UUID;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;

import org.pageseeder.bzadmin.cache.CaffeineCaches;
import org.pageseeder.bzadmin.cache.JCacheManagers;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.jspecify.annotations.Nullable;

/**
 * The cache managers of the test host, as a host application makes them: a cache named
 * {@code PSML} in three managers of two providers, so that tests can check that an operation
 * reaches the intended one.
 *
 * <ul>
 *   <li>EHCache 2, the default manager (from {@code ehcache.xml}, also used by the static caching
 *     filter): {@code fixture-key} in {@code PSML};</li>
 *   <li>EHCache 2, the manager {@code fixture}: {@code PSML} with keys of several types
 *     ({@code fixture-key}, the long 42, the integer 7 and a UUID), and {@code clear-me};</li>
 *   <li>JCache with Caffeine, registered as {@code fixture-caffeine}: {@code PSML} (text keys, with
 *     {@code fixture-key}) and {@code long-keys} (the long 42), statistics enabled;</li>
 *   <li>native Caffeine, registered as the manager {@code fixture-native}: {@code pages} (text keys,
 *     with {@code fixture-key}), at most 1000 entries, statistics recorded.</li>
 * </ul>
 *
 * <p>Closed and unregistered when the web application stops.
 */
public final class FixtureCacheManagers implements ServletContextListener {

  /** The identifier of the JCache manager in the admin pages. */
  public static final String JCACHE_ID = "fixture-caffeine";

  /** The identifier of the native Caffeine manager in the admin pages. */
  public static final String CAFFEINE_ID = "fixture-native";

  private static final String FIXTURE_KEY = "fixture-key";

  private static final String CLEAR_ME = "clear-me";

  private static final String CAFFEINE = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";

  private @Nullable CacheManager ehcache;

  private javax.cache.@Nullable CacheManager jcache;

  @Override
  public void contextInitialized(ServletContextEvent event) {
    CacheManager.create().getEhcache("PSML").put(new Element(FIXTURE_KEY, "default"));

    Configuration config = new Configuration().name("fixture");
    config.setUpdateCheck(false);
    this.ehcache = new CacheManager(config);
    this.ehcache.addCache(new Cache(new CacheConfiguration("PSML", 1000)));
    this.ehcache.addCache(new Cache(new CacheConfiguration(CLEAR_ME, 1000)));
    Cache psml = this.ehcache.getCache("PSML");
    psml.put(new Element(FIXTURE_KEY, "fixture"));
    psml.put(new Element(42L, "long"));
    psml.put(new Element(7, "integer"));
    psml.put(new Element(UUID.randomUUID(), "other type"));
    this.ehcache.getCache(CLEAR_ME).put(new Element(FIXTURE_KEY, CLEAR_ME));

    ClassLoader loader = FixtureCacheManagers.class.getClassLoader();
    // A URI per web application: hosts of earlier tests may still have their statistics MBeans
    URI uri = URI.create("urn:x-bzadmin:fixture:" + System.identityHashCode(event.getServletContext()));
    this.jcache = Caching.getCachingProvider(CAFFEINE, loader).getCacheManager(uri, loader);
    createJCaches(this.jcache);
    JCacheManagers.register(JCACHE_ID, this.jcache);

    com.github.benmanes.caffeine.cache.Cache<String, String> pages = Caffeine.newBuilder().maximumSize(1000).recordStats().build();
    pages.put(FIXTURE_KEY, "native");
    CaffeineCaches.register(CAFFEINE_ID, "pages", String.class, pages);
  }

  // The caches belong to the manager, which closes them in contextDestroyed: closing them here would empty the fixture
  @SuppressWarnings("java:S2095")
  private static void createJCaches(javax.cache.CacheManager manager) {
    manager.createCache("PSML", new MutableConfiguration<String, String>().setTypes(String.class, String.class).setStatisticsEnabled(true))
        .put(FIXTURE_KEY, "caffeine");
    manager.createCache("long-keys", new MutableConfiguration<Long, String>().setTypes(Long.class, String.class).setStatisticsEnabled(true))
        .put(42L, "long");
  }

  @Override
  public void contextDestroyed(ServletContextEvent event) {
    CaffeineCaches.unregister(CAFFEINE_ID);
    JCacheManagers.unregister(JCACHE_ID);
    if (this.jcache != null) this.jcache.close();
    if (this.ehcache != null) this.ehcache.shutdown();
  }

}
