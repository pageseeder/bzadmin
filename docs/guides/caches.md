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

The entries view lists the keys of native Caffeine and EHCache 2 caches as they are, without redaction, to
every administrator, as the Logs page shows log content: keys are the host's data. Text, integer and long keys
are shown; keys of other types are only counted by class name. Do not register a cache whose keys are
secrets, such as session or API tokens, or wrap its keys in a class of the host so that they are only counted;
its entries can then only be cleared together. JCache keys are not listed: a JCache iterator reads each entry
as an access, which would change the statistics and the expiry of the cache.

Give each registered JCache manager its own URI. JCache names the statistics of a cache after the URI of its
manager and the name of the cache, so the statistics of managers that share a URI cannot be told apart, and the
page shows none for their caches. EHCache 3 is the exception: the page reads the statistics that each manager
keeps, so they only lack the average times.

With EHCache 3, statistics are always on: EHCache counts for every cache, and enabling JCache statistics only
registers their MBean, which adds the average times. The page also shows the entries of each tier and the bytes
they occupy (on the heap only when it is limited in bytes), and can reset the statistics at any time.

EHCache 2 statistics cannot be reset: 2.7 and later collect them from the creation of the cache, and 2.6's
`clearStatistics()` is deprecated. The recent rates on the cache information page show the current activity.

The page shows the limits of EHCache 2 caches in bytes, not the bytes they use: for a cache limited by a number
of entries, EHCache 2 would measure every entry to compute them, which would slow down the page and the
cache.
