package org.pageseeder.bzadmin.cache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.cache.Cache;
import javax.cache.CacheManager;

import org.ehcache.config.CacheRuntimeConfiguration;
import org.ehcache.config.ResourcePools;
import org.ehcache.config.ResourceType;
import org.ehcache.config.SizedResourcePool;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.ExpiryPolicy;
import org.ehcache.impl.config.persistence.DefaultPersistenceConfiguration;
import org.ehcache.spi.service.ServiceCreationConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * An EHCache 3 cache behind JCache: the resource pools (heap, off-heap, disk) and the expiry policy
 * of its runtime configuration, and the persistence directory of its manager. EHCache 3 has no
 * constant-time entry count in its public API (its statistics service is not reachable from a
 * JCache manager): no {@link #entries()}. Clustered resources are not listed.
 */
final class Ehcache3Native extends JCacheNative {

  private static final ResourceType.Core[] CORE = {ResourceType.Core.HEAP, ResourceType.Core.OFFHEAP, ResourceType.Core.DISK};

  private final org.ehcache.Cache<?, ?> cache;

  private final @Nullable Path directory;

  private Ehcache3Native(org.ehcache.Cache<?, ?> cache, @Nullable Path directory) {
    this.cache = cache;
    this.directory = directory;
  }

  /**
   * @return the cache as EHCache 3 sees it, or {@code null} when it is not an EHCache 3 cache.
   */
  static @Nullable JCacheNative unwrap(Cache<?, ?> cache, CacheManager manager) {
    try {
      return new Ehcache3Native(cache.unwrap(org.ehcache.Cache.class), directory(manager));
    } catch (IllegalArgumentException ex) {
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
