package org.pageseeder.bzadmin.cache;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * A cache of a {@link CacheAdminManager}, with the capabilities that its provider supports.
 *
 * <p>A method of a capability that the cache does not have throws
 * {@link UnsupportedOperationException}. A method called after the cache or its manager was closed
 * throws {@link IllegalStateException}. No method creates the cache, loads entries from a loader
 * or writes through to a writer.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public interface CacheAdminCache {

  /**
   * @return the name of the cache in its manager.
   */
  String name();

  /**
   * @return what can be done with this cache.
   */
  Set<CacheCapability> capabilities();

  /**
   * @return whether an operation on this cache affects this application only or other nodes too.
   */
  CacheScope scope();

  /**
   * The configuration of the cache that can be shown ({@link CacheCapability#CONFIGURATION}): names
   * and values of the provider, as text, without secrets and without the text of host objects
   * (loaders, listeners, serializers).
   *
   * @return the properties of the configuration, in the order to display them.
   */
  List<CacheProperty> configuration();

  /**
   * The statistics of the cache ({@link CacheCapability#STATISTICS}), without reading entries.
   *
   * @return the statistics; a metric that the provider does not supply is absent, never zero.
   */
  CacheStatistics statistics();

  /**
   * The maximum number of entries of the whole cache, from its configuration: the entry limit of
   * the tier that holds every entry (the last of {@link #tiers()}). For the fill ratio of the
   * statistics.
   *
   * @return the limit, or {@code null} when the cache has none (unbounded), when a tier is limited
   *   in bytes, by weight or by a pool, or when the provider cannot tell.
   */
  default @Nullable Long entryLimit() {
    List<CacheTier> tiers = tiers();
    if (tiers.isEmpty()) return null;
    for (CacheTier tier : tiers) {
      if (tier.maxBytes() != null || tier.maxWeight() != null || tier.isPooled()) return null;
    }
    return tiers.get(tiers.size() - 1).maxEntries();
  }

  /**
   * Where the cache keeps its entries and how much it may keep there, from its configuration, from
   * the tier closest to the application (heap) to the furthest (disk).
   *
   * @return the tiers, empty when the provider cannot tell.
   */
  default List<CacheTier> tiers() {
    return List.of();
  }

  /**
   * Removes every entry of this cache in its manager ({@link CacheCapability#CLEAR}): the bulk
   * removal of the provider, without loading or writing through. Whether listeners are notified
   * and whether other nodes are cleared depends on the provider and {@link #scope()}.
   */
  void clear();

  /**
   * Enables or disables the collection of statistics ({@link CacheCapability#STATISTICS_TOGGLE}).
   *
   * @param enabled {@code true} to collect statistics.
   */
  default void setStatisticsEnabled(boolean enabled) {
    throw new UnsupportedOperationException("statistics-toggle");
  }

  /**
   * Resets the statistics of the cache ({@link CacheCapability#STATISTICS_RESET}): the counts
   * start again from zero; the entries are not changed.
   */
  default void resetStatistics() {
    throw new UnsupportedOperationException("statistics-reset");
  }

  /**
   * Lists a range of the keys of the cache ({@link CacheCapability#ENTRIES}), with
   * {@link CacheEntries#scan}: values are not read, and the work is bounded where the provider
   * allows it.
   *
   * @param offset The position of the first key.
   * @param limit  The number of positions.
   * @return the keys.
   */
  default CacheEntries entries(int offset, int limit) {
    throw new UnsupportedOperationException("entries");
  }

  /**
   * Removes one entry ({@link CacheCapability#REMOVE_ENTRY}), without writing through.
   *
   * @param key The key.
   * @return {@code true} if the cache had an entry with this key.
   * @throws IllegalArgumentException if the cache cannot have a key of this type.
   */
  default boolean remove(CacheKey key) {
    throw new UnsupportedOperationException("remove-entry");
  }

}
