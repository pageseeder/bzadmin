# Integrating host caches

The Cache page administers the host's caches; no cache library is required. EHCache 2 (2.6 or
later, the host's own dependency) is detected, and its live cache managers are listed. JCache
managers (EHCache 3, Caffeine…) are listed only once the host registers them, since JCache has no
registry of the managers an application uses:

```java
CacheManager manager = Caching.getCachingProvider(...).getCacheManager(...);
JCacheManagers.register("main", manager);   // org.pageseeder.bzadmin.cache.JCacheManagers
// on shutdown
JCacheManagers.unregister("main");
manager.close();
```

Native Caffeine caches (built with `Caffeine.newBuilder()`) are listed once the host registers them,
under a manager of its choice; build them with `recordStats()` for their statistics:

```java
Cache<String, Page> pages = Caffeine.newBuilder().maximumSize(1000).recordStats().build();
CaffeineCaches.register("main", "pages", String.class, pages);   // org.pageseeder.bzadmin.cache.CaffeineCaches
// on shutdown
CaffeineCaches.unregister("main");
```

The overlay never creates or closes a cache manager. See
[cache contract](../design/cache-contract.md).


Register caches when the host initializes them and unregister them during shutdown before closing them. Registration does not transfer ownership to the overlay. See the contract for provider capabilities and supported key types.
