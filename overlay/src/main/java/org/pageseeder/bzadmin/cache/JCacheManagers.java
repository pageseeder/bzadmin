package org.pageseeder.bzadmin.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.cache.CacheManager;

/**
 * The JCache (JSR-107) cache managers of the host that the admin pages administer: only those that
 * the host registers here. JCache has no registry of the managers an application uses, and the
 * overlay never asks a caching provider for one ({@code Caching.getCachingProvider()} and
 * {@code getCacheManager()} create them).
 *
 * <pre>{@code
 * CacheManager manager = Caching.getCachingProvider(...).getCacheManager(...);
 * JCacheManagers.register("main", manager);
 * ...
 * JCacheManagers.unregister("main");
 * manager.close();
 * }</pre>
 *
 * <p>The identifier is the {@code manager} parameter of the admin requests and is shown on the
 * pages: at most 128 characters, no control characters. The admin pages never close a registered
 * manager; one that the host closes is listed as closed until it is unregistered.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class JCacheManagers {

  private static final Map<String, CacheManager> MANAGERS = new LinkedHashMap<>();

  private JCacheManagers() {
  }

  /**
   * Registers a cache manager of the host.
   *
   * @param id      The identifier of the manager in the admin pages.
   * @param manager The cache manager.
   * @throws IllegalArgumentException if the identifier is invalid or used, or the manager is
   *   already registered.
   */
  public static synchronized void register(String id, CacheManager manager) {
    Objects.requireNonNull(manager, "manager");
    if (!CacheRegistry.isValidManagerId(id)) throw new IllegalArgumentException("Invalid cache manager identifier: " + id);
    if (MANAGERS.containsKey(id)) throw new IllegalArgumentException("Cache manager already registered: " + id);
    if (MANAGERS.containsValue(manager)) throw new IllegalArgumentException("Cache manager already registered as another identifier");
    MANAGERS.put(id, manager);
  }

  /**
   * Removes a cache manager; it does not close it.
   *
   * @param id The identifier of the manager.
   * @return {@code true} if it was registered.
   */
  public static synchronized boolean unregister(String id) {
    return MANAGERS.remove(id) != null;
  }

  /**
   * @return the managers registered, in the order of registration.
   */
  static synchronized List<Map.Entry<String, CacheManager>> list() {
    return MANAGERS.entrySet().stream()
        .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

}
