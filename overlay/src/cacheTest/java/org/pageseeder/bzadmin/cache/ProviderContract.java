package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.XmlOutputAdapter;

import org.jspecify.annotations.Nullable;

/**
 * The contract tests that every cache adapter passes with a real cache library: the same cache name
 * in two managers, basic administration, no cache created, closed managers.
 *
 * @param <M> The class of the library's cache manager.
 */
abstract class ProviderContract<M> {

  private final List<M> managers = new ArrayList<>();

  /**
   * @return the identifier of the provider under test.
   */
  abstract String providerId();

  /**
   * @return the registry with the provider under test.
   */
  abstract CacheRegistry registry();

  /**
   * Creates a manager of the host, with caches, and makes it known to the provider as the host
   * would.
   *
   * @return the manager.
   */
  abstract M createManager(String name, String... caches);

  /**
   * @return the identifier of the manager in the provider.
   */
  abstract String managerId(M manager);

  abstract void put(M manager, String cache, String key, String value);

  abstract @Nullable String get(M manager, String cache, String key);

  abstract boolean hasCache(M manager, String cache);

  abstract void close(M manager);

  final M manager(String name, String... caches) {
    M manager = createManager(name, caches);
    this.managers.add(manager);
    return manager;
  }

  @AfterEach
  void closeManagers() {
    for (M manager : this.managers) close(manager);
    this.managers.clear();
  }

  @Test
  void isListed() {
    CacheAdminProvider provider = registry().find(providerId());
    assertNotNull(provider);
    assertFalse(provider.label().isEmpty());
  }

  @Test
  void resolvesTheSameNameInEachManager() {
    M first = manager("contract-first", "PSML", "other");
    M second = manager("contract-second", "PSML");
    put(first, "PSML", "k", "first");
    put(second, "PSML", "k", "second");
    CacheRegistry registry = registry();

    CacheAdminCache cache = registry.cache(providerId(), managerId(first), "PSML", CacheCapability.CLEAR).get();
    assertNotNull(cache);
    assertEquals("PSML", cache.name());
    cache.clear();
    assertNull(get(first, "PSML", "k"));
    assertEquals("second", get(second, "PSML", "k"));

    List<String> names = registry.manager(providerId(), managerId(first)).get().cacheNames();
    assertEquals(Set.of("PSML", "other"), Set.copyOf(names));
    String xml = output(registry);
    assertTrue(xml.contains("id=\"" + managerId(first) + "\""), xml);
    assertTrue(xml.contains("id=\"" + managerId(second) + "\""), xml);
  }

  @Test
  void hasTheBasicCapabilities() {
    M manager = manager("contract-basic", "PSML");
    put(manager, "PSML", "k", "v");
    CacheAdminCache cache = registry().cache(providerId(), managerId(manager), "PSML").get();
    assertTrue(cache.capabilities().containsAll(Set.of(CacheCapability.CONFIGURATION, CacheCapability.STATISTICS, CacheCapability.CLEAR)));
    assertFalse(cache.configuration().isEmpty());
    for (CacheProperty property : cache.configuration()) {
      assertFalse(property.name().isEmpty());
      assertFalse(property.value().contains("@"), property.name() + ": no object identity " + property.value());
    }
    assertNotNull(cache.statistics());
    assertNotNull(cache.scope());
  }

  @Test
  void neverCreatesCaches() {
    M manager = manager("contract-create", "PSML");
    CacheRegistry registry = registry();
    assertEquals(CacheProblem.UNKNOWN_CACHE, registry.cache(providerId(), managerId(manager), "missing").problem());
    assertEquals(CacheProblem.UNKNOWN_CACHE, registry.cache(providerId(), managerId(manager), "missing", CacheCapability.CLEAR).problem());
    output(registry);
    assertFalse(hasCache(manager, "missing"));
  }

  @Test
  void doesNotReachClosedManagers() {
    M manager = manager("contract-closed", "PSML");
    String id = managerId(manager);
    CacheAdminCache cache = registry().cache(providerId(), id, "PSML").get();
    close(manager);
    CacheProblem problem = registry().cache(providerId(), id, "PSML").problem();
    assertTrue(problem == CacheProblem.CLOSED || problem == CacheProblem.UNKNOWN_MANAGER, String.valueOf(problem));
    assertThrows(IllegalStateException.class, cache::clear);
  }

  static String output(CacheRegistry registry) {
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      CacheOutput.providers(registry, out);
    }
    return xml.toString();
  }

}
