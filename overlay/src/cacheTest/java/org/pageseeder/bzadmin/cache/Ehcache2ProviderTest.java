package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.MemoryUnit;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.distribution.CacheReplicator;

import org.jspecify.annotations.Nullable;

/**
 * The EHCache 2 adapter with each representative version (2.6.11, 2.10.9.2, 2.11.1.3.11): the
 * contract, and what depends on the version.
 */
@DisabledIfSystemProperty(named = "bzadmin.cache.libraries", matches = "NoCache")
final class Ehcache2ProviderTest extends ProviderContract<CacheManager> {

  private static final boolean LEGACY = "Ehcache26".equals(System.getProperty("bzadmin.cache.libraries"));

  @Override
  String providerId() {
    return Ehcache2Provider.ID;
  }

  @Override
  CacheRegistry registry() {
    return new CacheRegistry(CacheProviders::detected);
  }

  @Override
  CacheManager createManager(String name, String... caches) {
    Configuration config = new Configuration().name(name);
    config.setUpdateCheck(false);
    CacheManager manager = new CacheManager(config);
    for (String cache : caches) {
      CacheConfiguration cacheConfig = new CacheConfiguration(cache, 1000);
      manager.addCache(new net.sf.ehcache.Cache(cacheConfig));
    }
    return manager;
  }

  @Override
  String managerId(CacheManager manager) {
    return manager.getName();
  }

  @Override
  void put(CacheManager manager, String cache, String key, String value) {
    manager.getEhcache(cache).put(new Element(key, value));
  }

  @Override
  @Nullable String get(CacheManager manager, String cache, String key) {
    Element element = manager.getEhcache(cache).get(key);
    return element != null ? (String) element.getObjectValue() : null;
  }

  @Override
  boolean hasCache(CacheManager manager, String cache) {
    return manager.cacheExists(cache);
  }

  @Override
  void close(CacheManager manager) {
    manager.shutdown();
  }

  @Test
  void listsLiveManagersWithoutCreatingOne() {
    assertTrue(CacheManager.ALL_CACHE_MANAGERS.isEmpty());
    CacheRegistry registry = registry();
    assertEquals(List.of(), registry.find(Ehcache2Provider.ID).managers());
    assertEquals(CacheProblem.UNKNOWN_MANAGER, registry.cache(Ehcache2Provider.ID, "__DEFAULT__", "PSML").problem());
    output(registry);
    assertTrue(CacheManager.ALL_CACHE_MANAGERS.isEmpty(), "No default manager created");
    assertTrue(registry.find(Ehcache2Provider.ID).label().startsWith("EHCache 2."), registry.find(Ehcache2Provider.ID).label());
  }

  @Test
  void countsEntriesAndRequests() {
    CacheManager manager = manager("statistics", "PSML");
    Ehcache ehcache = manager.getEhcache("PSML");
    if (LEGACY) ehcache.setStatisticsEnabled(true);
    ehcache.put(new Element("a", "1"));
    ehcache.put(new Element("b", "2"));
    ehcache.get("a");
    ehcache.get("missing");
    ehcache.remove("b");
    CacheAdminCache cache = registry().cache(Ehcache2Provider.ID, "statistics", "PSML").get();
    CacheStatistics statistics = cache.statistics();
    assertEquals(1L, statistics.get(CacheMetric.ENTRIES));
    assertEquals(1L, statistics.get(CacheMetric.HEAP_ENTRIES));
    assertEquals(1L, statistics.get(CacheMetric.HITS));
    assertEquals(1L, statistics.get(CacheMetric.MISSES));
    assertEquals(2L, statistics.get(CacheMetric.PUTS));
    assertEquals(1L, statistics.get(CacheMetric.REMOVALS));
    assertEquals(0.5, statistics.hitRatio());
    assertNull(statistics.get(CacheMetric.HEAP_BYTES), "Never measured");
    assertEquals(LEGACY ? Boolean.TRUE : null, statistics.enabled());
  }

  @Test
  void togglesStatisticsUpTo26() {
    manager("toggle", "PSML");
    CacheAdminCache cache = registry().cache(Ehcache2Provider.ID, "toggle", "PSML").get();
    assertEquals(LEGACY, cache.capabilities().contains(CacheCapability.STATISTICS_TOGGLE));
    if (!LEGACY) return;
    cache.setStatisticsEnabled(false);
    CacheStatistics disabled = cache.statistics();
    assertEquals(Boolean.FALSE, disabled.enabled());
    assertNull(disabled.get(CacheMetric.HITS), "Not collected: unavailable, not zero");
    assertEquals(0L, disabled.get(CacheMetric.ENTRIES));
    cache.setStatisticsEnabled(true);
    assertEquals(Boolean.TRUE, cache.statistics().enabled());
  }

