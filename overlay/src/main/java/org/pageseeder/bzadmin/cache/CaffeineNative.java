package org.pageseeder.bzadmin.cache;

import java.util.List;

import javax.cache.Cache;

import org.jspecify.annotations.Nullable;

/**
 * A Caffeine cache behind JCache: its estimated size (constant time; it may count entries that
 * expired and are not yet removed) and its maximum, in entries or by weight, on the heap.
 */
final class CaffeineNative extends JCacheNative {

  private final com.github.benmanes.caffeine.cache.Cache<?, ?> cache;

  private CaffeineNative(com.github.benmanes.caffeine.cache.Cache<?, ?> cache) {
    this.cache = cache;
  }

  /**
   * @return the cache as Caffeine sees it, or {@code null} when it is not a Caffeine cache.
   */
  static @Nullable JCacheNative unwrap(Cache<?, ?> cache) {
    try {
      return new CaffeineNative(cache.unwrap(com.github.benmanes.caffeine.cache.Cache.class));
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @Override
  Long entries() {
    return this.cache.estimatedSize();
  }

  @Override
  List<CacheProperty> configuration() {
    return CaffeinePolicy.maximum(this.cache);
  }

  @Override
  List<CacheTier> tiers() {
    return CaffeinePolicy.tiers(this.cache);
  }

}
