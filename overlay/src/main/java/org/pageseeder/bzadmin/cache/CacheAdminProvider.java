package org.pageseeder.bzadmin.cache;

import java.util.List;

/**
 * A cache library whose live, host-owned cache managers the admin pages can administer: EHCache 2,
 * JCache, or a provider that the host registers.
 *
 * <p>A cache is identified by its provider, its manager and its name, never by its name alone: the
 * same name can exist in several managers. A provider lists the managers it is bound to; it never
 * creates a cache manager or a cache to list them, and never closes one (see
 * {@code docs/implementation/cache-contract.md}).
 *
 * @see CacheRegistry
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public interface CacheAdminProvider {

  /**
   * @return the identifier of the provider, unique in the registry: lower-case letters, digits and
   *   {@code -}, at most 32 characters (for example {@code ehcache2}, {@code jcache}).
   */
  String id();

  /**
   * @return the name of the provider for display (for example {@code EHCache 2.10.9.2}).
   */
  String label();

  /**
   * Lists the cache managers of this provider, closed ones included while the provider still knows
   * them; called on every request, so a manager that is created or closed later is reflected.
   *
   * @return the cache managers, in the order to display them; empty when there are none.
   */
  List<CacheAdminManager> managers();

}
