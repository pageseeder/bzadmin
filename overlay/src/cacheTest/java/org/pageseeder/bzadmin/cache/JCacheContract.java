package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

import org.junit.jupiter.api.Test;

import org.jspecify.annotations.Nullable;

/**
 * The JCache adapter with one implementation: the contract, host registration, statistics through
 * the statistics MBean, and no manager or cache created or closed by the admin code.
 */
abstract class JCacheContract extends ProviderContract<CacheManager> {

  private static final String REGISTRATION = "registration";
  private static final String CLOSED = "closed";
  private static final String REMOVE = "remove";
  private static final String LONG_KEYS = "long-keys";
  private static final String WRITE_THROUGH = "write-through";
  private static final String INDEX = "/index.psml";
  private static final String LIFECYCLE = "lifecycle";
  private static final String OTHER = "other";

  private final Map<CacheManager, String> ids = new IdentityHashMap<>();

  /**
   * @return the class of the caching provider under test.
   */
  abstract String cachingProvider();

  /**
   * @return whether the implementation counts the entries in constant time.
   */
  abstract boolean countsEntries();

  /**
   * Creates a cache manager as the host would.
   *
   * @param uri The URI of the manager.
   * @return the manager.
   */
  CacheManager newManager(URI uri) {
    return Caching.getCachingProvider(cachingProvider()).getCacheManager(uri, getClass().getClassLoader());
  }

  @Override
  final String providerId() {
    return JCacheProvider.ID;
  }

  @Override
  final CacheRegistry registry() {
    return new CacheRegistry(CacheProviders::detected);
  }

  @Override
  final CacheManager createManager(String name, String... caches) {
    CacheManager manager = newManager(URI.create("urn:x-bzadmin:" + getClass().getSimpleName() + ":" + name));
    for (String cache : caches) {
      manager.createCache(cache, new MutableConfiguration<String, String>().setTypes(String.class, String.class).setStatisticsEnabled(true));
    }
    JCacheManagers.register(name, manager);
    this.ids.put(manager, name);
    return manager;
  }

  @Override
  final String managerId(CacheManager manager) {
    return this.ids.get(manager);
  }

  @Override
  final void put(CacheManager manager, String cache, String key, String value) {
    manager.getCache(cache, String.class, String.class).put(key, value);
  }

  @Override
  final @Nullable String get(CacheManager manager, String cache, String key) {
    return manager.getCache(cache, String.class, String.class).get(key);
  }

  @Override
  final boolean hasCache(CacheManager manager, String cache) {
    for (String name : manager.getCacheNames()) if (name.equals(cache)) return true;
    return false;
  }

  @Override
  final void close(CacheManager manager) {
    JCacheManagers.unregister(this.ids.get(manager));
    manager.close();
  }

  @Test
  void requiresExplicitRegistration() {
    CacheManager manager = manager(REGISTRATION, "PSML");
    assertThrows(IllegalArgumentException.class, () -> JCacheManagers.register(REGISTRATION, manager));
    assertThrows(IllegalArgumentException.class, () -> JCacheManagers.register("again", manager));
    assertThrows(IllegalArgumentException.class, () -> JCacheManagers.register("", manager));
    assertThrows(IllegalArgumentException.class, () -> JCacheManagers.register("a\nb", manager));
    assertThrows(NullPointerException.class, () -> JCacheManagers.register("none", null));
    // A manager that the host did not register is not listed
    CacheManager unregistered = newManager(URI.create("urn:x-bzadmin:unregistered"));
    try {
      unregistered.createCache("PSML", new MutableConfiguration<>());
      assertEquals(List.of(REGISTRATION), managerIds());
    } finally {
      unregistered.close();
    }
    assertTrue(JCacheManagers.unregister(REGISTRATION));
    assertFalse(manager.isClosed(), "Unregistering does not close");
    assertEquals(List.of(), managerIds());
    assertFalse(JCacheManagers.unregister(REGISTRATION));
    JCacheManagers.register(REGISTRATION, manager);
  }

