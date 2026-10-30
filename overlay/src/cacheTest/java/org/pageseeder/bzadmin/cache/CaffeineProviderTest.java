package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.jspecify.annotations.Nullable;

/**
 * The native Caffeine adapter: the contract, host registration, statistics from {@code stats()},
 * and keys listed without access. A manager of the contract is the identifier the caches are
 * registered under.
 */
final class CaffeineProviderTest extends ProviderContract<String> {

  private final Map<String, Map<String, Cache<String, String>>> caches = new HashMap<>();

  @Override
  String providerId() {
    return CaffeineProvider.ID;
  }

  @Override
  CacheRegistry registry() {
    return new CacheRegistry(CacheProviders::detected);
  }

  @Override
  String createManager(String name, String... names) {
    Map<String, Cache<String, String>> registered = new LinkedHashMap<>();
    for (String cache : names) {
      Cache<String, String> built = Caffeine.newBuilder().maximumSize(100).recordStats().build();
      CaffeineCaches.register(name, cache, String.class, built);
      registered.put(cache, built);
    }
    this.caches.put(name, registered);
    return name;
  }

  @Override
  String managerId(String manager) {
    return manager;
  }

  @Override
  void put(String manager, String cache, String key, String value) {
    this.caches.get(manager).get(cache).put(key, value);
  }

  @Override
  @Nullable String get(String manager, String cache, String key) {
    return this.caches.get(manager).get(cache).getIfPresent(key);
  }

  @Override
  boolean hasCache(String manager, String cache) {
    return CaffeineCaches.cacheNames(manager).contains(cache);
  }

  @Override
  void close(String manager) {
    CaffeineCaches.unregister(manager);
  }

  @Test
  @EnabledIfSystemProperty(named = "bzadmin.cache.libraries", matches = "Caffeine")
  void isDetectedWithoutTheJCacheApi() {
    assertFalse(CacheProviders.isPresent("javax.cache.CacheManager"));
    List<String> ids = CacheProviders.detected().stream().map(CacheAdminProvider::id).collect(Collectors.toList());
    assertEquals(List.of(CaffeineProvider.ID), ids);
    assertTrue(registry().find(CaffeineProvider.ID).label().startsWith("Caffeine 3."));
  }

  @Test
  void requiresExplicitRegistration() {
    manager("registration", "PSML");
    Cache<String, String> psml = this.caches.get("registration").get("PSML");
    Cache<String, String> other = Caffeine.newBuilder().build();
    assertThrows(IllegalArgumentException.class, () -> CaffeineCaches.register("registration", "PSML", String.class, other), "Name used");
    assertThrows(IllegalArgumentException.class, () -> CaffeineCaches.register("again", "PSML", String.class, psml), "Cache registered");
    assertThrows(IllegalArgumentException.class, () -> CaffeineCaches.register("", "PSML", String.class, other));
    assertThrows(IllegalArgumentException.class, () -> CaffeineCaches.register("a\nb", "PSML", String.class, other));
    assertThrows(IllegalArgumentException.class, () -> CaffeineCaches.register("registration", "", String.class, other));
    assertThrows(NullPointerException.class, () -> CaffeineCaches.register("registration", "none", String.class, null));
    assertEquals(List.of("registration"), managerIds(), "A cache that the host did not register is not listed");

    assertTrue(CaffeineCaches.unregister("registration", "PSML"));
    assertEquals("v", putAndGet(psml), "Unregistering does not clear");
    assertEquals(List.of(), managerIds(), "A manager without cache is not listed");
    assertFalse(CaffeineCaches.unregister("registration", "PSML"));
    assertFalse(CaffeineCaches.unregister("registration"));
  }

