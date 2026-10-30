package org.pageseeder.bzadmin.cache;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.config.PersistenceConfiguration;
import net.sf.ehcache.util.ProductInfo;

/**
 * The live EHCache 2 cache managers of the application: those of
 * {@link CacheManager#ALL_CACHE_MANAGERS}, which lists the managers that exist without creating
 * one ({@link CacheManager#getInstance()} would create the singleton, so it is never called).
 *
 * <p>Compiled against the EHCache 2.6 API, the earliest supported; the statistics of 2.7 and later
 * ({@code StatisticsGateway}, whose method has the name but not the type of the 2.6 one) are read
 * through {@link Ehcache2Statistics}. Only loaded when EHCache 2 is on the class path of the
 * overlay ({@link CacheProviders}).
 *
 * <p>The identifier of a manager is its name: unique among the live managers since EHCache 2.5.
 */
final class Ehcache2Provider implements CacheAdminProvider {

  /** The identifier of the provider. */
  static final String ID = "ehcache2";

  /** The name of a manager created without name. */
  private static final String DEFAULT_NAME = "__DEFAULT__";

  private final String label;

  private final Ehcache2Statistics statistics;

  Ehcache2Provider() {
    this.label = "EHCache " + version();
    this.statistics = Ehcache2Statistics.forRuntime();
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
    return CacheManager.ALL_CACHE_MANAGERS.stream()
        .<CacheAdminManager>map(manager -> new Manager(manager, this.statistics))
        .collect(Collectors.toList());
  }

  private static @Nullable String version() {
    try {
      String version = new ProductInfo().getVersion();
      return version != null ? version : "2";
    } catch (RuntimeException | LinkageError ex) {
      return "2";
    }
  }

  private static final class Manager implements CacheAdminManager {

    private final CacheManager cacheManager;

    private final String name;

    private final Ehcache2Statistics statistics;

    Manager(CacheManager manager, Ehcache2Statistics statistics) {
      this.cacheManager = manager;
      String managerName = manager.getName();
      this.name = managerName != null ? managerName : DEFAULT_NAME;
      this.statistics = statistics;
    }

    @Override
    public String id() {
      return this.name;
    }

    @Override
    public String label() {
      return this.name;
    }

    @Override
    public boolean isClosed() {
      return this.cacheManager.getStatus() != Status.STATUS_ALIVE;
    }

    @Override
    public List<String> cacheNames() {
      try {
        return Arrays.asList(this.cacheManager.getCacheNames());
      } catch (IllegalStateException ex) {
        // Shut down since listed
        return List.of();
      }
    }

    /**
     * The pools in bytes of the manager (EHCache 2.5+), which its caches share.
     */
    @Override
    public List<CacheTier> pools() {
      net.sf.ehcache.config.Configuration config = this.cacheManager.getConfiguration();
      List<CacheTier> pools = new ArrayList<>();
      if (config.isMaxBytesLocalHeapSet()) pools.add(CacheTier.bytes(CacheTier.Type.HEAP, config.getMaxBytesLocalHeap()));
      if (config.isMaxBytesLocalOffHeapSet()) pools.add(CacheTier.bytes(CacheTier.Type.OFF_HEAP, config.getMaxBytesLocalOffHeap()));
      if (config.isMaxBytesLocalDiskSet()) {
        pools.add(CacheTier.bytes(CacheTier.Type.DISK, config.getMaxBytesLocalDisk()).in(diskStorePath(this.cacheManager)));
      }
      return pools;
    }

    @Override
    public @Nullable CacheAdminCache cache(String name) {
      try {
        Ehcache cache = this.cacheManager.getEhcache(name);
        return cache != null ? new Cache(cache, this.cacheManager, this.statistics) : null;
      } catch (IllegalStateException ex) {
        return null;
      }
    }
  }

  /**
   * @param manager A manager.
   * @return the directory of its disk store as configured (EHCache translates {@code java.io.tmpdir}
   *   and the other tokens), the temporary directory by default; {@code null} when not a path.
   */
  static @Nullable Path diskStorePath(CacheManager manager) {
    DiskStoreConfiguration diskStore = manager.getConfiguration().getDiskStoreConfiguration();
    String path = diskStore != null && diskStore.getPath() != null ? diskStore.getPath() : System.getProperty("java.io.tmpdir");
    try {
      return path != null ? Paths.get(path) : null;
    } catch (InvalidPathException ex) {
      return null;
    }
  }

  private static final class Cache implements CacheAdminCache {

