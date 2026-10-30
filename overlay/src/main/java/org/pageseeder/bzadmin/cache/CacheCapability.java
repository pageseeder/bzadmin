package org.pageseeder.bzadmin.cache;

import java.util.Locale;

/**
 * What can be done with a cache. Listing caches is always possible; {@link #CONFIGURATION},
 * {@link #STATISTICS} and {@link #CLEAR} are the basic administration that every provider of the
 * overlay supports; the others are optional.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheCapability {

  /** The configuration of the cache can be shown. */
  CONFIGURATION,

  /** Statistics are available (each metric still may not be). */
  STATISTICS,

  /** The cache can be cleared. */
  CLEAR,

  /** The collection of statistics can be enabled and disabled. */
  STATISTICS_TOGGLE,

  /** The statistics can be reset, so that the counts start again from zero. */
  STATISTICS_RESET,

  /** Keys of supported types can be listed, a bounded number at a time. */
  ENTRIES,

  /** An entry whose key has a supported type can be removed. */
  REMOVE_ENTRY;

  /**
   * @return the name in the output: lower case, with {@code -} ({@code statistics-toggle}).
   */
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

}
