/**
 * Administration of the caches of the host application, whatever their library: the
 * provider-neutral contract ({@link org.pageseeder.bzadmin.cache.CacheAdminProvider}), the
 * {@link org.pageseeder.bzadmin.cache.CacheRegistry} of providers, the optional adapters (EHCache
 * 2, JCache, native Caffeine) and the generators.
 *
 * <p>See {@code docs/implementation/cache-contract.md}.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.cache;
