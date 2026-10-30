package org.pageseeder.bzadmin.cache;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A sign that the configuration of a cache may not suit the application, computed from its
 * statistics or from its configured limits, never by changing anything. An unavailable value never
 * gives a warning.
 *
 * <p>Statistics warnings need a minimum sample ({@link #MIN_SAMPLE} requests or puts), so that a
 * cache that has just started gives none. Their counters are those of the provider, usually since
 * the cache was created: a warning says what happened over that period, not necessarily now (the
 * page shows the recent rates).
 *
 * <p>Sizing warnings ({@link CacheCapacity}) compare configured limits, not use, with what the JVM
 * and the volume make available.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheWarning {

  /** Fewer than half the requests found an entry. */
  LOW_HIT_RATIO(0.5),

  /** At least half the entries put were evicted to make room, and the hit ratio is not high. */
  HIGH_EVICTION(0.5),

  /** At least half the entries put expired, and the hit ratio is not high. */
  HIGH_EXPIRATION(0.5),

  /** No tier of the cache has a limit: it can grow until memory or disk runs out. */
  UNBOUNDED(null),

  /** The heap limits in bytes reach half the maximum heap. */
  HEAP_OVER_ALLOCATED(0.5),

  /** The off-heap limits reach most of the maximum direct memory, which the JVM also uses for I/O. */
  OFF_HEAP_OVER_ALLOCATED(0.8),

  /** The disk limits of a volume are more than its free space. */
  DISK_OVER_ALLOCATED(1.0);

  /** The minimum number of requests (hits and misses) or puts for a statistics warning. */
  public static final long MIN_SAMPLE = 1000;

  /** A hit ratio from which evictions and expirations are not a problem. */
  public static final double HIGH_HIT_RATIO = 0.8;

  private final @Nullable Double threshold;

  CacheWarning(@Nullable Double threshold) {
    this.threshold = threshold;
  }

  /**
   * @return the ratio that triggers the warning: below it for {@link #LOW_HIT_RATIO}, from it for
   *   the others; {@code null} for a warning without ratio.
   */
  public @Nullable Double threshold() {
    return this.threshold;
  }

  /**
   * @param statistics The statistics of a cache.
   * @return the statistics warnings that apply, in the order of this enum, with the ratio each
   *   checks.
   */
  public static Map<CacheWarning, @Nullable Double> of(CacheStatistics statistics) {
    Map<CacheWarning, @Nullable Double> warnings = new LinkedHashMap<>();
    Double hitRatio = statistics.hitRatio();
    Long hits = statistics.get(CacheMetric.HITS);
    Long misses = statistics.get(CacheMetric.MISSES);
    if (hitRatio != null && hits + misses >= MIN_SAMPLE && hitRatio < LOW_HIT_RATIO.threshold) {
      warnings.put(LOW_HIT_RATIO, hitRatio);
    }
    Long puts = statistics.get(CacheMetric.PUTS);
    if (puts != null && puts >= MIN_SAMPLE && (hitRatio == null || hitRatio < HIGH_HIT_RATIO)) {
      Double evictions = statistics.evictionRatio();
      if (evictions != null && evictions >= HIGH_EVICTION.threshold) warnings.put(HIGH_EVICTION, evictions);
      Double expirations = statistics.expirationRatio();
      if (expirations != null && expirations >= HIGH_EXPIRATION.threshold) warnings.put(HIGH_EXPIRATION, expirations);
    }
    return warnings;
  }

  /**
   * @return the code in the output: lower case, with {@code -} ({@code high-eviction}).
   */
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

}
