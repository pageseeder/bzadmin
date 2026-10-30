package org.pageseeder.bzadmin.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import org.jspecify.annotations.Nullable;

/**
 * The native Caffeine caches that the host registers ({@link CaffeineCaches}), under the managers
 * it names. Only loaded when Caffeine is on the class path of the overlay ({@link CacheProviders});
 * it does not need the JCache API.
 *
 * <ul>
 *   <li>Entries: {@code estimatedSize()}, constant time; it may count entries that expired and are
 *     not yet removed.</li>
 *   <li>Statistics: hits, misses and evictions from {@code stats()}, when the cache was built with
 *     {@code recordStats()}; Caffeine counts no puts or removals, and its evictions include the
 *     entries that expired or were collected. Whether statistics are recorded is set when the cache
 *     is built: no toggle.</li>
 *   <li>Statistics reset: Caffeine's counters cannot be reset, so the statistics are those since a
 *     snapshot taken by the reset (the snapshot belongs to the registration).</li>
 *   <li>Clear is {@code invalidateAll()}, remove is {@code asMap().remove(key)}: both notify the
 *     removal listener (cause {@code EXPLICIT}); Caffeine 3 has no writer.</li>
 *   <li>Keys are listed from {@code asMap().keySet().iterator()}, which records no statistics and does
 *     not change the eviction or expiry order (no read of the policy); on an expired entry, it only
 *     schedules Caffeine's maintenance sooner.</li>
 *   <li>Scope: local, Caffeine is in-process only.</li>
 * </ul>
 */
final class CaffeineProvider implements CacheAdminProvider {

  /** The identifier of the provider. */
  static final String ID = "caffeine";

  private final String label;

  CaffeineProvider() {
    String version = Caffeine.class.getPackage().getImplementationVersion();
    this.label = version != null ? "Caffeine " + version : "Caffeine";
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String label() {
    return this.label;
  }

  @Override
  public List<CacheAdminManager> managers() {
    return CaffeineCaches.managers().stream()
        .map(Manager::new)
        .collect(Collectors.toList());
  }

  /**
   * A manager named by the host: the caches registered under its identifier. Never closed; it is
   * listed while it has a registered cache.
   */
  private static final class Manager implements CacheAdminManager {

    private final String id;

    Manager(String id) {
      this.id = id;
    }

    @Override
    public String id() {
      return this.id;
    }

    @Override
    public String label() {
      return this.id;
    }

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public List<String> cacheNames() {
      return CaffeineCaches.cacheNames(this.id);
    }

    @Override
    public @Nullable CacheAdminCache cache(String name) {
      return CaffeineCaches.cache(this.id, name);
    }
  }

  /**
   * A registered Caffeine cache, with the snapshot of its statistics reset.
   */
  static final class NativeCache implements CacheAdminCache {

    private final String name;

    private final Class<?> keyType;

    private final Cache<?, ?> cache;

    /** The statistics when they were last reset, subtracted from Caffeine's counters. */
    private final AtomicReference<CacheStats> baseline = new AtomicReference<>(CacheStats.empty());

    /** Set once the host unregisters the cache: an operation started on it fails. */
    private volatile boolean unregistered;

    NativeCache(String name, Class<?> keyType, Cache<?, ?> cache) {
      this.name = name;
      this.keyType = keyType;
      this.cache = cache;
    }

    /**
     * @param other A Caffeine cache.
     * @return {@code true} if this is a registration of that cache.
     */
    boolean isFor(Cache<?, ?> other) {
      return this.cache == other;
    }

    void unregistered() {
      this.unregistered = true;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public Set<CacheCapability> capabilities() {
      Set<CacheCapability> capabilities = EnumSet.of(CacheCapability.CONFIGURATION, CacheCapability.STATISTICS, CacheCapability.CLEAR, CacheCapability.ENTRIES);
      if (isRecordingStats()) capabilities.add(CacheCapability.STATISTICS_RESET);
      if (isRemovableKeyType()) capabilities.add(CacheCapability.REMOVE_ENTRY);
      return capabilities;
    }

    @Override
    public CacheScope scope() {
      return CacheScope.LOCAL;
    }

    @Override
    public List<CacheProperty> configuration() {
      List<CacheProperty> properties = new ArrayList<>();
      properties.add(CacheProperty.of("keyType", this.keyType.getTypeName()));
      properties.addAll(CaffeinePolicy.maximum(this.cache));
      this.cache.policy().expireAfterWrite().ifPresent(expiration ->
          properties.add(CacheProperty.of("expireAfterWrite", Duration.ofNanos(expiration.getExpiresAfter(TimeUnit.NANOSECONDS)))));
      this.cache.policy().expireAfterAccess().ifPresent(expiration ->
          properties.add(CacheProperty.of("expireAfterAccess", Duration.ofNanos(expiration.getExpiresAfter(TimeUnit.NANOSECONDS)))));
      if (this.cache.policy().expireVariably().isPresent()) properties.add(CacheProperty.of("expireAfter", "variable"));
      this.cache.policy().refreshAfterWrite().ifPresent(refresh ->
          properties.add(CacheProperty.of("refreshAfterWrite", Duration.ofNanos(refresh.getRefreshesAfter(TimeUnit.NANOSECONDS)))));
      properties.add(CacheProperty.of("recordStats", isRecordingStats()));
      return properties;
    }

    @Override
    public CacheStatistics statistics() {
      Map<CacheMetric, Long> metrics = new EnumMap<>(CacheMetric.class);
      metrics.put(CacheMetric.ENTRIES, this.cache.estimatedSize());
      boolean recording = isRecordingStats();
      if (recording) {
        CacheStats stats = this.cache.stats().minus(this.baseline.get());
        metrics.put(CacheMetric.HITS, stats.hitCount());
        metrics.put(CacheMetric.MISSES, stats.missCount());
        metrics.put(CacheMetric.EVICTIONS, stats.evictionCount());
      }
      return CacheStatistics.of(recording, metrics);
    }

    @Override
    public List<CacheTier> tiers() {
      return CaffeinePolicy.tiers(this.cache);
    }

    @Override
    public void clear() {
      checkRegistered();
      this.cache.invalidateAll();
    }

    @Override
    public void resetStatistics() {
      if (!isRecordingStats()) throw new UnsupportedOperationException(CacheCapability.STATISTICS_RESET.toString());
      checkRegistered();
      this.baseline.set(this.cache.stats());
    }

    @Override
    public CacheEntries entries(int offset, int limit) {
      checkRegistered();
      return CacheEntries.scan(this.cache.asMap().keySet().iterator(), offset, limit);
    }

    @Override
    public boolean remove(CacheKey key) {
      if (!isRemovableKeyType()) throw new UnsupportedOperationException(CacheCapability.REMOVE_ENTRY.toString());
      if (!this.keyType.isInstance(key.value())) throw new IllegalArgumentException("Not a key of this cache");
      checkRegistered();
      return this.cache.asMap().remove(key.value()) != null;
    }

    private boolean isRecordingStats() {
      return this.cache.policy().isRecordingStats();
    }

    /**
     * @return whether an entry can be removed by a typed key: keys of a supported type, or any type.
     */
    private boolean isRemovableKeyType() {
      return this.keyType == Object.class || this.keyType == String.class || this.keyType == Integer.class || this.keyType == Long.class;
    }

    private void checkRegistered() {
      if (this.unregistered) throw new IllegalStateException("Cache unregistered: " + this.name);
    }
  }

}
