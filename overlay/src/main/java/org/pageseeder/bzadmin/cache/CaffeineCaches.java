package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.benmanes.caffeine.cache.Cache;

import org.jspecify.annotations.Nullable;

/**
 * The native Caffeine caches of the host that the admin pages administer: only those that the host
 * registers here. Caffeine has no registry of the caches an application builds, and no manager: the
 * host groups its caches under managers of its choice, so that a cache is named by provider,
 * manager and cache like the others.
 *
 * <pre>{@code
 * Cache<String, Page> pages = Caffeine.newBuilder().maximumSize(1000).recordStats().build();
 * CaffeineCaches.register("main", "pages", String.class, pages);
 * ...
 * CaffeineCaches.unregister("main", "pages");
 * }</pre>
 *
 * <p>The manager identifier is the {@code manager} parameter of the admin requests and is shown on
 * the pages: at most 128 characters, no control characters. A manager is listed while it has a
 * registered cache. Build a cache with {@code recordStats()} for its hits, misses and evictions;
 * a {@code LoadingCache} is a cache, and an {@code AsyncCache} is registered with its
 * {@code synchronous()} view.
 *
 * <p>The key type is the type of the keys of the cache: an entry can be removed by its key when it
 * is text, an integer or a long (or {@code Object}, for keys of any type).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CaffeineCaches {

  /** The registered caches by manager, in the order of registration. */
  private static final Map<String, Map<String, CaffeineProvider.NativeCache>> MANAGERS = new LinkedHashMap<>();

  private CaffeineCaches() {
  }

  /**
   * Registers a cache of the host.
   *
   * @param manager The identifier of the manager in the admin pages.
   * @param name    The name of the cache in the manager.
   * @param keyType The type of the keys of the cache.
   * @param cache   The cache.
   * @param <K>     The type of the keys.
   * @throws IllegalArgumentException if the manager identifier is invalid, the name is empty or used
   *   in the manager, or the cache is already registered.
   */
  public static synchronized <K> void register(String manager, String name, Class<K> keyType, Cache<K, ?> cache) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(keyType, "keyType");
    Objects.requireNonNull(cache, "cache");
    if (!CacheRegistry.isValidManagerId(manager)) throw new IllegalArgumentException("Invalid cache manager identifier: " + manager);
    if (name.isEmpty()) throw new IllegalArgumentException("Empty cache name");
    Map<String, CaffeineProvider.NativeCache> caches = MANAGERS.get(manager);
    if (caches != null && caches.containsKey(name)) throw new IllegalArgumentException("Cache already registered: " + manager + "/" + name);
    for (Map<String, CaffeineProvider.NativeCache> registered : MANAGERS.values()) {
      for (CaffeineProvider.NativeCache other : registered.values()) {
        if (other.isFor(cache)) throw new IllegalArgumentException("Cache already registered as another name");
      }
    }
    MANAGERS.computeIfAbsent(manager, id -> new LinkedHashMap<>()).put(name, new CaffeineProvider.NativeCache(name, keyType, cache));
  }

  /**
   * Removes a cache; it does not clear it. An operation that the admin pages started on it fails.
   *
   * @param manager The identifier of the manager.
   * @param name    The name of the cache.
   * @return {@code true} if it was registered.
   */
  public static synchronized boolean unregister(String manager, String name) {
    Map<String, CaffeineProvider.NativeCache> caches = MANAGERS.get(manager);
    if (caches == null) return false;
    CaffeineProvider.NativeCache removed = caches.remove(name);
    if (caches.isEmpty()) MANAGERS.remove(manager);
    if (removed == null) return false;
    removed.unregistered();
    return true;
  }

  /**
   * Removes every cache of a manager; it does not clear them.
   *
   * @param manager The identifier of the manager.
   * @return {@code true} if it had a registered cache.
   */
  public static synchronized boolean unregister(String manager) {
    Map<String, CaffeineProvider.NativeCache> caches = MANAGERS.remove(manager);
    if (caches == null) return false;
    for (CaffeineProvider.NativeCache cache : caches.values()) cache.unregistered();
    return true;
  }

  /**
   * @return the identifiers of the managers with a registered cache, in the order of registration.
   */
  static synchronized List<String> managers() {
    return new ArrayList<>(MANAGERS.keySet());
  }

  /**
   * @param manager The identifier of a manager.
   * @return the names of its caches, in the order of registration; empty when it has none.
   */
  static synchronized List<String> cacheNames(String manager) {
    Map<String, CaffeineProvider.NativeCache> caches = MANAGERS.get(manager);
    return caches != null ? new ArrayList<>(caches.keySet()) : List.of();
  }

  /**
   * @param manager The identifier of a manager.
   * @param name    The name of a cache.
   * @return the cache, or {@code null} when none is registered with this name in the manager.
   */
  static synchronized CaffeineProvider.@Nullable NativeCache cache(String manager, String name) {
    Map<String, CaffeineProvider.NativeCache> caches = MANAGERS.get(manager);
    return caches != null ? caches.get(name) : null;
  }

}
