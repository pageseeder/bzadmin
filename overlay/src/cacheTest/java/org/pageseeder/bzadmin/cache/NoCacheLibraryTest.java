package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * A host without cache library: nothing detected, the registry and the output work, and no
 * adapter class is linked.
 */
@EnabledIfSystemProperty(named = "bzadmin.cache.libraries", matches = "NoCache")
final class NoCacheLibraryTest {

  @Test
  void detectsNoProvider() {
    assertFalse(CacheProviders.isPresent("net.sf.ehcache.CacheManager"));
    assertTrue(CacheProviders.detected().isEmpty());
    CacheRegistry registry = CacheRegistry.global();
    assertTrue(registry.providers().isEmpty());
    assertEquals(CacheProblem.UNKNOWN_PROVIDER, registry.cache("ehcache2", "__DEFAULT__", "PSML").problem());
    assertEquals("<cache-providers/>", ProviderContract.output(registry).replaceAll("\\s", ""));
  }

}
