package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.jspecify.annotations.Nullable;

/**
 * The cache management contract with fixture providers: identity by provider, manager and cache;
 * no provider, several providers, the same cache name in several managers; closed managers,
 * capabilities, unavailable statistics; and an output with no field specific to a provider.
 */
final class CacheContractTest {

  /** Every attribute of the output: none is specific to one provider. */
  private static final Set<String> ATTRIBUTES = Set.of("id", "label", "failed", "status", "problem", "total",
      "truncated", "name", "scope", "capabilities", "enabled", "hit-ratio", "entry-limit", "fill-ratio", "eviction-ratio",
      "expiration-ratio", "value", "provider", "manager", "code", "threshold", "type", "max-entries", "max-bytes", "max-weight",
      "pooled", "persistent", "path", "limits", "available", "ratio", "unsized");

  @Test
  void hasNoProvider() throws Exception {
    CacheRegistry registry = new CacheRegistry(List::of);
    assertTrue(registry.providers().isEmpty());
    assertEquals(CacheProblem.UNKNOWN_PROVIDER, registry.cache("ehcache2", "default", "PSML").problem());
    assertEquals(CacheProblem.UNKNOWN_PROVIDER, registry.manager("jcache", "main").problem());
    Document output = providers(registry);
    assertEquals("cache-providers", output.getDocumentElement().getTagName());
    assertEquals(0, output.getDocumentElement().getChildNodes().getLength());
  }

  @Test
  void requiresAValueOnlyForSuccessfulLookups() {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "Main");
    FixtureCaches.Cache cache = manager.basic("pages");
    CacheRegistry registry = registry(provider);
    assertSame(manager, registry.manager("jcache", "main").requireValue());
    CacheRegistry.Lookup<CacheAdminCache> found = registry.cache("jcache", "main", "pages");
    assertNull(found.problem());
    assertSame(cache, found.get());
    assertSame(cache, found.requireValue());

