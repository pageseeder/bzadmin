package org.pageseeder.bzadmin.cache;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

/**
 * The output of the cache generators, the same for every provider: identity, capabilities, and the
 * statistics and configuration a provider supplies. No field is specific to one provider.
 *
 * <pre>{@code
 * <cache-providers>
 *   <cache-provider id="jcache" label="JCache">
 *     <cache-manager id="main" label="main" status="active" total="2" truncated="false">
 *       <pool type="heap" max-bytes="268435456"/>
 *       <cache name="PSML" scope="local" capabilities="configuration statistics clear">
 *         <statistics enabled="true" hit-ratio="0.3" entry-limit="1000" fill-ratio="0.7" eviction-ratio="0">
 *           <metric name="entries" value="700"/>
 *           <metric name="hits" value="300"/>
 *           <metric name="misses" value="700"/>
 *           <metric name="puts" value="700"/>
 *           <metric name="evictions" value="0"/>
 *           <metric name="average-get-time" value="0.0125" unit="ms"/>
 *         </statistics>
 *         <tier type="heap" max-entries="1000"/>
 *         <warning code="low-hit-ratio" value="0.3" threshold="0.5"/>
 *       </cache>
 *     </cache-manager>
 *   </cache-provider>
 *   <cache-capacity>
 *     <resource type="heap" limits="268435456" available="1073741824" ratio="0.25" unsized="1"/>
 *   </cache-capacity>
 * </cache-providers>
 * }</pre>
 *
 * <p>The average times ({@link CacheTiming}) are metrics with {@code unit="ms"} and a decimal value;
 * the others are counts. A metric that the provider does not supply has no {@code metric} element; {@code enabled} is
 * absent when unknown. The ratios are derived from the metrics, and absent when one of their inputs
 * is; {@code entry-limit} is the entry limit of the configuration ({@link CacheAdminCache#entryLimit()}).
 * A {@code warning} ({@link CacheWarning}) gives the ratio it checks and its threshold, when it has
 * them. The {@code tier} and {@code pool} elements are the configured storage ({@link CacheTier});
 * {@code cache-capacity} adds up their limits in bytes against what is available
 * ({@link CacheCapacity}), when a cache has tiers. A manager whose identifier the provider lists twice (or that is invalid) has
 * {@code problem="ambiguous-manager"} and no cache: it cannot be addressed. A provider that fails
 * to list its managers has {@code failed} (the class of the exception), and is logged. A cache
 * closed while listed is left out.
 *
 * <p>In JSON: {@code {"providers": [{"id": ..., "managers": [{"id": ..., "caches": [{"name": ...,
 * "capabilities": [...], "statistics": {"enabled": true, "metrics": [{"name": "hits", "value": 1}]}, "warnings": [{"code": ...}]}]}]}]}}.
 *
 * <p>Each cache is read before anything of it is written ({@link Snapshot}), so that a cache closed
 * during a request fails before its output starts.
 */
final class CacheOutput {

  /** The maximum number of caches listed per manager. */
  static final int MAX_CACHES = 1000;

  private static final String PROVIDER = "provider";

  private static final String MANAGER = "manager";

