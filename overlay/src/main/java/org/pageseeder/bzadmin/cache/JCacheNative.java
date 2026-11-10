package org.pageseeder.bzadmin.cache;

import java.util.List;
import java.util.Map;

import javax.cache.Cache;
import javax.cache.CacheManager;

import org.jspecify.annotations.Nullable;

/**
 * What a JCache implementation tells about a cache beyond JSR-107, through {@link Cache#unwrap}:
 * its tiers and their limits, configuration properties, a constant-time entry count, and the
 * statistics it collects whatever the setting of JCache. Only the implementations whose library is
 * present are tried (EHCache 3, Caffeine), each in its own class so that an absent library is never
 * linked; an incompatible version is ignored. The unwrapped cache is never changed; only
 * {@link #resetStatistics()}, an operation, changes its statistics.
 */
abstract class JCacheNative {

  /** Nothing beyond JSR-107. */
  static final JCacheNative NONE = new JCacheNative() {
  };

  private static final boolean EHCACHE3 = CacheProviders.isPresent("org.ehcache.Cache");

  private static final boolean CAFFEINE = CacheProviders.isPresent("com.github.benmanes.caffeine.cache.Cache");

  /**
   * @param cache   A cache.
   * @param manager The manager of the cache, as the host registered it.
   * @return what its implementation tells, {@link #NONE} for another implementation.
   */
  static @Nullable JCacheNative of(Cache<?, ?> cache, CacheManager manager) {
    try {
      JCacheNative found = EHCACHE3 ? Ehcache3Native.unwrap(cache, manager) : null;
      if (found == null && CAFFEINE) found = CaffeineNative.unwrap(cache);
      return found != null ? found : NONE;
    } catch (LinkageError | RuntimeException ex) {
      // A version without the API used
      return NONE;
    }
  }

  /**
   * @return the properties to add to the configuration of the cache.
   */
  List<CacheProperty> configuration() {
    return List.of();
  }

  /**
   * @return the tiers of the cache, empty when unknown.
   */
  List<CacheTier> tiers() {
    return List.of();
  }

  /**
   * @return the number of entries, counted in constant time, or {@code null}.
   */
  @Nullable Long entries() {
    return null;
  }

  /**
   * @return whether the implementation collects statistics whatever the statistics setting of
   *   JCache, which then only registers the statistics MBean: {@link #statistics()} and
   *   {@link #resetStatistics()} are supported.
   */
  boolean collectsStatistics() {
    return false;
  }

  /**
   * @return the counters and sizes that the implementation collects, read without changing them;
   *   empty unless it {@link #collectsStatistics()}.
   */
  Map<CacheMetric, Long> statistics() {
    return Map.of();
  }

  /**
   * Resets the counters of {@link #statistics()}: they start again from zero, the entries are kept.
   *
   * @throws UnsupportedOperationException unless the implementation {@link #collectsStatistics()}.
   */
  void resetStatistics() {
    throw new UnsupportedOperationException(CacheCapability.STATISTICS_RESET.toString());
  }

}