  @Test
  void readsTheStatisticsMBean() {
    CacheManager manager = manager("statistics", "PSML");
    Cache<String, String> cache = manager.getCache("PSML", String.class, String.class);
    cache.put("a", "1");
    cache.put("b", "2");
    cache.get("a");
    cache.get("missing");
    cache.remove("b");
    CacheAdminCache admin = registry().cache(JCacheProvider.ID, "statistics", "PSML").get();
    CacheStatistics statistics = admin.statistics();
    assertEquals(Boolean.TRUE, statistics.enabled());
    assertEquals(1L, statistics.get(CacheMetric.HITS));
    assertEquals(1L, statistics.get(CacheMetric.MISSES));
    assertEquals(2L, statistics.get(CacheMetric.PUTS));
    assertEquals(1L, statistics.get(CacheMetric.REMOVALS));
    assertNotNull(statistics.get(CacheMetric.EVICTIONS));
    if (countsEntries()) assertEquals(1L, statistics.get(CacheMetric.ENTRIES));
    else assertNull(statistics.get(CacheMetric.ENTRIES), "No constant-time size");
    assertEquals(Set.of(CacheTiming.values()), statistics.timings().keySet(), "Read with the counters");

    admin.setStatisticsEnabled(false);
    CacheStatistics disabled = admin.statistics();
    assertEquals(Boolean.FALSE, disabled.enabled());
    assertEquals(countsEntries() ? Set.of(CacheMetric.ENTRIES) : Set.of(), disabled.metrics().keySet(), "Unavailable, not zero");
    assertTrue(disabled.timings().isEmpty());
    admin.setStatisticsEnabled(true);
    assertEquals(Boolean.TRUE, admin.statistics().enabled());
  }

  @Test
  void resetsTheStatistics() {
    CacheManager manager = manager("reset", "PSML");
    Cache<String, String> cache = manager.getCache("PSML", String.class, String.class);
    cache.put("a", "1");
    cache.get("a");
    CacheAdminCache admin = registry().cache(JCacheProvider.ID, "reset", "PSML").get();
    assertTrue(admin.capabilities().contains(CacheCapability.STATISTICS_RESET));
    assertEquals(1L, admin.statistics().get(CacheMetric.HITS));
    admin.resetStatistics();
    CacheStatistics reset = admin.statistics();
    assertEquals(0L, reset.get(CacheMetric.HITS));
    assertEquals(0L, reset.get(CacheMetric.PUTS));
    assertTrue(cache.containsKey("a"), "The entries are kept");

    admin.setStatisticsEnabled(false);
    assertFalse(admin.capabilities().contains(CacheCapability.STATISTICS_RESET), "Nothing to reset while disabled");
    assertThrows(UnsupportedOperationException.class, admin::resetStatistics);
  }

  @Test
  void showsTheConfiguration() {
    manager("configuration", "PSML");
    Map<String, String> config = new HashMap<>();
    for (CacheProperty property : registry().cache(JCacheProvider.ID, "configuration", "PSML").get().configuration()) {
      config.put(property.name(), property.value());
    }
    assertEquals("java.lang.String", config.get("keyType"));
    assertEquals("java.lang.String", config.get("valueType"));
    assertEquals("false", config.get("readThrough"));
    assertEquals("false", config.get("writeThrough"));
    assertEquals("true", config.get("statisticsEnabled"));
    assertEquals("0", config.get("entryListeners"));
  }

  @Test
  void showsArrayTypesAsInJava() {
    CacheManager manager = manager("array-types");
    manager.createCache("bytes", new MutableConfiguration<String, byte[]>().setTypes(String.class, byte[].class));
    Map<String, String> config = new HashMap<>();
    for (CacheProperty property : registry().cache(JCacheProvider.ID, "array-types", "bytes").get().configuration()) {
      config.put(property.name(), property.value());
    }
    assertEquals("byte[]", config.get("valueType"), "Not the binary name [B");
  }

  @Test
  void listsClosedManagersUntilUnregistered() {
    CacheManager manager = manager(CLOSED, "PSML");
    manager.close();
    CacheRegistry registry = registry();
    assertTrue(registry.manager(JCacheProvider.ID, CLOSED).get().isClosed());
    assertEquals(CacheProblem.CLOSED, registry.cache(JCacheProvider.ID, CLOSED, "PSML").problem());
    assertTrue(output(registry).contains("status=\"closed\""));
  }