  private static final String VALUE = "value";

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheOutput.class);

  private CacheOutput() {
  }

  /**
   * Writes every provider, its managers and their caches with their statistics.
   *
   * @param registry The registry.
   * @param out      The output.
   */
  static void providers(CacheRegistry registry, OutputWriter out) {
    providers(registry, CacheResources.platform(), out);
  }

  /**
   * Writes every provider, its managers and their caches, then their capacity: the configured
   * limits of all the caches listed against what is available.
   *
   * @param registry  The registry.
   * @param resources What the JVM and the volumes make available.
   * @param out       The output.
   */
  static void providers(CacheRegistry registry, CacheResources resources, OutputWriter out) {
    CacheCapacity capacity = new CacheCapacity(resources);
    out.startObject("cache-providers");
    out.startArray("providers", ContextOption.JSON_ONLY);
    for (CacheAdminProvider provider : registry.providers()) {
      provider(provider, resources, capacity, out);
    }
    out.endArray();
    List<CacheCapacity.Resource> list = capacity.resources();
    if (!list.isEmpty()) capacity(list, capacity.warnings(), out);
    out.endObject();
  }

  private static void provider(CacheAdminProvider provider, CacheResources resources, CacheCapacity capacity, OutputWriter out) {
    out.startObject("cache-provider");
    out.field("id", provider.id());
    out.field("label", provider.label());
    List<CacheAdminManager> managers;
    try {
      managers = provider.managers();
    } catch (RuntimeException ex) {
      LOGGER.warn("Cache provider {} failed to list its managers", provider.id(), ex);
      out.field("failed", ex.getClass().getName());
      managers = List.of();
    }
    out.startArray("managers", ContextOption.JSON_ONLY);
    for (CacheAdminManager manager : managers) {
      manager(manager, isAddressable(manager, managers), resources, capacity, out);
    }
    out.endArray();
    out.endObject();
  }

  private static void manager(CacheAdminManager manager, boolean addressable, CacheResources resources, CacheCapacity capacity,
      OutputWriter out) {
    out.startObject("cache-manager");
    out.field("id", manager.id());
    out.field("label", manager.label());
    boolean closed = manager.isClosed();
    out.field("status", closed ? "closed" : "active");
    if (!addressable) out.field("problem", CacheProblem.AMBIGUOUS_MANAGER.toString());
    List<String> names = addressable && !closed ? names(manager) : List.of();
    out.field("total", names.size());
    out.field("truncated", names.size() > MAX_CACHES);
    if (addressable && !closed) {
      List<CacheTier> pools = manager.pools();
      capacity.add(pools);
      out.startArray("pools", ContextOption.JSON_ONLY);
      for (CacheTier pool : pools) tier("pool", pool, out);
      out.endArray();
    }
    out.startArray("caches", ContextOption.JSON_ONLY);
    for (String name : names.subList(0, Math.min(names.size(), MAX_CACHES))) {
      Snapshot snapshot = snapshot(manager.cache(name), resources);
      if (snapshot != null) {
        capacity.add(snapshot.tiers);
        snapshot.write(out);
      }
    }
    out.endArray();
    out.endObject();
  }

  /**
   * @param manager  A manager of a provider.
   * @param managers All the managers of the provider.
   * @return {@code true} if a request can name the manager: its identifier is valid and unique.
   */
  static boolean isAddressable(CacheAdminManager manager, List<CacheAdminManager> managers) {
    if (!CacheRegistry.isValidManagerId(manager.id())) return false;
    int count = 0;
    for (CacheAdminManager other : managers) if (manager.id().equals(other.id())) count++;
    return count == 1;
  }

  /**
   * Reads a cache for the output, without its configuration.
   *
   * @param cache The cache, {@code null} when removed since listed.
   * @return the snapshot, or {@code null} when the cache is removed or closed.
   */
  private static @Nullable Snapshot snapshot(@Nullable CacheAdminCache cache, CacheResources resources) {
    if (cache == null) return null;
    try {
      return new Snapshot(cache, false, resources);
    } catch (IllegalStateException ex) {
      // Closed since listed
      return null;
    }
  }

  /**
   * Writes one cache: its identity, capabilities, statistics and configuration.
   *
   * @param provider The identifier of the provider.
   * @param manager  The identifier of the manager.
   * @param cache    The cache, read.
   * @param out      The output.
   */
  static void cache(String provider, String manager, Snapshot cache, OutputWriter out) {
    out.startObject("cache-info");
    out.field(PROVIDER, provider);
    out.field(MANAGER, manager);
    cache.write(out);
    out.endObject();
  }

  /**
   * Writes a range of the keys of a cache.
   *
   * @param provider The identifier of the provider.
   * @param manager  The identifier of the manager.
   * @param cache    The cache, read.
   * @param entries  The keys.
   * @param limit    The number of positions requested.
   * @param out      The output.
   */
  static void entries(String provider, String manager, Snapshot cache, CacheEntries entries, int limit, OutputWriter out) {
    out.startObject("cache-entries");
    out.field(PROVIDER, provider);
    out.field(MANAGER, manager);
    cache.write(out);
    out.startObject("entries");
    out.field("offset", entries.offset());
    out.field("limit", limit);
    out.field("scanned", entries.scanned());
    out.field("more", entries.more());
    out.field("max-scan", CacheEntries.MAX_SCAN);
    out.startArray("keys", ContextOption.JSON_ONLY);
    for (CacheKey key : entries.keys()) {
      out.startObject("entry");
      out.field("key", key.text());
      out.field("key-type", key.type().toString());
      out.endObject();
    }
    out.endArray();
    out.startArray("unsupported", ContextOption.JSON_ONLY);
    for (Map.Entry<String, Integer> type : entries.unsupported().entrySet()) {
      out.startObject("unsupported-keys");
      out.field("class", type.getKey());
      out.field("count", type.getValue().intValue());
      out.endObject();
    }
    out.endArray();
    out.endObject();
    out.endObject();
  }

  /**
   * Writes the identity of a cache that an operation changed.
   */
  static void identity(String element, String provider, String manager, String cache, OutputWriter out) {
    out.startObject(element);
    out.field(PROVIDER, provider);
    out.field(MANAGER, manager);
    out.field("name", cache);
    out.endObject();
  }

  /**
   * Writes why a request does not reach a manager or cache.
   *
   * @param problem  The problem.
   * @param provider The identifier of the provider requested.
   * @param manager  The identifier of the manager requested.
   * @param cache    The name of the cache requested, {@code null} for a manager.
   * @param out      The output.
   */
  static void problem(CacheProblem problem, @Nullable String provider, @Nullable String manager, @Nullable String cache, OutputWriter out) {
    out.startObject("no-cache");
    out.field("problem", problem.toString());
    out.optionalField(PROVIDER, provider);
    out.optionalField(MANAGER, manager);
    out.optionalField("name", cache);
    out.endObject();
  }

  /**
   * Writes a tier of a cache or a pool of a manager: its kind, its limit (none when unbounded) and,
   * on disk, its directory.
   */
  static void tier(String element, CacheTier tier, OutputWriter out) {
    out.startObject(element);
    out.field("type", tier.type().toString());
    out.optionalField("max-entries", tier.maxEntries());
    out.optionalField("max-bytes", tier.maxBytes());
    out.optionalField("max-weight", tier.maxWeight());
    if (tier.isPooled()) out.field("pooled", true);
    if (tier.isPersistent()) out.field("persistent", true);
    Path path = tier.path();
    if (path != null) out.field("path", path.toString());
    out.endObject();
  }

  private static void capacity(List<CacheCapacity.Resource> resources, Map<CacheWarning, @Nullable Double> warnings, OutputWriter out) {
    out.startObject("cache-capacity");
    out.startArray("resources", ContextOption.JSON_ONLY);
    for (CacheCapacity.Resource resource : resources) {
      out.startObject("resource");
      out.field("type", resource.type.toString());
      out.field("limits", resource.limits);
      out.optionalField("available", resource.available);
      out.optionalField("total", resource.total);
      out.optionalField("ratio", resource.ratio());
      if (resource.path != null) out.field("path", resource.path.toString());
      out.field("unsized", resource.unsized);
      out.endObject();
    }
    out.endArray();
    warnings(warnings, out);
    out.endObject();
  }

  /**
   * Writes warnings with the ratio each checks and its threshold, when it has them.
   */
  static void warnings(Map<CacheWarning, @Nullable Double> warnings, OutputWriter out) {
    out.startArray("warnings", ContextOption.JSON_ONLY);
    for (Map.Entry<CacheWarning, @Nullable Double> warning : warnings.entrySet()) {
      out.startObject("warning");
      out.field("code", warning.getKey().toString());
      out.optionalField(VALUE, warning.getValue());
      out.optionalField("threshold", warning.getKey().threshold());
      out.endObject();
    }
    out.endArray();
  }

  /**
   * @param manager A manager.
   * @return the names of its caches, sorted by name ignoring case.
   */
  static List<String> names(CacheAdminManager manager) {
    Set<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo));
    sorted.addAll(manager.cacheNames());
    return new ArrayList<>(sorted);
  }

  /**
   * What the output shows of a cache, read at once.
   */
  static final class Snapshot {

    private final String name;

    private final CacheScope scope;

    private final Set<CacheCapability> capabilities;

    private final @Nullable CacheStatistics statistics;

    private final @Nullable Long entryLimit;

    final List<CacheTier> tiers;

    private final Map<CacheWarning, @Nullable Double> warnings = new LinkedHashMap<>();

    private final @Nullable List<CacheProperty> configuration;

    /**
     * @param cache         The cache.
     * @param configuration Whether to read the configuration.
     * @throws IllegalStateException when the cache or its manager is closed.
     */
    Snapshot(CacheAdminCache cache, boolean configuration) {
      this(cache, configuration, CacheResources.platform());
    }

    /**
     * @param cache         The cache.
     * @param configuration Whether to read the configuration.
     * @param resources     What the JVM and the volumes make available, for the sizing warnings.
     * @throws IllegalStateException when the cache or its manager is closed.
     */
    Snapshot(CacheAdminCache cache, boolean configuration, CacheResources resources) {
      this.name = cache.name();
      this.scope = cache.scope();
      this.capabilities = cache.capabilities();
      this.statistics = this.capabilities.contains(CacheCapability.STATISTICS) ? cache.statistics() : null;
      this.entryLimit = this.statistics != null ? cache.entryLimit() : null;
      this.tiers = cache.tiers();
      if (this.statistics != null) this.warnings.putAll(CacheWarning.of(this.statistics));
      this.warnings.putAll(CacheCapacity.warnings(this.tiers, resources));
      this.configuration = configuration && this.capabilities.contains(CacheCapability.CONFIGURATION) ? cache.configuration() : null;
    }

    void write(OutputWriter out) {
      out.startObject("cache");
      out.field("name", this.name);
      out.field("scope", this.scope.toString());
      List<String> names = Arrays.stream(CacheCapability.values())
          .filter(this.capabilities::contains)
          .map(CacheCapability::toString)
          .collect(Collectors.toList());
      if (out.isXml()) out.field("capabilities", String.join(" ", names));
      else out.field("capabilities", names);
      if (this.statistics != null) statistics(this.statistics, this.entryLimit, out);
      out.startArray("tiers", ContextOption.JSON_ONLY);
      for (CacheTier tier : this.tiers) tier("tier", tier, out);
      out.endArray();
      warnings(this.warnings, out);
      if (this.configuration != null) {
        out.startArray("configuration", ContextOption.DEFAULT);
        for (CacheProperty property : this.configuration) {
          out.startObject("property");
          out.field("name", property.name());
          out.field(VALUE, property.value());
          out.endObject();
        }
        out.endArray();
      }
      out.endObject();
    }

    private static void statistics(CacheStatistics statistics, @Nullable Long entryLimit, OutputWriter out) {
      out.startObject("statistics");
      out.optionalField("enabled", statistics.enabled());
      out.optionalField("hit-ratio", statistics.hitRatio());
      out.optionalField("entry-limit", entryLimit);
      out.optionalField("fill-ratio", statistics.fillRatio(entryLimit));
      out.optionalField("eviction-ratio", statistics.evictionRatio());
      out.optionalField("expiration-ratio", statistics.expirationRatio());
      out.startArray("metrics", ContextOption.JSON_ONLY);
      for (Map.Entry<CacheMetric, Long> metric : statistics.metrics().entrySet()) {
        out.startObject("metric");
        out.field("name", metric.getKey().toString());
        out.field(VALUE, metric.getValue().longValue());
        out.endObject();
      }
      for (Map.Entry<CacheTiming, Double> timing : statistics.timings().entrySet()) {
        out.startObject("metric");
        out.field("name", timing.getKey().toString());
        out.field(VALUE, timing.getValue().doubleValue());
        out.field("unit", "ms");
        out.endObject();
      }
      out.endArray();
      out.endObject();
    }
  }

}