    CacheRegistry.Lookup<CacheAdminCache> missing = registry.cache("jcache", "main", "missing");
    assertEquals(CacheProblem.UNKNOWN_CACHE, missing.problem());
    assertNull(missing.get());
    assertThrows(IllegalStateException.class, missing::requireValue);
  }

  @Test
  void requiresTheWholeIdentity() {
    CacheRegistry registry = registry(FixtureCaches.provider("a"));
    for (String[] identity : new String[][] {{null, "m", "c"}, {"a", null, "c"}, {"a", "m", null}, {"", "m", "c"}, {"a", "", "c"}, {"a", "m", ""}}) {
      assertEquals(CacheProblem.MISSING_PARAMETER, registry.cache(identity[0], identity[1], identity[2]).problem());
    }
    assertEquals(CacheProblem.MISSING_PARAMETER, registry.manager("a", null).problem());
    assertEquals(400, CacheProblem.MISSING_PARAMETER.status().code());
  }

  @Test
  void listsSeveralProvidersInOrder() throws Exception {
    FixtureCaches.Provider detected = FixtureCaches.provider("ehcache2");
    detected.manager("default", "default").basic("PSML");
    FixtureCaches.Provider jcache = FixtureCaches.provider("jcache");
    jcache.manager("ehcache3", "urn:x-bzadmin:ehcache3").basic("PSML");
    jcache.manager("caffeine", "urn:x-bzadmin:caffeine").basic("PSML");
    CacheRegistry registry = new CacheRegistry(() -> List.of(detected));
    registry.register(jcache);
    assertEquals(List.of("ehcache2", "jcache"), ids(registry.providers()));
    Document output = providers(registry);
    assertEquals("ehcache2 jcache", join(output, "/cache-providers/cache-provider/@id"));
    assertEquals("default ehcache3 caffeine", join(output, "//cache-manager/@id"));
    assertEquals("PSML PSML PSML", join(output, "//cache/@name"));
    assertAllowedAttributes(output);
    assertTrue(registry.unregister("jcache"));
    assertFalse(registry.unregister("jcache"));
    assertFalse(registry.unregister("ehcache2"), "Detected providers are not registered");
    assertEquals(List.of("ehcache2"), ids(registry.providers()));
  }

  @Test
  void rejectsDuplicateAndInvalidProviders() {
    CacheRegistry registry = new CacheRegistry(() -> List.of(FixtureCaches.provider("ehcache2")));
    registry.register(FixtureCaches.provider("jcache"));
    CacheAdminProvider jcache = FixtureCaches.provider("jcache");
    assertThrows(IllegalArgumentException.class, () -> registry.register(jcache));
    CacheAdminProvider ehcache2 = FixtureCaches.provider("ehcache2");
    assertThrows(IllegalArgumentException.class, () -> registry.register(ehcache2));
    for (String id : new String[] {"", "JCache", "a b", "a/b", "-a", "a".repeat(33)}) {
      CacheAdminProvider invalid = FixtureCaches.provider(id);
      assertThrows(IllegalArgumentException.class, () -> registry.register(invalid), id);
    }
    CacheAdminProvider unnamed = FixtureCaches.provider(null);
    assertThrows(IllegalArgumentException.class, () -> registry.register(unnamed));
    assertThrows(NullPointerException.class, () -> registry.register(null));
    assertEquals(List.of("ehcache2", "jcache"), ids(registry.providers()));
  }

  /**
   * The same cache name in two managers (and in two providers): each identity reaches its own
   * cache, and an operation on one leaves the others.
   */
  @Test
  void resolvesTheSameNameInEachManager() throws Exception {
    FixtureCaches.Provider jcache = FixtureCaches.provider("jcache");
    FixtureCaches.Cache first = jcache.manager("one", "same label").basic("PSML");
    FixtureCaches.Cache second = jcache.manager("two", "same label").basic("PSML");
    FixtureCaches.Provider other = FixtureCaches.provider("other");
    FixtureCaches.Cache third = other.manager("one", "same label").basic("PSML");
    for (FixtureCaches.Cache cache : List.of(first, second, third)) cache.entries.put("k", "v");
    CacheRegistry registry = registry(jcache, other);

    assertSame(first, registry.cache("jcache", "one", "PSML").get());
    assertSame(second, registry.cache("jcache", "two", "PSML").get());
    assertSame(third, registry.cache("other", "one", "PSML").get());

    registry.cache("jcache", "two", "PSML", CacheCapability.CLEAR).get().clear();
    assertEquals(1, first.entries.size());
    assertEquals(0, second.entries.size());
    assertEquals(1, third.entries.size());
    assertEquals(List.of(0, 1, 0), List.of(first.clears, second.clears, third.clears));

    Document output = providers(registry);
    assertEquals("1 0 1", join(output, "//cache/statistics/metric[@name = 'entries']/@value"));
    assertEquals(CacheProblem.UNKNOWN_MANAGER, registry.cache("jcache", "same label", "PSML").problem());
    assertEquals(CacheProblem.UNKNOWN_CACHE, registry.cache("jcache", "one", "psml").problem());
  }

  @Test
  void neverCreatesCaches() {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    manager.basic("PSML");
    CacheRegistry registry = registry(provider);
    assertEquals(CacheProblem.UNKNOWN_CACHE, registry.cache("jcache", "main", "missing").problem());
    assertEquals(CacheProblem.UNKNOWN_CACHE, registry.cache("jcache", "main", "missing", CacheCapability.CLEAR).problem());
    assertEquals(Set.of("PSML"), manager.caches.keySet());
    assertEquals(2, manager.unknown);
  }

  @Test
  void doesNotResolveAmbiguousManagers() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    provider.manager("main", "first").basic("PSML");
    provider.manager("main", "second").basic("PSML");
    provider.manager("ok", "ok").basic("PSML");
    provider.manager("bad\nid", "control character").basic("PSML");
    CacheRegistry registry = registry(provider);
    assertEquals(CacheProblem.AMBIGUOUS_MANAGER, registry.cache("jcache", "main", "PSML").problem());
    assertEquals(409, CacheProblem.AMBIGUOUS_MANAGER.status().code());
    Document output = providers(registry);
    assertEquals("ambiguous-manager ambiguous-manager ambiguous-manager", join(output, "//cache-manager/@problem"));
    assertEquals("ok", join(output, "//cache-manager[cache]/@id"));
  }

  @Test
  void reportsClosedManagers() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    FixtureCaches.Cache cache = manager.basic("PSML");
    CacheRegistry registry = registry(provider);
    manager.closed = true;
    assertEquals(CacheProblem.CLOSED, registry.cache("jcache", "main", "PSML").problem());
    assertSame(manager, registry.manager("jcache", "main").get());
    Document output = providers(registry);
    assertEquals("closed", join(output, "//cache-manager/@status"));
    assertEquals("0", join(output, "//cache-manager/@total"));
    // Closed after the lookup: the operation fails as the contract says
    assertThrows(IllegalStateException.class, cache::clear);
  }

  @Test
  void requiresTheCapabilityOfAnOperation() {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    FixtureCaches.Cache readOnly = manager.cache("read-only", CacheCapability.STATISTICS);
    manager.basic("basic");
    CacheRegistry registry = registry(provider);
    assertEquals(CacheProblem.UNSUPPORTED, registry.cache("jcache", "main", "read-only", CacheCapability.CLEAR).problem());
    assertEquals(501, CacheProblem.UNSUPPORTED.status().code());
    assertEquals(CacheProblem.UNSUPPORTED, registry.cache("jcache", "main", "basic", CacheCapability.STATISTICS_TOGGLE).problem());
    assertNull(registry.cache("jcache", "main", "basic", CacheCapability.CLEAR).problem());
    assertThrows(UnsupportedOperationException.class, readOnly::clear);
    CacheAdminCache basic = registry.cache("jcache", "main", "basic").get();
    assertThrows(UnsupportedOperationException.class, () -> basic.setStatisticsEnabled(true));
    assertEquals(CacheProblem.UNSUPPORTED, registry.cache("jcache", "main", "basic", CacheCapability.STATISTICS_RESET).problem());
    assertThrows(UnsupportedOperationException.class, basic::resetStatistics);
    CacheAdminCache minimal = new CacheAdminCache() {
      @Override public String name() { return "minimal"; }
      @Override public Set<CacheCapability> capabilities() { return Set.of(); }
      @Override public CacheScope scope() { return CacheScope.UNKNOWN; }
      @Override public List<CacheProperty> configuration() { throw new UnsupportedOperationException(); }
      @Override public CacheStatistics statistics() { throw new UnsupportedOperationException(); }
      @Override public void clear() { throw new UnsupportedOperationException(); }
    };
    assertThrows(UnsupportedOperationException.class, () -> minimal.setStatisticsEnabled(false));
    assertThrows(UnsupportedOperationException.class, minimal::resetStatistics);
  }

  @Test
  void leavesOutUnavailableMetrics() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    FixtureCaches.Cache cache = manager.basic("PSML");
    cache.metrics.put(CacheMetric.HITS, 3L);
    cache.metrics.put(CacheMetric.MISSES, 1L);
    cache.metrics.put(CacheMetric.EVICTIONS, -1L);
    FixtureCaches.Cache none = manager.cache("no-statistics", CacheCapability.CLEAR);
    none.entries.put("k", "v");
    CacheRegistry registry = registry(provider);

    CacheStatistics statistics = registry.cache("jcache", "main", "PSML").get().statistics();
    assertEquals(Map.of(CacheMetric.ENTRIES, 0L, CacheMetric.HITS, 3L, CacheMetric.MISSES, 1L), statistics.metrics());
    assertNull(statistics.get(CacheMetric.EVICTIONS));
    assertNull(statistics.enabled());
    assertEquals(0.75, statistics.hitRatio());
    assertNull(CacheStatistics.of(true, Map.of(CacheMetric.HITS, 0L, CacheMetric.MISSES, 0L)).hitRatio());
    assertNull(CacheStatistics.of(true, Map.of(CacheMetric.HITS, 1L)).hitRatio());
    assertTrue(CacheStatistics.UNAVAILABLE.metrics().isEmpty());

    Document output = providers(registry);
    assertEquals("entries hits misses", join(output, "//cache[@name = 'PSML']/statistics/metric/@name"));
    assertEquals("0.75", join(output, "//cache[@name = 'PSML']/statistics/@hit-ratio"));
    assertEquals("", join(output, "//cache[@name = 'PSML']/statistics/@enabled"));
    assertEquals(0, nodes(output, "//cache[@name = 'no-statistics']/statistics").getLength(), "Unavailable, not zero");
    assertEquals("clear", join(output, "//cache[@name = 'no-statistics']/@capabilities"));
    assertEquals("configuration statistics clear", join(output, "//cache[@name = 'PSML']/@capabilities"));
  }

  @Test
  void averageTimesAreDecimalMetricsOfCountedOperations() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Cache cache = provider.manager("main", "main").basic("PSML");
    cache.metrics.put(CacheMetric.HITS, 3L);
    cache.metrics.put(CacheMetric.MISSES, 0L);
    cache.metrics.put(CacheMetric.PUTS, 0L);
    cache.timings.put(CacheTiming.AVERAGE_GET_TIME, 0.0125);
    cache.timings.put(CacheTiming.AVERAGE_PUT_TIME, 0.0);
    cache.timings.put(CacheTiming.AVERAGE_REMOVE_TIME, 0.5);
    CacheRegistry registry = registry(provider);

    CacheStatistics statistics = registry.cache("jcache", "main", "PSML").get().statistics();
    assertEquals(Map.of(CacheTiming.AVERAGE_GET_TIME, 0.0125), statistics.timings(), "No put counted, removals unavailable: no average");
    assertNull(CacheStatistics.of(true, Map.of(CacheMetric.HITS, 1L), Map.of(CacheTiming.AVERAGE_GET_TIME, Double.NaN)).get(CacheTiming.AVERAGE_GET_TIME));
    assertNull(CacheStatistics.of(true, Map.of(CacheMetric.HITS, 1L), Map.of(CacheTiming.AVERAGE_GET_TIME, -1.0)).get(CacheTiming.AVERAGE_GET_TIME));
    assertEquals(0.0, CacheStatistics.of(true, Map.of(CacheMetric.MISSES, 1L), Map.of(CacheTiming.AVERAGE_GET_TIME, 0.0)).get(CacheTiming.AVERAGE_GET_TIME));

    Document output = providers(registry);
    assertEquals("0.0125", join(output, "//cache[@name = 'PSML']/statistics/metric[@name = 'average-get-time']/@value"));
    assertEquals("ms", join(output, "//cache[@name = 'PSML']/statistics/metric[@name = 'average-get-time']/@unit"));
    assertEquals("", join(output, "//cache[@name = 'PSML']/statistics/metric[@name = 'hits']/@unit"), "Counts have no unit");
  }

  @Test
  void derivesRatiosFromAvailableMetrics() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    FixtureCaches.Cache cache = manager.basic("PSML");
    cache.entries.put("k", "v");
    cache.entryLimit = 4L;
    cache.metrics.put(CacheMetric.PUTS, 8L);
    cache.metrics.put(CacheMetric.EVICTIONS, 2L);
    FixtureCaches.Cache unbounded = manager.basic("unbounded");
    unbounded.metrics.put(CacheMetric.PUTS, 0L);
    unbounded.metrics.put(CacheMetric.EVICTIONS, 0L);
    CacheRegistry registry = registry(provider);

    CacheStatistics statistics = cache.statistics();
    assertEquals(0.25, statistics.evictionRatio());
    assertNull(statistics.expirationRatio(), "No expiration count");
    assertEquals(0.25, statistics.fillRatio(4L));
    assertNull(statistics.fillRatio(null));
    assertNull(statistics.fillRatio(0L));
    assertNull(unbounded.statistics().evictionRatio(), "No put");

    Document output = providers(registry);
    assertEquals("4", join(output, "//cache[@name = 'PSML']/statistics/@entry-limit"));
    assertEquals("0.25", join(output, "//cache[@name = 'PSML']/statistics/@fill-ratio"));
    assertEquals("0.25", join(output, "//cache[@name = 'PSML']/statistics/@eviction-ratio"));
    assertEquals("", join(output, "//cache[@name = 'PSML']/statistics/@expiration-ratio"));
    assertEquals("", join(output, "//cache[@name = 'unbounded']/statistics/@entry-limit"));
    assertEquals("", join(output, "//cache[@name = 'unbounded']/statistics/@fill-ratio"));
    assertEquals(0, nodes(output, "//warning").getLength());
    assertAllowedAttributes(output);
  }

  /** Warnings need a minimum sample and their inputs; evictions and expirations need a hit ratio that is not high. */
  @Test
  void warnsOnlyWithASample() throws Exception {
    long sample = CacheWarning.MIN_SAMPLE;
    assertEquals(List.of(), codes(statistics(100, 800, 900, 900, 0)), "Below the sample");
    assertEquals(List.of(CacheWarning.LOW_HIT_RATIO, CacheWarning.HIGH_EVICTION), codes(statistics(100, sample, sample, sample, 0)));
    assertEquals(List.of(CacheWarning.HIGH_EXPIRATION), codes(statistics(sample, sample, sample, 0, sample / 2)));
    assertEquals(List.of(), codes(statistics(9 * sample, sample, sample, sample, sample)), "High hit ratio");
    assertEquals(List.of(), codes(CacheStatistics.of(null, Map.of(CacheMetric.PUTS, sample))), "Unavailable inputs");
    assertEquals(List.of(CacheWarning.HIGH_EVICTION),
        codes(CacheStatistics.of(null, Map.of(CacheMetric.PUTS, sample, CacheMetric.EVICTIONS, sample))),
        "No hit ratio: evictions alone");

    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Cache cache = provider.manager("main", "main").basic("PSML");
    cache.metrics.putAll(statistics(100, sample, sample, sample, 0).metrics());
    Document output = providers(registry(provider));
    assertEquals("low-hit-ratio high-eviction", join(output, "//cache/warning/@code"));
    assertEquals("0.5 0.5", join(output, "//cache/warning/@threshold"));
    assertEquals("1.0", join(output, "//cache/warning[@code = 'high-eviction']/@value"));
    assertAllowedAttributes(output);
  }

  private static List<CacheWarning> codes(CacheStatistics statistics) {
    return new ArrayList<>(CacheWarning.of(statistics).keySet());
  }

  /** Tiers as written, and the entry limit they give: the last tier's, only when no tier is sized another way. */
  @Test
  void writesTiersAndDerivesTheEntryLimit() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    manager.basic("entries").tiers = List.of(CacheTier.entries(CacheTier.Type.HEAP, 100), CacheTier.entries(CacheTier.Type.DISK, 5000));
    manager.basic("bytes").tiers = List.of(CacheTier.entries(CacheTier.Type.HEAP, 100),
        CacheTier.bytes(CacheTier.Type.DISK, 1 << 20).persistent(true).in(Paths.get("/var/cache/app")));
    manager.basic("weight").tiers = List.of(CacheTier.weight(CacheTier.Type.HEAP, 1000));
    manager.basic("unbounded").tiers = List.of(CacheTier.entries(CacheTier.Type.HEAP, Long.MAX_VALUE));
    manager.basic("unknown");
    CacheRegistry registry = registry(provider);
    assertEquals(5000L, registry.cache("jcache", "main", "entries").get().entryLimit());
    assertNull(registry.cache("jcache", "main", "bytes").get().entryLimit(), "A tier sized in bytes");
    assertNull(registry.cache("jcache", "main", "weight").get().entryLimit());
    assertNull(registry.cache("jcache", "main", "unbounded").get().entryLimit(), "Long.MAX_VALUE is no limit");
    assertTrue(CacheTier.entries(CacheTier.Type.HEAP, Long.MAX_VALUE).isUnbounded());

    Document output = providers(registry, new Resources(1L << 40, 1L << 40, 1L << 40));
    assertEquals("heap disk", join(output, "//cache[@name = 'bytes']/tier/@type"));
    assertEquals("100 1048576", join(output, "//cache[@name = 'bytes']/tier/@*[starts-with(name(), 'max-')]"));
    assertEquals("true", join(output, "//cache[@name = 'bytes']/tier[@type = 'disk']/@persistent"));
    assertEquals(Paths.get("/var/cache/app").toString(), join(output, "//cache[@name = 'bytes']/tier[@type = 'disk']/@path"));
    assertEquals("1000", join(output, "//cache[@name = 'weight']/tier/@max-weight"));
    assertEquals("unbounded", join(output, "//cache[@name = 'unbounded']/warning/@code"));
    assertEquals("", join(output, "//cache[@name = 'unbounded']/warning/@value"), "No ratio");
    assertEquals(0, nodes(output, "//cache[@name = 'unknown']/tier | //cache[@name = 'unknown']/warning").getLength(), "Unknown is not unbounded");
    assertAllowedAttributes(output);
  }

  /**
   * Limits in bytes are added up by kind of storage, a manager's pool counted once, disks by volume;
   * tiers sized otherwise are counted, not guessed; warnings compare the limits with what is available.
   */
  @Test
  void comparesLimitsWithWhatIsAvailable() throws Exception {
    long mb = 1L << 20;
    FixtureCaches.Provider provider = FixtureCaches.provider("ehcache2");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    manager.pools = List.of(CacheTier.bytes(CacheTier.Type.HEAP, 300 * mb));
    manager.basic("pooled").tiers = List.of(CacheTier.pooled(CacheTier.Type.HEAP));
    manager.basic("big").tiers = List.of(CacheTier.bytes(CacheTier.Type.HEAP, 600 * mb), CacheTier.bytes(CacheTier.Type.OFF_HEAP, 900 * mb),
        CacheTier.bytes(CacheTier.Type.DISK, 2000 * mb).in(Paths.get("/data/a")));
    manager.basic("counted").tiers = List.of(CacheTier.entries(CacheTier.Type.HEAP, 1000),
        CacheTier.bytes(CacheTier.Type.DISK, 100 * mb).in(Paths.get("/data/b")));
    Resources resources = new Resources(1000 * mb, 1000 * mb, 1500 * mb);

    Document output = providers(registry(provider), resources);
    String heap = "/cache-providers/cache-capacity/resource[@type = 'heap']";
    assertEquals(String.valueOf(900 * mb), join(output, heap + "/@limits"), "The pool and the cache in bytes");
    assertEquals(String.valueOf(1000 * mb), join(output, heap + "/@available"));
    assertEquals("1", join(output, heap + "/@unsized"), "The heap in entries");
    assertEquals("0.9", join(output, heap + "/@ratio"));
    assertEquals(String.valueOf(2100 * mb), join(output, "//cache-capacity/resource[@type = 'disk']/@limits"), "One volume");
    assertEquals("heap-over-allocated off-heap-over-allocated disk-over-allocated", join(output, "/cache-providers/cache-capacity/warning/@code"));
    assertEquals("0.5 0.8 1.0", join(output, "/cache-providers/cache-capacity/warning/@threshold"));
    assertEquals("heap", join(output, "//cache-manager/pool/@type"));
    // Each cache against what is available on its own
    assertEquals("heap-over-allocated off-heap-over-allocated disk-over-allocated", join(output, "//cache[@name = 'big']/warning/@code"));
    assertEquals("", join(output, "//cache[@name = 'counted']/warning/@code"), "100 MB of disk fits");
    assertEquals("", join(output, "//cache[@name = 'pooled']/warning/@code"), "The pool is the manager's");
    assertAllowedAttributes(output);

    Document unknown = providers(registry(provider), new Resources(null, null, null));
    assertEquals(0, nodes(unknown, "//warning").getLength(), "Unknown resources give no warning");
    assertEquals("", join(unknown, "//cache-capacity/resource/@available"));
  }

  /** What a test JVM makes available: one volume for every directory. */
  private static final class Resources extends CacheResources {

    private final @Nullable Long heap;

    private final @Nullable Long direct;

    private final @Nullable Long usable;

    Resources(@Nullable Long heap, @Nullable Long direct, @Nullable Long usable) {
      this.heap = heap;
      this.direct = direct;
      this.usable = usable;
    }

    @Override
    @Nullable Long maxHeap() {
      return this.heap;
    }

    @Override
    @Nullable Long maxDirectMemory() {
      return this.direct;
    }

    @Override
    @Nullable Volume volume(@Nullable Path directory) {
      return this.usable != null && directory != null ? new Volume("volume", this.usable, 2 * this.usable) : null;
    }
  }

  private static CacheStatistics statistics(long hits, long misses, long puts, long evictions, long expirations) {
    return CacheStatistics.of(null, Map.of(CacheMetric.HITS, hits, CacheMetric.MISSES, misses, CacheMetric.PUTS, puts,
        CacheMetric.EVICTIONS, evictions, CacheMetric.EXPIRATIONS, expirations));
  }

  @Test
  void writesOneCache() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Cache cache = provider.manager("main", "main").cache("PSML", CacheCapability.values());
    cache.scope = CacheScope.CLUSTERED;
    cache.statisticsEnabled = false;
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      CacheOutput.cache("jcache", "main", new CacheOutput.Snapshot(cache, true), out);
    }
    Document output = parse(xml.toString());
    assertEquals("jcache", join(output, "/cache-info/@provider"));
    assertEquals("main", join(output, "/cache-info/@manager"));
    assertEquals("clustered", join(output, "//cache/@scope"));
    assertEquals("configuration statistics clear statistics-toggle statistics-reset entries remove-entry", join(output, "//cache/@capabilities"));
    assertEquals("false", join(output, "//statistics/@enabled"));
    assertEquals("capacity expiry", join(output, "/cache-info/cache/configuration/property/@name"));
    assertEquals("100", join(output, "/cache-info/cache/configuration/property[1]/@value"));
    assertEquals("", join(output, "/cache-info/cache/configuration/property[2]/@value"));
    assertAllowedAttributes(output);
  }

  @Test
  void writesProblems() throws Exception {
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      CacheOutput.problem(CacheProblem.UNKNOWN_CACHE, "jcache", "main", "PSML", out);
    }
    Document output = parse(xml.toString());
    for (String[] attribute : new String[][] {{"problem", "unknown-cache"}, {"provider", "jcache"}, {"manager", "main"}, {"name", "PSML"}}) {
      assertEquals(attribute[1], join(output, "/no-cache/@" + attribute[0]));
    }
  }

  @Test
  void reportsAFailingProvider() throws Exception {
    FixtureCaches.Provider failing = FixtureCaches.provider("failing");
    failing.failure = new IllegalStateException("Provider closed");
    FixtureCaches.Provider working = FixtureCaches.provider("working");
    working.manager("main", "main").basic("PSML");
    Document output = providers(registry(failing, working));
    assertEquals(IllegalStateException.class.getName(), join(output, "//cache-provider[@id = 'failing']/@failed"));
    assertEquals("PSML", join(output, "//cache/@name"));
    assertFalse(xmlOf(registry(failing)).contains("Provider closed"), "No exception message");
  }

  @Test
  void boundsTheCachesOfAManager() throws Exception {
    FixtureCaches.Provider provider = FixtureCaches.provider("jcache");
    FixtureCaches.Manager manager = provider.manager("main", "main");
    for (int i = CacheOutput.MAX_CACHES; i >= 0; i--) manager.basic(String.format("cache-%04d", i));
    Document output = providers(registry(provider));
    assertEquals("1001", join(output, "//cache-manager/@total"));
    assertEquals("true", join(output, "//cache-manager/@truncated"));
    assertEquals(CacheOutput.MAX_CACHES, nodes(output, "//cache").getLength());
    assertEquals("cache-0000", join(output, "(//cache)[1]/@name"));
  }

  @Test
  void cutsLongConfigurationValues() {
    assertEquals(CacheProperty.MAX_VALUE_LENGTH, CacheProperty.of("long", "x".repeat(5000)).value().length());
    assertTrue(CacheProperty.of("long", "x".repeat(5000)).value().endsWith("…"));
    assertEquals("", CacheProperty.of("none", null).value());
    assertThrows(NullPointerException.class, () -> CacheProperty.of(null, "x"));
  }

  @Test
  void namesInTheOutput() {
    assertEquals("statistics-toggle", CacheCapability.STATISTICS_TOGGLE.toString());
    assertEquals("statistics-reset", CacheCapability.STATISTICS_RESET.toString());
    assertEquals("off-heap-entries", CacheMetric.OFF_HEAP_ENTRIES.toString());
    assertEquals("unknown-cache", CacheProblem.UNKNOWN_CACHE.toString());
    assertEquals("local", CacheScope.LOCAL.toString());
    assertTrue(CacheRegistry.isValidManagerId("urn:x-bzadmin:ehcache3 (classloader 1)"));
    assertFalse(CacheRegistry.isValidManagerId("x".repeat(CacheRegistry.MAX_MANAGER_ID_LENGTH + 1)));
    assertFalse(CacheRegistry.isValidManagerId(""));
    assertFalse(CacheRegistry.isValidManagerId(null));
  }

  private static CacheRegistry registry(CacheAdminProvider... providers) {
    CacheRegistry registry = new CacheRegistry(List::of);
    for (CacheAdminProvider provider : providers) registry.register(provider);
    return registry;
  }

  private static List<String> ids(List<CacheAdminProvider> providers) {
    List<String> ids = new ArrayList<>();
    for (CacheAdminProvider provider : providers) ids.add(provider.id());
    return ids;
  }

  private static String xmlOf(CacheRegistry registry) {
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      CacheOutput.providers(registry, out);
    }
    return xml.toString();
  }

  private static Document providers(CacheRegistry registry) throws Exception {
    return parse(xmlOf(registry));
  }

  private static Document providers(CacheRegistry registry, CacheResources resources) throws Exception {
    StringWriter xml = new StringWriter();
    try (XmlOutputAdapter out = new XmlOutputAdapter(xml)) {
      CacheOutput.providers(registry, resources, out);
    }
    return parse(xml.toString());
  }

  private static Document parse(String xml) throws Exception {
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private static NodeList nodes(Document document, String xpath) throws Exception {
    return (NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, document, XPathConstants.NODESET);
  }

  private static String join(Document document, String xpath) throws Exception {
    NodeList nodes = nodes(document, xpath);
    List<String> values = new ArrayList<>();
    for (int i = 0; i < nodes.getLength(); i++) values.add(nodes.item(i).getNodeValue());
    return String.join(" ", values);
  }

  private static void assertAllowedAttributes(Document document) throws Exception {
    Set<String> found = new TreeSet<>();
    NodeList elements = nodes(document, "//*");
    for (int i = 0; i < elements.getLength(); i++) {
      NamedNodeMap attributes = ((Element) elements.item(i)).getAttributes();
      for (int j = 0; j < attributes.getLength(); j++) found.add(attributes.item(j).getNodeName());
    }
    found.removeAll(ATTRIBUTES);
    assertEquals(Set.of(), found, "Attributes outside the provider-neutral schema");
  }

}
