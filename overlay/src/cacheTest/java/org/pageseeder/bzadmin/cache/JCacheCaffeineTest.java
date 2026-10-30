package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import javax.cache.CacheManager;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;

/**
 * The JCache adapter with Caffeine.
 */
final class JCacheCaffeineTest extends JCacheContract {

  @Override
  String cachingProvider() {
    return "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";
  }

  @Override
  boolean countsEntries() {
    return true;
  }

  /** The maximum of Caffeine, through unwrap: entries on the heap, or none. */
  @Test
  void readsTheMaximumSize() {
    CacheManager manager = manager("caffeine-maximum", "unbounded");
    CaffeineConfiguration<String, String> bounded = new CaffeineConfiguration<>();
    bounded.setTypes(String.class, String.class);
    bounded.setMaximumSize(OptionalLong.of(500));
    manager.createCache("bounded", bounded);
    CacheRegistry registry = registry();

    CacheAdminCache cache = registry.cache(JCacheProvider.ID, "caffeine-maximum", "bounded").get();
    List<CacheTier> tiers = cache.tiers();
    assertEquals(CacheTier.Type.HEAP, tiers.get(0).type());
    assertEquals(500L, tiers.get(0).maxEntries());
    assertEquals(500L, cache.entryLimit());
    Map<String, String> config = new HashMap<>();
    for (CacheProperty property : cache.configuration()) config.put(property.name(), property.value());
    assertEquals("500", config.get("maximumSize"));

    CacheAdminCache unbounded = registry.cache(JCacheProvider.ID, "caffeine-maximum", "unbounded").get();
    assertTrue(unbounded.tiers().get(0).isUnbounded());
    assertNull(unbounded.entryLimit());
  }

}
