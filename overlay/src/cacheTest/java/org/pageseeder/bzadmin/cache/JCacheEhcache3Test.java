package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.cache.CacheManager;
import javax.cache.Caching;

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
 * The JCache adapter with EHCache 3.
 */
final class JCacheEhcache3Test extends JCacheContract {

  static final String PROVIDER = "org.ehcache.jsr107.EhcacheCachingProvider";

  @Override
  String cachingProvider() {
    return PROVIDER;
  }

  @Override
  boolean countsEntries() {
    return false;
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
    } finally {
      JCacheManagers.unregister("ehcache3-pools");
      manager.close();
    }
  }

  /**
   * A cache made from the configuration of EHCache 3 has no complete JCache configuration: whether
   * statistics are enabled comes from the statistics MBean, registered exactly while they are.
   */
  @Test
  void tellsWhetherStatisticsAreEnabledWithoutCompleteConfiguration() {
    CacheManager manager = manager("native");
    manager.createCache("native", Eh107Configuration.fromEhcacheCacheConfiguration(
        CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, byte[].class, ResourcePoolsBuilder.heap(10))));
    CacheAdminCache admin = registry().cache(JCacheProvider.ID, "native", "native").get();
    assertEquals(Boolean.TRUE, admin.statistics().enabled(), "EHCache 3 enables them for such a cache");
    manager.enableStatistics("native", false);
    assertEquals(Boolean.FALSE, admin.statistics().enabled());
    assertFalse(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
    admin.setStatisticsEnabled(true);
    assertEquals(Boolean.TRUE, admin.statistics().enabled());
    assertTrue(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
  }

}
