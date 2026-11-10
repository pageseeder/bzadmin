package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.core.config.DefaultConfiguration;
import org.ehcache.jsr107.Eh107Configuration;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The JCache adapter with EHCache 3: statistics from the statistics service of the manager.
 */
final class JCacheEhcache3Test extends JCacheContract {

  static final String PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";

  @Override
  String cachingProvider() {
    return PROVIDER;
  }

  @Override
  boolean countsEntries() {
    return true;
  }

  @Override
  boolean collectsStatistics() {
    return true;
  }

  @Override
  CacheManager newManager(URI uri) {
    return create(uri);
  }

  /**
   * EHCache 3 reads the URI of a manager as the location of its XML configuration: a programmatic
   * configuration instead.
   */
  static CacheManager create(URI uri) {
    EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider(PROVIDER);
    return provider.getCacheManager(uri, new DefaultConfiguration(JCacheEhcache3Test.class.getClassLoader()));
  }

  /** The resource pools of EHCache 3, through unwrap, with the persistence directory of the manager. */
  @Test
  void readsTheResourcePools(@TempDir Path directory) {
    EhcacheCachingProvider provider = (EhcacheCachingProvider) Caching.getCachingProvider(PROVIDER);
    CacheManager manager = provider.getCacheManager(URI.create("urn:x-bzadmin:ehcache3-pools"),
        new DefaultConfiguration(getClass().getClassLoader(), new DefaultPersistenceConfiguration(directory.toFile())));
    try {
      manager.createCache("tiered", Eh107Configuration.fromEhcacheCacheConfiguration(CacheConfigurationBuilder.newCacheConfigurationBuilder(
          String.class, String.class, ResourcePoolsBuilder.heap(100).offheap(2, MemoryUnit.MB).disk(8, MemoryUnit.MB, true))));
      manager.createCache("heap", Eh107Configuration.fromEhcacheCacheConfiguration(
          CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class, ResourcePoolsBuilder.heap(10))));
      JCacheManagers.register("ehcache3-pools", manager);
      CacheRegistry registry = registry();

      CacheAdminCache tiered = registry.cache(JCacheProvider.ID, "ehcache3-pools", "tiered").get();
      List<CacheTier> tiers = tiered.tiers();
      assertEquals(List.of(CacheTier.Type.HEAP, CacheTier.Type.OFF_HEAP, CacheTier.Type.DISK), tiers.stream().map(CacheTier::type).collect(Collectors.toList()));
      assertEquals(100L, tiers.get(0).maxEntries());
      assertEquals(2L << 20, tiers.get(1).maxBytes());
      assertEquals(8L << 20, tiers.get(2).maxBytes());
      assertTrue(tiers.get(2).isPersistent());
      assertEquals(directory.toFile().getAbsoluteFile().toPath(), tiers.get(2).path().toAbsolutePath());
      assertNull(tiered.entryLimit(), "Off-heap and disk are in bytes");
      Map<String, String> config = new HashMap<>();
      for (CacheProperty property : tiered.configuration()) config.put(property.name(), property.value());
      assertEquals("100 entries", config.get("heap"));
      assertEquals("8 MB (persistent)", config.get("disk"));
      assertEquals("none", config.get("expiryPolicy"));

      assertEquals(10L, registry.cache(JCacheProvider.ID, "ehcache3-pools", "heap").get().entryLimit());

      // Entries and sizes per tier: puts go to the lowest tier, which holds every entry
      Cache<String, String> entries = manager.getCache("tiered", String.class, String.class);
      for (int i = 0; i < 5; i++) entries.put("k" + i, "value " + i);
      CacheStatistics statistics = tiered.statistics();
      assertEquals(5L, statistics.get(CacheMetric.ENTRIES));
      assertEquals(5L, statistics.get(CacheMetric.DISK_ENTRIES));
      assertNotNull(statistics.get(CacheMetric.HEAP_ENTRIES));
      assertNotNull(statistics.get(CacheMetric.OFF_HEAP_ENTRIES));
      assertTrue(statistics.get(CacheMetric.DISK_BYTES) > 0, statistics.metrics().toString());
      assertNotNull(statistics.get(CacheMetric.OFF_HEAP_BYTES));
      assertNull(statistics.get(CacheMetric.HEAP_BYTES), "The heap is limited in entries: its bytes are not tracked");

      manager.createCache("heap-bytes", Eh107Configuration.fromEhcacheCacheConfiguration(CacheConfigurationBuilder.newCacheConfigurationBuilder(
          String.class, String.class, ResourcePoolsBuilder.newResourcePoolsBuilder().heap(1, MemoryUnit.MB))));
      manager.getCache("heap-bytes", String.class, String.class).put("k", "value");
      CacheStatistics heap = registry.cache(JCacheProvider.ID, "ehcache3-pools", "heap-bytes").get().statistics();
      assertEquals(1L, heap.get(CacheMetric.ENTRIES));
      assertTrue(heap.get(CacheMetric.HEAP_BYTES) > 0, "Tracked on a heap limited in bytes");
    } finally {
      JCacheManagers.unregister("ehcache3-pools");
      manager.close();
    }
  }

  /**
   * The statistics service of the manager: always on, with the entries, without reading an entry;
   * the statistics MBean only adds the average times, while it is registered.
   */
  @Test
  void readsTheStatisticsService() {
    CacheManager manager = manager("service", "PSML");
    Cache<String, String> cache = manager.getCache("PSML", String.class, String.class);
    cache.put("a", "1");
    cache.put("b", "2");
    cache.get("a");
    cache.get("missing");
    cache.remove("b");
    CacheAdminCache admin = registry().cache(JCacheProvider.ID, "service", "PSML").get();
    assertTrue(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
    assertFalse(admin.capabilities().contains(CacheCapability.STATISTICS_TOGGLE), "Always collected");
    CacheStatistics statistics = admin.statistics();
    assertNull(statistics.enabled(), "Always on");
    assertEquals(1L, statistics.get(CacheMetric.HITS));
    assertEquals(1L, statistics.get(CacheMetric.MISSES));
    assertEquals(2L, statistics.get(CacheMetric.PUTS));
    assertEquals(1L, statistics.get(CacheMetric.REMOVALS));
    assertEquals(0L, statistics.get(CacheMetric.EVICTIONS));
    assertEquals(0L, statistics.get(CacheMetric.EXPIRATIONS));
    assertEquals(1L, statistics.get(CacheMetric.ENTRIES));
    assertEquals(1L, statistics.get(CacheMetric.HEAP_ENTRIES));
    assertEquals(Set.of(CacheTiming.values()), statistics.timings().keySet(), "From the MBean");
    assertEquals(statistics.metrics(), admin.statistics().metrics(), "Reading changes nothing");

    manager.enableStatistics("PSML", false);
    CacheStatistics unregistered = admin.statistics();
    assertNull(unregistered.enabled());
    assertEquals(1L, unregistered.get(CacheMetric.HITS), "Still collected");
    assertTrue(unregistered.timings().isEmpty(), "No MBean");
    assertThrows(UnsupportedOperationException.class, () -> admin.setStatisticsEnabled(true));
  }

  /**
   * EHCache 3 counts even for a cache made without JCache statistics.
   */
  @Test
  void countsWithoutJCacheStatistics() {
    CacheManager manager = manager("not-enabled");
    manager.createCache("off", new MutableConfiguration<String, String>().setTypes(String.class, String.class));
    manager.getCache("off", String.class, String.class).get("missing");
    CacheStatistics statistics = registry().cache(JCacheProvider.ID, "not-enabled", "off").get().statistics();
    assertNull(statistics.enabled());
    assertEquals(1L, statistics.get(CacheMetric.MISSES));
    assertEquals(0L, statistics.get(CacheMetric.ENTRIES));
  }

  /**
   * The reset of the statistics service, without the MBean: the counters, including the evictions
   * of the lowest tier, start again from zero; the entries are kept.
   */
  @Test
  void resetsTheStatisticsWithoutTheMBean() {
    CacheManager manager = manager("reset-service");
    manager.createCache("small", Eh107Configuration.fromEhcacheCacheConfiguration(
        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class, ResourcePoolsBuilder.heap(2))));
    manager.enableStatistics("small", false);
    Cache<String, String> cache = manager.getCache("small", String.class, String.class);
    for (int i = 0; i < 10; i++) cache.put("k" + i, "v");
    CacheAdminCache admin = registry().cache(JCacheProvider.ID, "reset-service", "small").get();
    CacheStatistics before = admin.statistics();
    assertEquals(10L, before.get(CacheMetric.PUTS));
    assertTrue(before.get(CacheMetric.EVICTIONS) > 0, before.metrics().toString());
    admin.resetStatistics();
    CacheStatistics after = admin.statistics();
    assertEquals(0L, after.get(CacheMetric.PUTS));
    assertEquals(0L, after.get(CacheMetric.EVICTIONS));
    assertEquals(before.get(CacheMetric.ENTRIES), after.get(CacheMetric.ENTRIES), "The entries are kept");
  }

  /**
   * A manager whose statistics service cannot be read (here, a host's wrapper of the manager):
   * the statistics MBean, as for other implementations.
   */
  @Test
  void readsTheMBeanWithoutTheStatisticsService() {
    CacheManager manager = manager("direct", "PSML");
    JCacheManagers.unregister("direct");
    CacheManager wrapper = (CacheManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {CacheManager.class},
        (proxy, method, args) -> {
          try {
            return method.invoke(manager, args);
          } catch (InvocationTargetException ex) {
            throw ex.getCause();
          }
        });
    JCacheManagers.register("wrapper", wrapper);
    try {
      manager.getCache("PSML", String.class, String.class).get("missing");
      CacheAdminCache admin = registry().cache(JCacheProvider.ID, "wrapper", "PSML").get();
      assertTrue(admin.capabilities().contains(CacheCapability.STATISTICS_TOGGLE));
      CacheStatistics statistics = admin.statistics();
      assertEquals(Boolean.TRUE, statistics.enabled());
      assertEquals(1L, statistics.get(CacheMetric.MISSES));
      assertNull(statistics.get(CacheMetric.ENTRIES), "No constant-time size without the service");
      manager.enableStatistics("PSML", false);
      assertEquals(Boolean.FALSE, admin.statistics().enabled());
      assertFalse(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
    } finally {
      JCacheManagers.unregister("wrapper");
    }
  }

  /**
   * The adapter reads the statistics service of the manager, and never creates one.
   */
  @Test
  void neverCreatesAStatisticsService() throws IOException {
    try (InputStream in = Ehcache3Native.class.getResourceAsStream("Ehcache3Native.class")) {
      String constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
      assertTrue(constants.contains("org/ehcache/core/spi/service/StatisticsService"));
      assertFalse(constants.contains("DefaultStatisticsService"));
      assertFalse(constants.contains("registerDerivedStatistic"));
    }
  }

}