  @Test
  void removesTypedKeysWithoutListingThem() {
    CacheManager manager = manager(REMOVE, "PSML");
    manager.createCache(LONG_KEYS, new MutableConfiguration<Long, String>().setTypes(Long.class, String.class));
    manager.createCache(WRITE_THROUGH, new MutableConfiguration<String, String>().setTypes(String.class, String.class)
        .setWriteThrough(true).setCacheWriterFactory(javax.cache.configuration.FactoryBuilder.factoryOf(NoWriter.class)));
    manager.createCache("object-keys", new MutableConfiguration<java.util.UUID, String>().setTypes(java.util.UUID.class, String.class));
    put(manager, "PSML", INDEX, "v");
    manager.getCache(LONG_KEYS, Long.class, String.class).put(42L, "v");
    CacheRegistry registry = registry();

    CacheAdminCache cache = registry.cache(JCacheProvider.ID, REMOVE, "PSML").get();
    assertTrue(cache.capabilities().contains(CacheCapability.REMOVE_ENTRY));
    assertFalse(cache.capabilities().contains(CacheCapability.ENTRIES), "Iterating reads every entry as a get");
    assertTrue(cache.remove(CacheKey.parse("string", INDEX)));
    assertNull(get(manager, "PSML", INDEX));
    assertFalse(cache.remove(CacheKey.parse("string", INDEX)));
    CacheKey longKey = CacheKey.parse("long", "42");
    assertThrows(IllegalArgumentException.class, () -> cache.remove(longKey), "Not a key of this cache");

    CacheAdminCache longs = registry.cache(JCacheProvider.ID, REMOVE, LONG_KEYS).get();
    CacheKey integerKey = CacheKey.parse("integer", "42");
    assertThrows(IllegalArgumentException.class, () -> longs.remove(integerKey));
    assertTrue(longs.remove(CacheKey.parse("long", "42")));

    assertFalse(registry.cache(JCacheProvider.ID, REMOVE, WRITE_THROUGH).get().capabilities().contains(CacheCapability.REMOVE_ENTRY),
        "A removal would write through");
    assertFalse(registry.cache(JCacheProvider.ID, REMOVE, "object-keys").get().capabilities().contains(CacheCapability.REMOVE_ENTRY));
    assertEquals(CacheProblem.UNSUPPORTED, registry.cache(JCacheProvider.ID, REMOVE, WRITE_THROUGH, CacheCapability.REMOVE_ENTRY).problem());
  }

  /**
   * A writer that writes nothing.
   */
  public static final class NoWriter implements javax.cache.integration.CacheWriter<String, String> {
    @Override public void write(Cache.Entry<? extends String, ? extends String> entry) {
      // Nothing to store: the tests only check that removals do not write through
    }
    @Override public void writeAll(java.util.Collection<Cache.Entry<? extends String, ? extends String>> entries) {
      // Nothing to store: the tests only check that removals do not write through
    }
    @Override public void delete(Object key) { throw new AssertionError("Deleted through the writer"); }
    @Override public void deleteAll(java.util.Collection<?> keys) { throw new AssertionError("Deleted through the writer"); }
  }

  /**
   * The admin code leaves the managers and caches of the host as they are: none closed or created
   * by listing, statistics, configuration or clear.
   */
  @Test
  void leavesTheHostLifecycle() {
    CacheManager manager = manager(LIFECYCLE, "PSML", OTHER);
    CacheRegistry registry = registry();
    output(registry);
    for (String name : List.of("PSML", OTHER)) {
      CacheAdminCache cache = registry.cache(JCacheProvider.ID, LIFECYCLE, name).get();
      cache.configuration();
      cache.statistics();
      cache.clear();
    }
    assertFalse(manager.isClosed());
    assertEquals(Set.of("PSML", OTHER), Set.copyOf(registry.manager(JCacheProvider.ID, LIFECYCLE).get().cacheNames()));
    assertFalse(manager.getCache("PSML").isClosed());
  }

  /**
   * The overlay never asks a caching provider for a manager, and never creates, destroys or closes
   * caches: its classes do not refer to {@code Caching} or to these methods.
   */
  @Test
  void neverUsesTheCachingFactories() throws IOException, URISyntaxException {
    Path classes = Paths.get(JCacheProvider.class.getResource("JCacheProvider.class").toURI()).getParent();
    try (Stream<Path> files = Files.list(classes)) {
      for (Path file : (Iterable<Path>) files::iterator) {
        if (!file.toString().endsWith(".class")) continue;
        String constants = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        String name = file.getFileName().toString();
        assertFalse(constants.contains("\u0000\u0013javax/cache/Caching"), name);
        for (String method : List.of("createCache", "destroyCache", "getCacheManager", "enableManagement")) {
          // getCacheManager of a Cache returns the manager that owns it: allowed
          if (method.equals("getCacheManager") && name.startsWith("JCacheProvider")) continue;
          assertFalse(constants.contains(method), name + ": " + method);
        }
      }
    }
  }

  private List<String> managerIds() {
    return registry().find(JCacheProvider.ID).managers().stream().map(CacheAdminManager::id).collect(Collectors.toList());
  }

}
