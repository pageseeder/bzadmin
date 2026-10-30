package org.pageseeder.bzadmin.cache;

import java.util.Locale;

/**
 * A statistic of a cache. Counts are since the statistics were enabled or last reset, as the
 * provider counts them; entry counts are the provider's own count, never computed by reading the
 * entries.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheMetric {

  /** Entries in the cache (all tiers). */
  ENTRIES,

  /** Entries on the heap. */
  HEAP_ENTRIES,

  /** Entries off the heap. */
  OFF_HEAP_ENTRIES,

  /** Entries on disk. */
  DISK_ENTRIES,

  /** Bytes used on the heap, when the provider tracks them (never measured by the overlay). */
  HEAP_BYTES,

  /** Bytes used off the heap, when the provider tracks them. */
  OFF_HEAP_BYTES,

  /** Bytes used on disk, when the provider tracks them. */
  DISK_BYTES,

  /** Requests that found an entry. */
  HITS,

  /** Requests that found no entry. */
  MISSES,

  /** Entries added or replaced. */
  PUTS,

  /** Entries removed explicitly. */
  REMOVALS,

  /** Entries evicted to make room. */
  EVICTIONS,

  /** Entries that expired. */
  EXPIRATIONS;

  /**
   * @return the name in the output: lower case, with {@code -} ({@code heap-entries}).
   */
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

}