  @Test
  void readsTheStatistics() {
    manager("statistics", "PSML");
    Cache<String, String> cache = this.caches.get("statistics").get("PSML");
    cache.put("a", "1");
    cache.put("b", "2");
    cache.getIfPresent("a");
    cache.getIfPresent("missing");
    cache.invalidate("b");
    CacheAdminCache admin = registry().cache(CaffeineProvider.ID, "statistics", "PSML").get();
    CacheStatistics statistics = admin.statistics();
    assertEquals(Boolean.TRUE, statistics.enabled());
    assertEquals(1L, statistics.get(CacheMetric.HITS));
    assertEquals(1L, statistics.get(CacheMetric.MISSES));
    assertEquals(0L, statistics.get(CacheMetric.EVICTIONS));
    assertEquals(1L, statistics.get(CacheMetric.ENTRIES));
    assertNull(statistics.get(CacheMetric.PUTS), "Not counted by Caffeine: unavailable, not zero");
    assertNull(statistics.get(CacheMetric.REMOVALS));
    assertTrue(statistics.timings().isEmpty());
    assertFalse(admin.capabilities().contains(CacheCapability.STATISTICS_TOGGLE), "Set when the cache is built");
    assertThrows(UnsupportedOperationException.class, () -> admin.setStatisticsEnabled(false));

    Cache<String, String> unrecorded = Caffeine.newBuilder().build();
    CaffeineCaches.register("statistics", "unrecorded", String.class, unrecorded);
    unrecorded.put("a", "1");
    unrecorded.getIfPresent("a");
    CacheAdminCache off = registry().cache(CaffeineProvider.ID, "statistics", "unrecorded").get();
    assertEquals(Boolean.FALSE, off.statistics().enabled());
    assertEquals(Set.of(CacheMetric.ENTRIES), off.statistics().metrics().keySet(), "Counted whether statistics are recorded or not");
    assertFalse(off.capabilities().contains(CacheCapability.STATISTICS_RESET), "Nothing to reset");
    assertThrows(UnsupportedOperationException.class, off::resetStatistics);
  }

