package org.pageseeder.bzadmin.cache;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * The statistics of a cache: the metrics and average times that its provider supplies, and whether
 * their collection is enabled. A metric that is not supplied is unavailable, not zero.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheStatistics {

  /** No metric, collection unknown. */
  public static final CacheStatistics UNAVAILABLE = new CacheStatistics(null, Collections.emptyMap(), Collections.emptyMap());

  private final @Nullable Boolean enabled;

  private final Map<CacheMetric, Long> metrics;

  private final Map<CacheTiming, Double> timings;

  private CacheStatistics(@Nullable Boolean enabled, Map<CacheMetric, Long> metrics, Map<CacheTiming, Double> timings) {
    this.enabled = enabled;
    this.metrics = metrics;
    this.timings = timings;
  }

  /**
   * @param enabled Whether the collection of statistics is enabled; {@code null} when unknown or
   *                when it cannot be disabled.
   * @param metrics The metrics supplied; negative values are left out (providers use -1 for "not
   *                available").
   * @return the statistics.
   */
  public static CacheStatistics of(@Nullable Boolean enabled, Map<CacheMetric, Long> metrics) {
    return of(enabled, metrics, Collections.emptyMap());
  }

  /**
   * @param enabled Whether the collection of statistics is enabled; {@code null} when unknown or
   *                when it cannot be disabled.
   * @param metrics The metrics supplied; negative values are left out.
   * @param timings The average times supplied, in milliseconds; negative or non-finite values are
   *                left out, and so is the average of an operation that was not counted.
   * @return the statistics.
   */
  public static CacheStatistics of(@Nullable Boolean enabled, Map<CacheMetric, Long> metrics, Map<CacheTiming, Double> timings) {
    Map<CacheMetric, Long> available = new EnumMap<>(CacheMetric.class);
    for (Map.Entry<CacheMetric, Long> metric : metrics.entrySet()) {
      Long value = metric.getValue();
      if (value != null && value >= 0) available.put(metric.getKey(), value);
    }
    Map<CacheTiming, Double> measured = new EnumMap<>(CacheTiming.class);
    for (Map.Entry<CacheTiming, Double> timing : timings.entrySet()) {
      Double value = timing.getValue();
      if (value != null && value >= 0 && !value.isInfinite() && timing.getKey().measured(available)) measured.put(timing.getKey(), value);
    }
    return new CacheStatistics(enabled, Collections.unmodifiableMap(available), Collections.unmodifiableMap(measured));
  }

  /**
   * @return whether the collection of statistics is enabled, {@code null} when unknown.
   */
  public @Nullable Boolean enabled() {
    return this.enabled;
  }

  /**
   * @return the metrics supplied, in the order of {@link CacheMetric}.
   */
  public Map<CacheMetric, Long> metrics() {
    return this.metrics;
  }

  /**
   * @return the average times supplied, in milliseconds, in the order of {@link CacheTiming}.
   */
  public Map<CacheTiming, Double> timings() {
    return this.timings;
  }

  /**
   * @param timing The average time.
   * @return its value in milliseconds, or {@code null} when it is not available.
   */
  public @Nullable Double get(CacheTiming timing) {
    return this.timings.get(timing);
  }

  /**
   * @param metric The metric.
   * @return its value, or {@code null} when it is not available.
   */
  public @Nullable Long get(CacheMetric metric) {
    return this.metrics.get(metric);
  }

  /**
   * @return the ratio of hits to requests (hits and misses), or {@code null} when either is not
   *   available or there was no request.
   */
  public @Nullable Double hitRatio() {
    Long hits = get(CacheMetric.HITS);
    Long misses = get(CacheMetric.MISSES);
    if (hits == null || misses == null || hits + misses == 0) return null;
    return (double) hits / (hits + misses);
  }

  /**
   * @return the ratio of evictions to puts, or {@code null} when either is not available or there
   *   was no put. High when entries are evicted before they are used again: the cache is too small.
   */
  public @Nullable Double evictionRatio() {
    return ratioToPuts(CacheMetric.EVICTIONS);
  }

  /**
   * @return the ratio of expirations to puts, or {@code null} when either is not available or there
   *   was no put. High when entries expire before they are used again.
   */
  public @Nullable Double expirationRatio() {
    return ratioToPuts(CacheMetric.EXPIRATIONS);
  }

  /**
   * @param limit The maximum number of entries of the cache, {@code null} when it has none.
   * @return the ratio of entries to the limit, or {@code null} when either is not available.
   */
  public @Nullable Double fillRatio(@Nullable Long limit) {
    Long entries = get(CacheMetric.ENTRIES);
    if (entries == null || limit == null || limit <= 0) return null;
    return (double) entries / limit;
  }

  private @Nullable Double ratioToPuts(CacheMetric metric) {
    Long count = get(metric);
    Long puts = get(CacheMetric.PUTS);
    if (count == null || puts == null || puts == 0) return null;
    return (double) count / puts;
  }

}