    private final Ehcache ehcache;

    /** The manager that owns the cache, for its pools. */
    private final CacheManager cacheManager;

    private final Ehcache2Statistics statistics;

    Cache(Ehcache cache, CacheManager manager, Ehcache2Statistics statistics) {
      this.ehcache = cache;
      this.cacheManager = manager;
      this.statistics = statistics;
    }

    @Override
    public String name() {
      return this.ehcache.getName();
    }

    @Override
    public Set<CacheCapability> capabilities() {
      Set<CacheCapability> capabilities = EnumSet.of(CacheCapability.CONFIGURATION, CacheCapability.STATISTICS, CacheCapability.CLEAR);
      if (this.statistics.canToggle()) capabilities.add(CacheCapability.STATISTICS_TOGGLE);
      // The keys of a clustered cache are those of the cluster
      if (!isClustered(this.ehcache)) {
        capabilities.add(CacheCapability.ENTRIES);
        capabilities.add(CacheCapability.REMOVE_ENTRY);
      }
      return capabilities;
    }

    @Override
    public CacheScope scope() {
      if (isClustered(this.ehcache)) return CacheScope.CLUSTERED;
      // Replicated (RMI, JGroups, JMS) by listeners
      return hasCacheReplicators(this.ehcache) ? CacheScope.CLUSTERED : CacheScope.LOCAL;
    }

    @Override
    public List<CacheProperty> configuration() {
      CacheConfiguration config = this.ehcache.getCacheConfiguration();
      List<CacheProperty> properties = new ArrayList<>();
      properties.add(CacheProperty.of("status", this.ehcache.getStatus()));
      properties.add(CacheProperty.of("maxEntriesLocalHeap", config.getMaxEntriesLocalHeap()));
      properties.add(CacheProperty.of("maxEntriesLocalDisk", config.getMaxEntriesLocalDisk()));
      if (config.getMaxBytesLocalHeap() > 0) properties.add(CacheProperty.of("maxBytesLocalHeap", config.getMaxBytesLocalHeapAsString()));
      if (config.getMaxBytesLocalOffHeap() > 0) properties.add(CacheProperty.of("maxBytesLocalOffHeap", config.getMaxBytesLocalOffHeapAsString()));
      if (config.getMaxBytesLocalDisk() > 0) properties.add(CacheProperty.of("maxBytesLocalDisk", config.getMaxBytesLocalDiskAsString()));
      properties.add(CacheProperty.of("eternal", config.isEternal()));
      properties.add(CacheProperty.of("timeToIdleSeconds", config.getTimeToIdleSeconds()));
      properties.add(CacheProperty.of("timeToLiveSeconds", config.getTimeToLiveSeconds()));
      properties.add(CacheProperty.of("memoryStoreEvictionPolicy", config.getMemoryStoreEvictionPolicy()));
      PersistenceConfiguration persistence = config.getPersistenceConfiguration();
      if (persistence != null) {
        properties.add(CacheProperty.of("persistence", persistence.getStrategy()));
      } else {
        properties.add(CacheProperty.of("overflowToDisk", config.isOverflowToDisk()));
        properties.add(CacheProperty.of("diskPersistent", config.isDiskPersistent()));
      }
      properties.add(CacheProperty.of("overflowToOffHeap", config.isOverflowToOffHeap()));
      properties.add(CacheProperty.of("diskExpiryThreadIntervalSeconds", config.getDiskExpiryThreadIntervalSeconds()));
      properties.add(CacheProperty.of("clearOnFlush", config.isClearOnFlush()));
      properties.add(CacheProperty.of("terracottaClustered", config.isTerracottaClustered()));
      properties.add(CacheProperty.of("transactionalMode", transactionalMode(config)));
      properties.add(CacheProperty.of("replicated", hasCacheReplicators(this.ehcache)));
      return properties;
    }

    @Override
    public CacheStatistics statistics() {
      Map<CacheMetric, Long> metrics = new EnumMap<>(CacheMetric.class);
      boolean clustered = isClustered(this.ehcache);
      // The size of a clustered cache asks the cluster: only the local tiers
      if (!clustered) metrics.put(CacheMetric.ENTRIES, (long) this.ehcache.getSize());
      metrics.put(CacheMetric.HEAP_ENTRIES, this.ehcache.getMemoryStoreSize());
      metrics.put(CacheMetric.OFF_HEAP_ENTRIES, this.ehcache.getOffHeapStoreSize());
      metrics.put(CacheMetric.DISK_ENTRIES, (long) this.ehcache.getDiskStoreSize());
      Boolean enabled = this.statistics.counters(this.ehcache, metrics);
      return CacheStatistics.of(enabled, metrics);
    }