  @Test
  void resetsTheStatistics() {
    manager("reset", "PSML");
    Cache<String, String> cache = this.caches.get("reset").get("PSML");
    cache.put("a", "1");
    cache.getIfPresent("a");
    CacheAdminCache admin = registry().cache(CaffeineProvider.ID, "reset", "PSML").get();
    assertTrue(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
    assertEquals(1L, admin.statistics().get(CacheMetric.HITS));
    admin.resetStatistics();
    assertEquals(0L, admin.statistics().get(CacheMetric.HITS));
    assertEquals(1L, admin.statistics().get(CacheMetric.ENTRIES), "The entries are kept");
    cache.getIfPresent("a");
    assertEquals(1L, registry().cache(CaffeineProvider.ID, "reset", "PSML").get().statistics().get(CacheMetric.HITS),
        "Since the reset, on every request");
    assertEquals(2L, cache.stats().hitCount(), "Caffeine's own counters are unchanged");
  }

  /**
   * Listing the keys records no statistic and no access: the time since the last access of an entry
   * (expiry after access, and the order of eviction) is the same before and after.
   */
  @Test
  void listsKeysWithoutAccess() {
    AtomicLong nanos = new AtomicLong();
    Cache<String, String> cache = Caffeine.newBuilder().maximumSize(100).expireAfterAccess(Duration.ofHours(1))
        .ticker(nanos::get).executor(Runnable::run).recordStats().build();
    CaffeineCaches.register("entries", "PSML", String.class, cache);
    manager("entries");
    cache.put("/a.psml", "a");
    cache.put("/b.psml", "b");
    nanos.addAndGet(TimeUnit.MINUTES.toNanos(10));
    CacheAdminCache admin = registry().cache(CaffeineProvider.ID, "entries", "PSML").get();
    assertTrue(admin.capabilities().contains(CacheCapability.ENTRIES));

    CacheEntries entries = admin.entries(0, 100);
    Set<String> keys = entries.keys().stream().map(CacheKey::text).collect(Collectors.toSet());
    assertEquals(Set.of("/a.psml", "/b.psml"), keys);
    assertFalse(entries.more());
    assertEquals(0L, cache.stats().requestCount(), "No hit or miss recorded");
    assertEquals(Duration.ofMinutes(10), cache.policy().expireAfterAccess().orElseThrow().ageOf("/a.psml").orElseThrow(), "Not accessed");
    assertEquals(1, admin.entries(1, 100).keys().size(), "A range of the keys");
  }

  @Test
  void removesTypedKeys() {
    manager("remove", "PSML");
    put("remove", "PSML", "/index.psml", "v");
    Cache<Long, String> longs = Caffeine.newBuilder().build();
    longs.put(42L, "v");
    CaffeineCaches.register("remove", "long-keys", Long.class, longs);
    Cache<UUID, String> uuids = Caffeine.newBuilder().build();
    uuids.put(UUID.randomUUID(), "v");
    CaffeineCaches.register("remove", "uuid-keys", UUID.class, uuids);
    Cache<Object, String> objects = Caffeine.newBuilder().build();
    objects.put(7, "v");
    CaffeineCaches.register("remove", "object-keys", Object.class, objects);
    CacheRegistry registry = registry();

    CacheAdminCache cache = registry.cache(CaffeineProvider.ID, "remove", "PSML").get();
    assertTrue(cache.capabilities().contains(CacheCapability.REMOVE_ENTRY));
    assertTrue(cache.remove(CacheKey.parse("string", "/index.psml")));
    assertNull(get("remove", "PSML", "/index.psml"));
    assertFalse(cache.remove(CacheKey.parse("string", "/index.psml")));
    CacheKey longKey = CacheKey.parse("long", "42");
    assertThrows(IllegalArgumentException.class, () -> cache.remove(longKey), "Not a key of this cache");

    CacheAdminCache longKeys = registry.cache(CaffeineProvider.ID, "remove", "long-keys").get();
    CacheKey integerKey = CacheKey.parse("integer", "42");
    assertThrows(IllegalArgumentException.class, () -> longKeys.remove(integerKey));
    assertTrue(longKeys.remove(CacheKey.parse("long", "42")));
    assertEquals(0L, longs.estimatedSize());

    CacheAdminCache uuidKeys = registry.cache(CaffeineProvider.ID, "remove", "uuid-keys").get();
    assertFalse(uuidKeys.capabilities().contains(CacheCapability.REMOVE_ENTRY));
    assertEquals(Map.of(UUID.class.getName(), 1), uuidKeys.entries(0, 100).unsupported(), "Counted by class, never as text");
    assertEquals(CacheProblem.UNSUPPORTED, registry.cache(CaffeineProvider.ID, "remove", "uuid-keys", CacheCapability.REMOVE_ENTRY).problem());

    CacheAdminCache objectKeys = registry.cache(CaffeineProvider.ID, "remove", "object-keys").get();
    assertFalse(objectKeys.remove(CacheKey.parse("long", "7")), "Typed: the long 7 is not the integer 7");
    assertTrue(objectKeys.remove(CacheKey.parse("integer", "7")));
  }

  @Test
  void showsTheConfigurationAndTiers() {
    Cache<String, byte[]> weighted = Caffeine.newBuilder().maximumWeight(1024).weigher((String key, byte[] value) -> value.length)
        .expireAfterWrite(Duration.ofMinutes(5)).build();
    CaffeineCaches.register("configuration", "weighted", String.class, weighted);
    manager("configuration", "PSML");
    weighted.put("a", new byte[10]);
    // Weight accounting is maintained asynchronously; settle the fixture before observing it.
    weighted.cleanUp();
    CacheRegistry registry = registry();

    Map<String, String> config = configuration(registry.cache(CaffeineProvider.ID, "configuration", "PSML").get());
    assertEquals("java.lang.String", config.get("keyType"));
    assertEquals("100", config.get("maximumSize"));
    assertEquals("true", config.get("recordStats"));
    CacheAdminCache psml = registry.cache(CaffeineProvider.ID, "configuration", "PSML").get();
    assertEquals(100L, psml.tiers().get(0).maxEntries());
    assertEquals(100L, psml.entryLimit());
    assertEquals(CacheScope.LOCAL, psml.scope());

    CacheAdminCache cache = registry.cache(CaffeineProvider.ID, "configuration", "weighted").get();
    Map<String, String> weights = configuration(cache);
    assertEquals("1024", weights.get("maximumWeight"));
    assertEquals("10", weights.get("weightedSize"));
    assertEquals("PT5M", weights.get("expireAfterWrite"));
    assertEquals("false", weights.get("recordStats"));
    assertEquals(1024L, cache.tiers().get(0).maxWeight());
    assertNull(cache.entryLimit(), "Limited by weight");
  }

  /**
   * An operation on a cache that the host unregistered after it was looked up fails, as on a
   * closed cache.
   */
  @Test
  void failsOnUnregisteredCaches() {
    manager("unregistered", "PSML");
    CacheAdminCache cache = registry().cache(CaffeineProvider.ID, "unregistered", "PSML").get();
    CaffeineCaches.unregister("unregistered", "PSML");
    assertEquals(CacheProblem.UNKNOWN_MANAGER, registry().cache(CaffeineProvider.ID, "unregistered", "PSML").problem());
    assertThrows(IllegalStateException.class, () -> cache.entries(0, 10));
    CacheKey key = CacheKey.parse("string", "k");
    assertThrows(IllegalStateException.class, () -> cache.remove(key));
    assertThrows(IllegalStateException.class, cache::resetStatistics);
  }

  private static Map<String, String> configuration(CacheAdminCache cache) {
    Map<String, String> config = new HashMap<>();
    for (CacheProperty property : cache.configuration()) config.put(property.name(), property.value());
    return config;
  }

  private static String putAndGet(Cache<String, String> cache) {
    cache.put("k", "v");
    return cache.getIfPresent("k");
  }

  private List<String> managerIds() {
    return registry().find(CaffeineProvider.ID).managers().stream().map(CacheAdminManager::id).collect(Collectors.toList());
  }

}
