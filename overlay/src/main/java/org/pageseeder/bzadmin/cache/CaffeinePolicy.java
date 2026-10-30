package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Policy;

/**
 * What the policy of a Caffeine cache tells about its maximum: the configuration properties and
 * the heap tier. Shared by Caffeine behind JCache ({@link CaffeineNative}) and native Caffeine
 * ({@link CaffeineProvider}); it does not link the JCache API.
 */
final class CaffeinePolicy {

  private CaffeinePolicy() {
  }

  /**
   * @param cache A Caffeine cache.
   * @return its maximum, in entries or by weight with the weighted size, as configuration properties.
   */
  static List<CacheProperty> maximum(Cache<?, ?> cache) {
    List<CacheProperty> properties = new ArrayList<>();
    Optional<? extends Policy.Eviction<?, ?>> eviction = cache.policy().eviction();
    if (eviction.isEmpty()) {
      properties.add(CacheProperty.of("maximumSize", "none"));
    } else if (eviction.get().isWeighted()) {
      properties.add(CacheProperty.of("maximumWeight", eviction.get().getMaximum()));
      eviction.get().weightedSize().ifPresent(size -> properties.add(CacheProperty.of("weightedSize", size)));
    } else {
      properties.add(CacheProperty.of("maximumSize", eviction.get().getMaximum()));
    }
    return properties;
  }

  /**
   * @param cache A Caffeine cache.
   * @return its tier: the heap, limited in entries or by weight, or unbounded.
   */
  static List<CacheTier> tiers(Cache<?, ?> cache) {
    Optional<? extends Policy.Eviction<?, ?>> eviction = cache.policy().eviction();
    if (eviction.isEmpty()) return List.of(CacheTier.unbounded(CacheTier.Type.HEAP));
    long maximum = eviction.get().getMaximum();
    return List.of(eviction.get().isWeighted() ? CacheTier.weight(CacheTier.Type.HEAP, maximum) : CacheTier.entries(CacheTier.Type.HEAP, maximum));
  }

}