    /**
     * The heap, the off-heap store when the cache overflows to it, and the local disk store. A
     * limit in bytes of the cache comes first, then a pool of the manager, then a number of entries
     * (0 is unbounded). The disk store is in the directory of the manager.
     */
    @Override
    public List<CacheTier> tiers() {
      CacheConfiguration config = this.ehcache.getCacheConfiguration();
      net.sf.ehcache.config.Configuration pools = this.cacheManager.getConfiguration();
      List<CacheTier> tiers = new ArrayList<>();
      tiers.add(tier(CacheTier.Type.HEAP, config.getMaxBytesLocalHeap(), pools.isMaxBytesLocalHeapSet(), config.getMaxEntriesLocalHeap()));
      if (config.isOverflowToOffHeap()) {
        tiers.add(tier(CacheTier.Type.OFF_HEAP, config.getMaxBytesLocalOffHeap(), pools.isMaxBytesLocalOffHeapSet(), 0));
      }
      if (hasLocalDisk(config)) {
        tiers.add(tier(CacheTier.Type.DISK, config.getMaxBytesLocalDisk(), pools.isMaxBytesLocalDiskSet(), config.getMaxEntriesLocalDisk())
            .persistent(isPersistent(config)).in(diskStorePath(this.cacheManager)));
      }
      return tiers;
    }

    @Override
    public void clear() {
      this.ehcache.removeAll();
    }

    /**
     * The keys come from {@code getKeys()}, the only way EHCache 2 lists them: it copies the keys
     * (not the values) of the cache; the range read and returned is bounded.
     */
    @Override
    public CacheEntries entries(int offset, int limit) {
      if (isClustered(this.ehcache)) throw new UnsupportedOperationException(CacheCapability.ENTRIES.toString());
      return CacheEntries.scan(this.ehcache.getKeys().iterator(), offset, limit);
    }

    /**
     * {@code Ehcache.remove(Object)}: notifies the listeners of the cache (and replicates the removal
     * when configured to), without its writer.
     */
    @Override
    public boolean remove(CacheKey key) {
      if (isClustered(this.ehcache)) throw new UnsupportedOperationException(CacheCapability.REMOVE_ENTRY.toString());
      return this.ehcache.remove(key.value());
    }

    @Override
    public void setStatisticsEnabled(boolean enabled) {
      if (!this.statistics.canToggle()) throw new UnsupportedOperationException(CacheCapability.STATISTICS_TOGGLE.toString());
      this.ehcache.setStatisticsEnabled(enabled);
    }

    private static boolean isClustered(Ehcache cache) {
      return cache.getCacheConfiguration().isTerracottaClustered();
    }

    // EHCache 2.11.1 removed the replication by listeners, and with it `hasCacheReplicators()`
    private static boolean hasCacheReplicators(Ehcache cache) {
      try {
        return cache.getCacheEventNotificationService().hasCacheReplicators();
      } catch (LinkageError ex) {
        return false;
      }
    }

    private static CacheTier tier(CacheTier.Type type, long bytes, boolean pooled, long entries) {
      if (bytes > 0) return CacheTier.bytes(type, bytes);
      if (pooled) return CacheTier.pooled(type);
      return CacheTier.entries(type, entries);
    }

    @SuppressWarnings("deprecation")
    private static boolean isPersistent(CacheConfiguration config) {
      PersistenceConfiguration persistence = config.getPersistenceConfiguration();
      if (persistence == null) return config.isDiskPersistent();
      return persistence.getStrategy() == PersistenceConfiguration.Strategy.LOCALRESTARTABLE;
    }

    private static boolean hasLocalDisk(CacheConfiguration config) {
      PersistenceConfiguration persistence = config.getPersistenceConfiguration();
      if (persistence == null) return config.isOverflowToDisk() || config.isDiskPersistent();
      PersistenceConfiguration.Strategy strategy = persistence.getStrategy();
      return strategy == PersistenceConfiguration.Strategy.LOCALTEMPSWAP || strategy == PersistenceConfiguration.Strategy.LOCALRESTARTABLE;
    }

    private static String transactionalMode(CacheConfiguration config) {
      if (config.isXaStrictTransactional()) return "xa_strict";
      if (config.isXaTransactional()) return "xa";
      if (config.isLocalTransactional()) return "local";
      return "off";
    }
  }

}
