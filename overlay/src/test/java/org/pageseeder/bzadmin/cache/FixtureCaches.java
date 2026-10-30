package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

/**
 * An in-memory cache provider for the contract tests: managers and caches are made by the test, and
 * the fixture records what the admin code does to them.
 */
final class FixtureCaches {

  private FixtureCaches() {
  }

  static Provider provider(String id) {
    return new Provider(id);
  }

  static final class Provider implements CacheAdminProvider {

    private final String id;

    final List<Manager> managers = new ArrayList<>();

    @Nullable RuntimeException failure;

    Provider(String id) {
      this.id = id;
    }

    Manager manager(String id, String label) {
      Manager manager = new Manager(id, label);
      this.managers.add(manager);
      return manager;
    }

    @Override
    public String id() {
      return this.id;
    }

    @Override
    public String label() {
      return "Fixture " + this.id;
    }

    @Override
    public List<CacheAdminManager> managers() {
      if (this.failure != null) throw this.failure;
      return new ArrayList<>(this.managers);
    }
  }

  static final class Manager implements CacheAdminManager {

    private final String id;

    private final String label;

    final Map<String, Cache> caches = new LinkedHashMap<>();

    List<CacheTier> pools = List.of();

    volatile boolean closed;

    /** Calls of {@link #cache(String)} with a name it does not have. */
    int unknown;

    Manager(String id, String label) {
      this.id = id;
      this.label = label;
    }

    Cache cache(String name, CacheCapability... capabilities) {
      Cache cache = new Cache(this, name, capabilities.length == 0 ? EnumSet.noneOf(CacheCapability.class) : EnumSet.of(capabilities[0], capabilities));
      this.caches.put(name, cache);
      return cache;
    }

    Cache basic(String name) {
      return cache(name, CacheCapability.CONFIGURATION, CacheCapability.STATISTICS, CacheCapability.CLEAR);
    }

    @Override
    public String id() {
      return this.id;
    }

    @Override
    public String label() {
      return this.label;
    }

    @Override
    public boolean isClosed() {
      return this.closed;
    }

    @Override
    public List<String> cacheNames() {
      return this.closed ? List.of() : new ArrayList<>(this.caches.keySet());
    }

    @Override
    public List<CacheTier> pools() {
      return this.pools;
    }

    @Override
    public @Nullable CacheAdminCache cache(String name) {
      if (this.closed) return null;
      Cache cache = this.caches.get(name);
      if (cache == null) this.unknown++;
      return cache;
    }
  }

  static final class Cache implements CacheAdminCache {

    private final Manager manager;

    private final String name;

    private final Set<CacheCapability> capabilities;

    final Map<String, String> entries = new ConcurrentHashMap<>();

    final Map<CacheMetric, Long> metrics = new EnumMap<>(CacheMetric.class);

    final Map<CacheTiming, Double> timings = new EnumMap<>(CacheTiming.class);

    Boolean statisticsEnabled;

    Long entryLimit;

    List<CacheTier> tiers = List.of();

    CacheScope scope = CacheScope.LOCAL;

    int clears;

    Cache(Manager manager, String name, Set<CacheCapability> capabilities) {
      this.manager = manager;
      this.name = name;
      this.capabilities = capabilities;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public Set<CacheCapability> capabilities() {
      return this.capabilities;
    }

    @Override
    public CacheScope scope() {
      return this.scope;
    }

    @Override
    public List<CacheProperty> configuration() {
      check(CacheCapability.CONFIGURATION);
      return List.of(CacheProperty.of("capacity", 100), CacheProperty.of("expiry", null));
    }

    @Override
    public CacheStatistics statistics() {
      check(CacheCapability.STATISTICS);
      Map<CacheMetric, Long> values = new EnumMap<>(this.metrics);
      values.put(CacheMetric.ENTRIES, (long) this.entries.size());
      return CacheStatistics.of(this.statisticsEnabled, values, this.timings);
    }

    @Override
    public Long entryLimit() {
      return this.entryLimit != null ? this.entryLimit : CacheAdminCache.super.entryLimit();
    }

    @Override
    public List<CacheTier> tiers() {
      return this.tiers;
    }

    @Override
    public void clear() {
      check(CacheCapability.CLEAR);
      this.entries.clear();
      this.clears++;
    }

    @Override
    public void setStatisticsEnabled(boolean enabled) {
      check(CacheCapability.STATISTICS_TOGGLE);
      this.statisticsEnabled = enabled;
    }

    @Override
    public void resetStatistics() {
      check(CacheCapability.STATISTICS_RESET);
      this.metrics.clear();
    }

    private void check(CacheCapability capability) {
      if (!this.capabilities.contains(capability)) throw new UnsupportedOperationException(capability.toString());
      if (this.manager.closed) throw new IllegalStateException("Closed");
    }
  }

}