  @Test
  void neverResetsStatistics() {
    manager("reset", "PSML");
    CacheAdminCache cache = registry().cache(Ehcache2Provider.ID, "reset", "PSML").get();
    assertFalse(cache.capabilities().contains(CacheCapability.STATISTICS_RESET), "EHCache 2 is deprecated");
    assertThrows(UnsupportedOperationException.class, cache::resetStatistics);
  }

  @Test
  void showsTheConfiguration() {
    manager("configuration", "PSML");
    Map<String, String> config = new HashMap<>();
    for (CacheProperty property : registry().cache(Ehcache2Provider.ID, "configuration", "PSML").get().configuration()) {
      config.put(property.name(), property.value());
    }
    assertEquals("1000", config.get("maxEntriesLocalHeap"));
    assertEquals("false", config.get("eternal"));
    assertEquals("LRU", config.get("memoryStoreEvictionPolicy"));
    assertEquals("off", config.get("transactionalMode"));
    assertEquals("false", config.get("replicated"));
    assertEquals("STATUS_ALIVE", config.get("status"));
  }

  /** The limit of the tier holding every entry, only when it is a number of entries. */
  @Test
  void readsTheEntryLimit() {
    CacheManager manager = manager("limits", "heap");
    manager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("unbounded", 0)));
    manager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("disk", 10)
        .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.LOCALTEMPSWAP)).maxEntriesLocalDisk(500)));
    manager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration().name("bytes").maxBytesLocalHeap(1, MemoryUnit.MEGABYTES)));
    CacheRegistry registry = registry();
    assertEquals(1000L, registry.cache(Ehcache2Provider.ID, "limits", "heap").get().entryLimit());
    assertNull(registry.cache(Ehcache2Provider.ID, "limits", "unbounded").get().entryLimit());
    assertEquals(500L, registry.cache(Ehcache2Provider.ID, "limits", "disk").get().entryLimit(), "The disk holds every entry");
    assertNull(registry.cache(Ehcache2Provider.ID, "limits", "bytes").get().entryLimit(), "Limited in bytes");

    Configuration pooled = new Configuration().name("pooled").maxBytesLocalHeap(1, MemoryUnit.MEGABYTES);
    pooled.setUpdateCheck(false);
    CacheManager pool = new CacheManager(pooled);
    try {
      pool.addCache(new net.sf.ehcache.Cache(new CacheConfiguration().name("PSML")));
      assertNull(registry.cache(Ehcache2Provider.ID, "pooled", "PSML").get().entryLimit(), "A pool of the manager in bytes");
    } finally {
      pool.shutdown();
    }
  }

  /** Tiers in the order of EHCache 2: heap, then disk; a pool of the manager instead of a cache limit. */
  @Test
  void listsTheTiersAndPools() {
    CacheManager manager = manager("tiers", "heap");
    manager.addCache(new net.sf.ehcache.Cache(new CacheConfiguration("disk", 10)
        .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.LOCALTEMPSWAP)).maxBytesLocalDisk(64, MemoryUnit.MEGABYTES)));
    CacheRegistry registry = registry();
    List<CacheTier> heap = registry.cache(Ehcache2Provider.ID, "tiers", "heap").get().tiers();
    assertEquals(1, heap.size());
    assertEquals(1000L, heap.get(0).maxEntries());
    List<CacheTier> disk = registry.cache(Ehcache2Provider.ID, "tiers", "disk").get().tiers();
    assertEquals(List.of(CacheTier.Type.HEAP, CacheTier.Type.DISK), List.of(disk.get(0).type(), disk.get(1).type()));
    assertEquals(64L << 20, disk.get(1).maxBytes());
    assertFalse(disk.get(1).isPersistent(), "localTempSwap");
    assertEquals(Ehcache2Provider.diskStorePath(manager), disk.get(1).path());
    assertTrue(registry.find(Ehcache2Provider.ID).managers().stream().filter(m -> m.id().equals("tiers")).findFirst().orElseThrow().pools().isEmpty());

    Configuration pooled = new Configuration().name("tier-pool").maxBytesLocalHeap(2, MemoryUnit.MEGABYTES);
    pooled.setUpdateCheck(false);
    CacheManager pool = new CacheManager(pooled);
    try {
      pool.addCache(new net.sf.ehcache.Cache(new CacheConfiguration().name("PSML")));
      CacheTier tier = registry.cache(Ehcache2Provider.ID, "tier-pool", "PSML").get().tiers().get(0);
      assertTrue(tier.isPooled());
      CacheAdminManager admin = registry.manager(Ehcache2Provider.ID, "tier-pool").get();
      assertEquals(2L << 20, admin.pools().get(0).maxBytes());
    } finally {
      pool.shutdown();
    }
  }

  @Test
  // EHCache 2.11.1 removed the replication by listeners (`CacheReplicator`)
  @DisabledIfSystemProperty(named = "bzadmin.cache.libraries", matches = "Ehcache211")
  void labelsReplicatedCaches() {
    CacheManager manager = manager("replicated", "PSML", "local");
    manager.getEhcache("PSML").getCacheEventNotificationService().registerListener(new Replicator());
    CacheRegistry registry = registry();
    assertEquals(CacheScope.CLUSTERED, registry.cache(Ehcache2Provider.ID, "replicated", "PSML").get().scope());
    assertEquals(CacheScope.LOCAL, registry.cache(Ehcache2Provider.ID, "replicated", "local").get().scope());
  }

  @Test
  void clearsOneCacheOnly() {
    CacheManager manager = manager("clear", "PSML", "other");
    put(manager, "PSML", "k", "v");
    put(manager, "other", "k", "v");
    registry().cache(Ehcache2Provider.ID, "clear", "PSML", CacheCapability.CLEAR).get().clear();
    assertEquals(0, manager.getEhcache("PSML").getSize());
    assertEquals(1, manager.getEhcache("other").getSize());
    assertFalse(manager.cacheExists("missing"));
  }

  @Test
  void listsAndRemovesTypedKeys() {
    CacheManager manager = manager("entries", "PSML");
    Ehcache ehcache = manager.getEhcache("PSML");
    ehcache.put(new Element("/index.psml", "1"));
    ehcache.put(new Element(42L, "2"));
    ehcache.put(new Element(UUID.randomUUID(), "3"));
    CacheAdminCache cache = registry().cache(Ehcache2Provider.ID, "entries", "PSML").get();
    assertTrue(cache.capabilities().containsAll(java.util.Set.of(CacheCapability.ENTRIES, CacheCapability.REMOVE_ENTRY)));
    CacheEntries entries = cache.entries(0, 100);
    assertEquals(java.util.Set.of(CacheKey.of("/index.psml"), CacheKey.of(42L)), java.util.Set.copyOf(entries.keys()));
    assertEquals(Map.of(UUID.class.getName(), 1), entries.unsupported());
    assertEquals(3, entries.scanned());
    assertFalse(entries.more());

    assertTrue(cache.remove(CacheKey.parse("long", "42")));
    assertFalse(cache.remove(CacheKey.parse("integer", "42")), "Typed: the integer 42 is another key");
    assertFalse(cache.remove(CacheKey.parse("string", "42")));
    assertNull(ehcache.get(42L));
    assertEquals(2, ehcache.getSize());
  }

  @Test
  void boundsTheKeysRead() {
    CacheManager manager = manager("many", "PSML");
    manager.getEhcache("PSML").getCacheConfiguration().setMaxEntriesLocalHeap(0);
    for (int i = 0; i < CacheEntries.MAX_SCAN + 5; i++) manager.getEhcache("PSML").put(new Element(i, "v"));
    CacheEntries entries = registry().cache(Ehcache2Provider.ID, "many", "PSML").get().entries(CacheEntries.MAX_SCAN - 2, 1000);
    assertEquals(2, entries.keys().size());
    assertEquals(CacheEntries.MAX_SCAN, entries.scanned());
    assertTrue(entries.more());
  }

  /**
   * A replicator that replicates nothing: what RMI, JGroups or JMS replication register.
   */
  private static final class Replicator implements CacheReplicator {

    @Override public boolean isReplicateUpdatesViaCopy() { return true; }
    @Override public boolean notAlive() { return false; }
    @Override public boolean alive() { return true; }
    @Override public void notifyElementRemoved(Ehcache cache, Element element) { /* Replicates nothing */ }
    @Override public void notifyElementPut(Ehcache cache, Element element) { /* Replicates nothing */ }
    @Override public void notifyElementUpdated(Ehcache cache, Element element) { /* Replicates nothing */ }
    @Override public void notifyElementExpired(Ehcache cache, Element element) { /* Replicates nothing */ }
    @Override public void notifyElementEvicted(Ehcache cache, Element element) { /* Replicates nothing */ }
    @Override public void notifyRemoveAll(Ehcache cache) { /* Replicates nothing */ }
    @Override public void dispose() { /* Holds no resources */ }

    // Required by CacheEventListener, which declares clone()
    @SuppressWarnings("java:S2975")
    @Override public Object clone() throws CloneNotSupportedException { return super.clone(); }
  }

}
