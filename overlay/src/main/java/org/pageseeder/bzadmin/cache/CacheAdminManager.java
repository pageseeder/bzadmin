package org.pageseeder.bzadmin.cache;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A live cache manager of the host, as one {@link CacheAdminProvider} sees it.
 *
 * <p>The manager belongs to the host: the admin pages read it and run the operations of its
 * caches, but never create caches in it, change its configuration or close it.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public interface CacheAdminManager {

  /**
   * The identifier of the manager in its provider: the value of the {@code manager} parameter of
   * the admin requests. It must be unique among the managers of the provider and stable while the
   * manager is live, at most 128 characters without control characters. The provider chooses it
   * (a name the host registered, the manager's own unique name); it is not a secret.
   *
   * @return the identifier of the manager.
   */
  String id();

  /**
   * @return the name of the manager for display: may be the same as another manager's.
   */
  String label();

  /**
   * @return {@code true} once the manager is closed (shut down): it has no cache to administer.
   */
  boolean isClosed();

  /**
   * Lists the names of the caches of this manager. The work must not depend on the number of
   * entries of the caches.
   *
   * @return the names of the caches, empty when the manager is closed.
   */
  List<String> cacheNames();

  /**
   * Returns a cache of this manager by name, without creating it.
   *
   * @param name The name of the cache.
   * @return the cache, or {@code null} when the manager has no cache with this name or is closed.
   */
  @Nullable CacheAdminCache cache(String name);

  /**
   * The storage that the caches of this manager share, from its configuration: a cache whose tier
   * draws on a pool has a {@linkplain CacheTier#isPooled() pooled} tier.
   *
   * @return the pools, empty when the manager has none or the provider cannot tell.
   */
  default List<CacheTier> pools() {
    return List.of();
  }

}
