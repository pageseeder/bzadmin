package org.pageseeder.bzadmin.cache;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.cache.Cache;
import javax.cache.CacheManager;

import org.ehcache.config.CacheRuntimeConfiguration;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.spi.service.StatisticsService;
import org.ehcache.core.statistics.CacheStatistics;
import org.ehcache.core.statistics.TierStatistics;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.spi.service.ServiceCreationConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * An EHCache 3 cache behind JCache: the resource pools (heap, off-heap, disk) and the expiry policy
 * of its runtime configuration, the persistence directory of its manager, and its statistics.
 *
 * <p>Statistics come from the statistics service of the manager, which EHCache 3 creates for every
 * manager made through JCache and which counts for every cache from its creation, whether JCache
 * statistics are enabled or not (they only register the statistics MBean, which reads the same
 * counters). The service is not public: it is read from the private field
 * {@code statisticsService} of {@code org.ehcache.jsr107.Eh107CacheManager} (3.10 to 3.12), never
 * created. Without it (another version, another manager class, another class loader), the cache has
 * no statistics here, and the adapter reads the statistics MBean as for other implementations.
 *
 * <p>Everything read is maintained by EHCache as it goes, read in constant time without changing
 * the cache or its statistics: the counters, and per tier ({@code OnHeap}, {@code OffHeap},
 * {@code Disk}) the number of entries and the bytes they occupy (on the heap only when it is limited
 * in bytes). The entries of the cache are those of its lowest tier, which holds them all; none for a
 * clustered cache. Clustered resources are not listed.
 */
final class Ehcache3Native extends JCacheNative {

  private static final ResourceType.Core[] CORE = {ResourceType.Core.HEAP, ResourceType.Core.OFFHEAP, ResourceType.Core.DISK};

  private static final String MANAGER = "org.ehcache.jsr107.Eh107CacheManager";

  /** The tiers of EHCache 3, from the highest to the lowest, and their metrics. */
  private static final String[] TIERS = {"OnHeap", "OffHeap", "Disk"};

  private static final CacheMetric[] TIER_ENTRIES = {CacheMetric.HEAP_ENTRIES, CacheMetric.OFF_HEAP_ENTRIES, CacheMetric.DISK_ENTRIES};

  private static final CacheMetric[] TIER_BYTES = {CacheMetric.HEAP_BYTES, CacheMetric.OFF_HEAP_BYTES, CacheMetric.DISK_BYTES};

  private static final String CLUSTERED = "Clustered";

  /** The field that holds the statistics service, by class of manager; empty for other classes. */
  private static final ClassValue<Optional<Field>> STATISTICS_SERVICE = new ClassValue<>() {
    @Override
    protected Optional<Field> computeValue(Class<?> type) {
      for (Class<?> c = type; c != null; c = c.getSuperclass()) {
        if (!MANAGER.equals(c.getName())) continue;
        try {
          Field field = c.getDeclaredField("statisticsService");
          field.setAccessible(true);
          return Optional.of(field);
        } catch (NoSuchFieldException | RuntimeException ex) {
          // Another version, or not accessible
          return Optional.empty();
        }
      }
      return Optional.empty();
    }
  };

  private final org.ehcache.Cache<?, ?> cache;

  private final @Nullable Path directory;

  private final @Nullable CacheStatistics statistics;

  private Ehcache3Native(org.ehcache.Cache<?, ?> cache, @Nullable Path directory, @Nullable CacheStatistics statistics) {
    this.cache = cache;
    this.directory = directory;
    this.statistics = statistics;
  }

