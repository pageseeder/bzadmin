package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;

/**
 * Several providers at once: EHCache 2, and JCache with EHCache 3 and Caffeine, each with a cache
 * named {@code PSML}; and two JCache managers with the same URI.
 */
@EnabledIfSystemProperty(named = "bzadmin.cache.libraries", matches = "AllProviders")
final class MultipleProvidersTest {

  private static final String EHCACHE3 = "org.ehcache.jsr107.EhcacheCachingProvider";

  private static final String CAFFEINE = "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";

  private final List<AutoCloseable> closing = new ArrayList<>();

  @AfterEach
  void close() throws Exception {
    for (String id : List.of("ehcache3", "caffeine", "same-uri-1", "same-uri-2")) JCacheManagers.unregister(id);
    for (AutoCloseable closeable : this.closing) closeable.close();
  }

  @Test
  void administersEachProviderSeparately() {
    Configuration config = new Configuration().name("two");
    config.setUpdateCheck(false);
    net.sf.ehcache.CacheManager ehcache2 = new net.sf.ehcache.CacheManager(config);
    this.closing.add(ehcache2::shutdown);
    ehcache2.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("PSML", 100)));
    ehcache2.getEhcache("PSML").put(new Element("k", "ehcache2"));
    javax.cache.CacheManager ehcache3 = jcache(EHCACHE3, "urn:x-bzadmin:multiple");
    javax.cache.CacheManager caffeine = jcache(CAFFEINE, "urn:x-bzadmin:multiple");
    JCacheManagers.register("ehcache3", ehcache3);
    JCacheManagers.register("caffeine", caffeine);
    ehcache3.getCache("PSML", String.class, String.class).put("k", "ehcache3");
    caffeine.getCache("PSML", String.class, String.class).put("k", "caffeine");

    CacheRegistry registry = CacheRegistry.global();
    assertEquals(List.of("ehcache2", "jcache"), List.of(registry.providers().get(0).id(), registry.providers().get(1).id()));
    String xml = ProviderContract.output(registry);
    assertTrue(xml.contains("EhcacheCachingProvider") && xml.contains("CaffeineCachingProvider"), xml);

    registry.cache("jcache", "caffeine", "PSML", CacheCapability.CLEAR).get().clear();
    assertNull(caffeine.getCache("PSML", String.class, String.class).get("k"));
    assertEquals("ehcache3", ehcache3.getCache("PSML", String.class, String.class).get("k"));
    assertEquals("ehcache2", ehcache2.getEhcache("PSML").get("k").getObjectValue());

    registry.cache("ehcache2", "two", "PSML", CacheCapability.CLEAR).get().clear();
    assertEquals(0, ehcache2.getEhcache("PSML").getSize());
    assertEquals("ehcache3", ehcache3.getCache("PSML", String.class, String.class).get("k"));
    assertEquals(CacheScope.LOCAL, registry.cache("jcache", "caffeine", "PSML").get().scope());
    assertEquals(CacheScope.UNKNOWN, registry.cache("jcache", "ehcache3", "PSML").get().scope());
  }

  /**
   * Two managers with the same URI (different class loaders): the statistics MBeans cannot tell
   * them apart, so their statistics are unavailable rather than another cache's.
   */
  @Test
  void hasNoStatisticsForManagersWithTheSameUri() {
    javax.cache.CacheManager first = jcache(EHCACHE3, "urn:x-bzadmin:same");
    javax.cache.CacheManager second = jcache(CAFFEINE, "urn:x-bzadmin:same");
    JCacheManagers.register("same-uri-1", first);
    JCacheManagers.register("same-uri-2", second);
    first.getCache("PSML", String.class, String.class).get("missing");
    CacheStatistics statistics = CacheRegistry.global().cache("jcache", "same-uri-1", "PSML").get().statistics();
    assertEquals(Boolean.TRUE, statistics.enabled());
    assertTrue(statistics.metrics().isEmpty());
  }

  private javax.cache.CacheManager jcache(String provider, String uri) {
    javax.cache.CacheManager manager = EHCACHE3.equals(provider) ? JCacheEhcache3Test.create(URI.create(uri))
        : Caching.getCachingProvider(provider).getCacheManager(URI.create(uri), getClass().getClassLoader());
    this.closing.add(manager::close);
    manager.createCache("PSML", new MutableConfiguration<String, String>().setTypes(String.class, String.class).setStatisticsEnabled(true));
    return manager;
  }

}
