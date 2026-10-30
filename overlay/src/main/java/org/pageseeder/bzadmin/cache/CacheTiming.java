package org.pageseeder.bzadmin.cache;

import java.util.Locale;
import java.util.Map;

/**
 * An average operation time of a cache, in milliseconds, as the provider measures it over the
 * same period as its counters. Unlike a {@link CacheMetric}, a decimal; unavailable while the
 * operation it averages was not counted. A slow get on a cache with a disk tier is a sign that
 * the heap tier is too small.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheTiming {

  /** The average time of a get (hit or miss). */
  AVERAGE_GET_TIME,

  /** The average time of a put. */
  AVERAGE_PUT_TIME,

  /** The average time of a removal. */
  AVERAGE_REMOVE_TIME;

  /**
   * @param metrics The counters of the cache.
   * @return whether the operation this timing averages was counted at least once; a provider that
   *   reports 0 for an average without any operation does not mean that it took no time.
   */
  boolean measured(Map<CacheMetric, Long> metrics) {
    switch (this) {
      case AVERAGE_GET_TIME: return positive(metrics.get(CacheMetric.HITS), metrics.get(CacheMetric.MISSES));
      case AVERAGE_PUT_TIME: return positive(metrics.get(CacheMetric.PUTS));
      default: return positive(metrics.get(CacheMetric.REMOVALS));
    }
  }

  private static boolean positive(Long... counts) {
    long sum = 0;
    for (Long count : counts) {
      if (count != null) sum += count;
    }
    return sum > 0;
  }

  /**
   * @return the name in the output: lower case, with {@code -} ({@code average-get-time}).
   */
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

}