  /**
   * @return the cache as EHCache 3 sees it, or {@code null} when it is not an EHCache 3 cache.
   */
  static @Nullable JCacheNative unwrap(Cache<?, ?> cache, CacheManager manager) {
    try {
      return new Ehcache3Native(cache.unwrap(org.ehcache.Cache.class), directory(manager), statistics(manager, cache.getName()));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * @return the statistics of the cache that the statistics service of its manager keeps, or
   *   {@code null} when the service cannot be read.
   */
  private static @Nullable CacheStatistics statistics(CacheManager manager, String name) {
    Optional<Field> field = STATISTICS_SERVICE.get(manager.getClass());
    if (field.isEmpty()) return null;
    try {
      Object service = field.get().get(manager);
      // A lookup: the service has the statistics of every cache of the manager
      return service instanceof StatisticsService ? ((StatisticsService) service).getCacheStatistics(name) : null;
    } catch (IllegalAccessException | IllegalArgumentException ex) {
      // Not accessible, or a cache the service does not know
      return null;
    }
  }

  private static @Nullable Path directory(CacheManager manager) {
    try {
      org.ehcache.CacheManager ehcache = manager.unwrap(org.ehcache.CacheManager.class);
      for (ServiceCreationConfiguration<?, ?> service : ehcache.getRuntimeConfiguration().getServiceCreationConfigurations()) {
        if (service instanceof DefaultPersistenceConfiguration) return ((DefaultPersistenceConfiguration) service).getRootDirectory().toPath();
      }
    } catch (IllegalArgumentException ex) {
      // Not unwrapped: unknown
    }
    return null;
  }

  @Override
  List<CacheProperty> configuration() {
    CacheRuntimeConfiguration<?, ?> config = this.cache.getRuntimeConfiguration();
    List<CacheProperty> properties = new ArrayList<>();
    ResourcePools pools = config.getResourcePools();
    for (ResourceType.Core type : CORE) {
      if (!pools.getResourceTypeSet().contains(type)) continue;
      SizedResourcePool pool = pools.getPoolForResource(type);
      properties.add(CacheProperty.of(type.name().toLowerCase(Locale.ROOT),
          pool.getSize() + " " + pool.getUnit() + (pool.isPersistent() ? " (persistent)" : "")));
    }
    ExpiryPolicy<?, ?> expiry = config.getExpiryPolicy();
    properties.add(CacheProperty.of("expiryPolicy", expiry == ExpiryPolicy.NO_EXPIRY ? "none" : expiry.getClass().getName()));
    return properties;
  }

  @Override
  List<CacheTier> tiers() {
    ResourcePools pools = this.cache.getRuntimeConfiguration().getResourcePools();
    List<CacheTier> tiers = new ArrayList<>();
    for (ResourceType.Core type : CORE) {
      if (!pools.getResourceTypeSet().contains(type)) continue;
      SizedResourcePool pool = pools.getPoolForResource(type);
      CacheTier.Type tierType = tierType(type);
      CacheTier tier = pool.getUnit() instanceof MemoryUnit
          ? CacheTier.bytes(tierType, ((MemoryUnit) pool.getUnit()).toBytes(pool.getSize()))
          : CacheTier.entries(tierType, pool.getSize());
      tier = tier.persistent(pool.isPersistent());
      tiers.add(tierType == CacheTier.Type.DISK ? tier.in(this.directory) : tier);
    }
    return tiers;
  }

  @Override
  @Nullable Long entries() {
    if (this.statistics == null) return null;
    Map<String, TierStatistics> tiers = this.statistics.getTierStatistics();
    if (tiers.containsKey(CLUSTERED)) return null;
    for (int i = TIERS.length - 1; i >= 0; i--) {
      TierStatistics tier = tiers.get(TIERS[i]);
      if (tier != null) return tier.getMappings();
    }
    return null;
  }

  @Override
  boolean collectsStatistics() {
    return this.statistics != null;
  }

  @Override
  Map<CacheMetric, Long> statistics() {
    CacheStatistics stats = this.statistics;
    if (stats == null) return Map.of();
    Map<CacheMetric, Long> metrics = new EnumMap<>(CacheMetric.class);
    Long entries = entries();
    if (entries != null) metrics.put(CacheMetric.ENTRIES, entries);
    Map<String, TierStatistics> tiers = stats.getTierStatistics();
    for (int i = 0; i < TIERS.length; i++) {
      TierStatistics tier = tiers.get(TIERS[i]);
      if (tier == null) continue;
      // -1 when not tracked (the heap limited in entries): left out
      metrics.put(TIER_ENTRIES[i], tier.getMappings());
      metrics.put(TIER_BYTES[i], tier.getOccupiedByteSize());
    }
    metrics.put(CacheMetric.HITS, stats.getCacheHits());
    metrics.put(CacheMetric.MISSES, stats.getCacheMisses());
    metrics.put(CacheMetric.PUTS, stats.getCachePuts());
    metrics.put(CacheMetric.REMOVALS, stats.getCacheRemovals());
    metrics.put(CacheMetric.EVICTIONS, stats.getCacheEvictions());
    metrics.put(CacheMetric.EXPIRATIONS, stats.getCacheExpirations());
    return metrics;
  }

  /**
   * Resets the counters of the cache and of each tier: the evictions and expirations of the cache
   * are those of its lowest tier, which the reset of the cache does not reset.
   */
  @Override
  void resetStatistics() {
    CacheStatistics stats = this.statistics;
    if (stats == null) throw new UnsupportedOperationException(CacheCapability.STATISTICS_RESET.toString());
    stats.clear();
    for (TierStatistics tier : stats.getTierStatistics().values()) tier.clear();
  }

  private static CacheTier.Type tierType(ResourceType.Core type) {
    switch (type) {
      case HEAP:
        return CacheTier.Type.HEAP;
      case OFFHEAP:
        return CacheTier.Type.OFF_HEAP;
      default:
        return CacheTier.Type.DISK;
    }
  }

}
