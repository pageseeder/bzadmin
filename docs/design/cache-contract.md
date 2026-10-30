# Cache management contract

This contract describes current cache administration. Future capabilities are tracked in the [roadmap](../../ROADMAP.md). Code: `org.pageseeder.bzadmin.cache`
(`CacheAdminProvider`, `CacheAdminManager`, `CacheAdminCache`, `CacheRegistry`, `CacheOutput`, `CacheKey`,
`CacheEntries`; adapters `Ehcache2Provider`, `JCacheProvider`, `CaffeineProvider`; generators below). Tests:
`CacheContractTest`, `CacheEntriesTest` (fixture provider `FixtureCaches`), the adapter runs `cacheTest*`
(`ProviderContract`), and the host tests `CacheAdminTest`, `NoCacheHostTest`.

## Purpose

Developers use the cache page to check that the caches of the application are **configured** and **operating**
correctly, and to **clear** a whole cache or **remove** one entry. The contract is:

- Browsing is **side-effect free** (next section): viewing a page, listing caches or listing entries never
  changes a cache, its entries or its statistics. In particular, no page or generator affects the hit count or
  another statistic by listing or looking up entries.
- The page shows **as much information as the libraries expose** without side effect: the common schema for
  every provider, plus what is specific to a library (configuration, extended statistics).
- Changes are **deliberate operations** only, and those may affect the cache: clear the entries, remove an
  entry, enable or disable statistics, reset statistics. POST, `OPERATE`, audited.
- When the page cannot see a cache, it says why, so that an empty page is never ambiguous.

## Side-effect free reads

Every GET service, and every method an adapter calls for one, satisfies all of (the operations, POST, are
exempt: they are what changes a cache):

- **No lifecycle change**: never creates or closes a manager or a cache (Registration below).
- **No access**: never reads an entry in a way the library counts as an access. An access changes hit/miss
  counts, eviction order (LRU/LFU) and access-based expiry (time-to-idle), so it would change what the page
  reports and how the cache behaves.
- **No configuration change**: never enables statistics or management, never registers an MBean, listener or
  statistics service. Statistics that are disabled are reported as disabled.
- **Bounded cost**: no whole-cache measurement or materialization on the summary or info pages; the work of a
  request is bounded (`build-contract.md`). The provider may still evict or expire entries on its own while a
  read runs, as it would without the admin pages.

Calls per library (an adapter uses only the left column on reads):

| Library | Allowed on reads | Never on reads |
| --- | --- | --- |
| EHCache 2 | `ALL_CACHE_MANAGERS`, `getCacheNames()`, `getEhcache(name)`, configuration getters, `getSize()`, tier sizes, statistics getters, `isKeyInCache(key)`, `getQuiet(key)`, `getKeys()` (entries view only) | `CacheManager.create()`/`getInstance()`/constructors, `addCache`, `get(key)`, `calculateInMemorySize()`, `setStatisticsEnabled` |
| JCache | registered managers, `getCacheNames()`, `getCache(name)`, `getConfiguration(…)`, the statistics MBean (attributes, `isRegistered`), `containsKey(key)`, `unwrap(…)` | `Caching.getCachingProvider()`, `getCacheManager()`, `createCache`, `get(key)`, `iterator()` (each entry read as a get), `enableStatistics`, the MBean's `clear` |
| Caffeine (through `Cache.unwrap`, `CaffeineNative`) | `estimatedSize()`, `policy().eviction()` getters (`getMaximum`, `isWeighted`, `weightedSize`) | `getIfPresent`, `asMap()` reads or views, `cleanUp()`, `setMaximum` |
| EHCache 3 (through `Cache.unwrap` and `CacheManager.unwrap`, `Ehcache3Native`) | `getRuntimeConfiguration()` resource pools and expiry policy; manager persistence root | creating a `StatisticsService`, `get(key)`, iteration, `updateResourcePools` |
| Caffeine native (`CaffeineProvider`) | registered caches, `estimatedSize()`, `stats()`, `policy()` getters (`eviction()` as above, `isRecordingStats()`, `expireAfterWrite()`, `expireAfterAccess()`, `expireVariably()`, `refreshAfterWrite()`), `asMap().keySet().iterator()` (entries view only) | `get`, `getIfPresent`, `getAll`, `asMap()` reads (`get`, `getOrDefault`…) and the other views (`values()`, `entrySet()`), `cleanUp()`, `setMaximum`, `setExpiresAfter` |

A library call not in the table is checked against these rules (and added to the table) before an adapter uses
it on a read.

Caffeine's key iterator: on a
bounded cache, `asMap().keySet().iterator()` walks the internal map (`BoundedLocalCache.KeyIterator` over
`EntryIterator`), reading each node's key and value without `afterRead`: no statistic is recorded and neither the
frequency sketch nor the access order or access time changes, so eviction and expiry after access are unaffected.
An entry that has expired or been collected is skipped and `scheduleDrainBuffers()` is called, which only runs
Caffeine's own maintenance sooner (the entry is then counted as evicted, as it would be anyway). On an unbounded
cache it is the key iterator of the `ConcurrentHashMap`. `CaffeineProviderTest.listsKeysWithoutAccess` checks the
statistics and the age of an entry after listing. Other `asMap()` views are not allowed: `entrySet()` returns
entries whose `setValue` writes through, and `asMap().get` is an access.

## Identity

- A cache is identified by **provider + manager + cache name**, never by its name alone: the same name can exist
  in several managers and in several providers. Requests name all three (`provider`, `manager`, `cache`
  parameters); a missing one is 400 (`missing-parameter`).
- **Provider ID**: chosen by the overlay or the host, unique in the registry, `[a-z0-9][a-z0-9-]{0,31}`
  (`ehcache2`, `jcache`). Registering an invalid or used ID fails (`IllegalArgumentException`).
- **Manager ID**: chosen by the provider, unique among its managers and stable while the manager lives, at most
  128 characters without control characters. Not a secret. A manager whose ID the provider lists twice, or that
  is invalid, is listed with `problem="ambiguous-manager"` and no cache, and is never resolved (409).
- **Labels** (provider, manager) are for display only and may repeat; they never resolve anything.
- Cache names are compared exactly (case-sensitive), as the providers do.

## Registration and lifecycle

- `CacheRegistry.global()` is the registry of the admin pages. Its providers are those the overlay **detects**
  (first) and those the host **registers** (`register(provider)`, `unregister(id)`), in that order.
- Library presence does not identify live managers. Providers list the managers they are bound to on every
  request; the registry keeps no manager, so a manager created, closed or registered later is reflected.
- The admin code **never creates or closes** a cache manager or a cache: `cache(name)` returns `null` for an
  unknown name (404 `unknown-cache`), never creates it; no default manager factory is called for discovery;
  unregistering a provider does not close anything. The host creates its managers (often lazily, on the first
  use of a cache); the `test-host` wrapper and the test hosts create theirs in a `ServletContextListener`.
- Per provider:
  - EHCache 2 (detected when the host has it): the live managers of `CacheManager.ALL_CACHE_MANAGERS`, which
    lists managers without creating one (`CacheManager.getInstance()` creates the singleton: not used). Manager
    ID: the manager's name, unique in a JVM since 2.5.
  - JCache: only managers the host registers explicitly, with an ID of its choice; `Caching.getCachingProvider()`
    and `getCacheManager()` are never called by the overlay.
  - Native Caffeine (detected when the host has Caffeine; it does not need the JCache API): only the caches that
    the host registers with `CaffeineCaches.register(manager, name, keyType, cache)`, under managers it names
    (Caffeine has no manager). A manager is listed while it has a registered cache and is never closed; a cache
    that the host unregisters after a lookup fails its operations with `IllegalStateException` (409 `closed`).
  - Other libraries: a `CacheAdminProvider` that the host registers.
- **Closed managers** stay listed while the provider knows them (`status="closed"`, no cache); a request for one
  of their caches is 409 (`closed`). An operation on a cache closed between the lookup and the call throws
  `IllegalStateException`, which the generators report as 409 `closed`.
- A provider that throws while listing its managers is listed with `failed` (the exception class, no message) and
  logged; the other providers are still listed. Unexpected failures of an operation stay visible (500).

## Capabilities

Listing managers and cache names is always possible. Per cache (`CacheAdminCache.capabilities()`):

| Capability | Basic | Meaning |
| --- | --- | --- |
| `configuration` | yes | Safe configuration as name/value text (provider names, display only) |
| `statistics` | yes | Available statistics; each metric may still be unavailable |
| `clear` | yes | Remove every entry (semantics below) |
| `statistics-toggle` | no | Enable/disable the collection of statistics (EHCache 2.6 and earlier, JCache) |
| `statistics-reset` | no | Reset the statistics: the counts start again from zero, the entries are kept (JCache while statistics are enabled and the MBean identifies the cache; native Caffeine while statistics are recorded) |
| `entries` | no | List keys of supported types, bounded |
| `remove-entry` | no | Remove one entry by a key of a supported type |

Basic administration is listing, configuration, statistics and clear: every adapter of
the overlay supports them. An operation without the capability is 501 (`unsupported`); the method throws
`UnsupportedOperationException`.

## Semantics

- **Clear** (labelled "Clear entries"): the provider's bulk removal of the entries of the cache in its manager,
  without loading or writing through. It does not reset the statistics (`statistics-reset` does): the entry
  count drops, the other counts carry on.
  EHCache 2: `Ehcache.removeAll()` (notifies the cache's listeners, and replicates when the cache is configured to).
  JCache: `Cache.clear()` (no listener, no `CacheWriter`, no statistics). Never `removeAll()` in JCache, which calls
  the writer. Native Caffeine: `invalidateAll()` (the removal listener, cause `EXPLICIT`; Caffeine 3 has no writer). "Clear all" is a clear of each cache of each addressable, active manager, reported per cache.
- **Scope** (`scope`): `local`, `clustered` (clustered or replicated: a clear can affect other nodes) or `unknown`.
  The page labels non-local clears.
- **Remove entry**: one key of an explicit type (`CacheKey`: text up to 1024 characters, integer, long), parsed
  from its type and text (`key`, `key-type` parameters), never from `toString()` of an arbitrary key, never
  deserialized; typed, so the long 42 is not the integer 42 or the text "42". EHCache 2: `Ehcache.remove(Object)`
  (listeners, replication; no writer), not for clustered caches. JCache: `Cache.remove(key)` (listeners), only
  without write-through (JCache would delete through the writer) and for caches whose key type is text, integer,
  long or `Object`; a key of another type than the cache's is `invalid-key` (400). Native Caffeine:
  `asMap().remove(key)` (the removal listener), for the same key types as JCache, from the key type the host
  registered. `removed="false"` when there was no such entry.

- **Entries** (`CacheEntries.scan`): a range of positions (`offset`, `limit` ≤ 1000, 100 by default) in the
  provider's order, reading at most 10 000 keys per request and no value; keys of supported types as typed text,
  others counted by class name (never as text; text keys over 1024 characters as `java.lang.String (too long)`).
  EHCache 2 (`getKeys()`, which copies the key list: known limitation, so only on the entries view), not for
  clustered caches. Native Caffeine: `asMap().keySet().iterator()`, weakly consistent and lazy, so the work is
  bounded by the scan (see the key iterator above). JCache keys are not listed: a JCache iterator reads each entry as a get (access expiry,
  statistics); an entry can still be removed by an explicitly typed key.
- **Statistics toggle**: an operation, never a side effect of a read. EHCache 2.6:
  `setStatisticsEnabled`; JCache: `CacheManager.enableStatistics`.
- **Statistics reset**: an operation, never a side effect of a read; the entries are not changed. EHCache 2: never
  (deprecated: no code for 2.6's `clearStatistics()`, and 2.7+ cannot reset its counters, always collected from
  the creation of the cache). JCache: the `clear` operation of the statistics MBean (EHCache 3, Caffeine), only while
  it is registered (statistics enabled) and identifies the cache (unique manager URI); otherwise `unsupported`.
  Native Caffeine: its counters cannot be reset, so the reset keeps a snapshot of `stats()` with the registration,
  and the statistics are those since (`CacheStats.minus`); Caffeine's own counters are unchanged, and registering
  the cache again starts from its creation.
- **Keys are shown as they are** (no redaction), to `VIEW` users: like log content, cache keys are the host's data.

## Configuration

Name/value text, display only (`configuration` capability); names are the library's own, so a developer can
match them with the library's documentation and the host's configuration files.

- **Effective configuration** of the cache, as the library reports it at run time (current: limits per tier,
  expiry, eviction policy, persistence, transactions, replication for EHCache 2; types, store-by-value,
  read/write-through, statistics, management and listener count for JCache).

- **Tiers** (`CacheAdminCache.tiers()`, `<tier>`): where a cache keeps its entries and its limit there, from the
  heap to the disk, as configured (never measured). A tier has at most one limit: `max-entries`, `max-bytes` or
  `max-weight` (the unit of the host's weigher); `pooled="true"` when the limit is a pool of the manager; none when
  unbounded (`Long.MAX_VALUE` is no limit). A disk tier has its directory (`path`) when known, and
  `persistent="true"` when kept across restarts. Unknown tiers are an empty list, never unbounded.
  - EHCache 2: the heap; the off-heap store with `overflowToOffHeap`; the local disk store (`localTempSwap`,
    `localRestartable`, or the legacy `overflowToDisk`/`diskPersistent`) in the manager's disk store path (the
    temporary directory by default). A cache limit in bytes first, then a pool of the manager, then entries.
  - JCache: EHCache 3's resource pools (heap, off-heap, disk; clustered resources are not listed), the disk in the
    manager's persistence directory; Caffeine's maximum (entries, or weight with a weigher) on the heap, unbounded
    without one. Other implementations: unknown.
- **Pools** (`CacheAdminManager.pools()`, `<pool>`): the storage the caches of a manager share. EHCache 2: the
  manager's `maxBytesLocalHeap`, `maxBytesLocalOffHeap`, `maxBytesLocalDisk`.
- **Capacity** (`CacheCapacity`, `<cache-capacity>` on the list of every cache): the limits in bytes of all the
  caches listed added up by kind of storage (disks by volume, `FileStore`), against what is available
  (`CacheResources`): the maximum heap (`Runtime.maxMemory()`), the maximum direct memory (the
  `MaxDirectMemorySize` option of the HotSpot diagnostic MBean read by name, the maximum heap when it is 0, absent
  on another JVM), the usable space of the volume of a disk tier (or of its nearest existing parent). A pool is
  counted once and its pooled tiers not at all; a tier limited in entries or by weight is counted as `unsized`, never
  converted into bytes. `ratio` is the limits over what is available. Limits, not use: the page says so, and that the
  free space of a volume excludes what the caches already store there.
- **Warnings**: `<warning code="…"/>` on a cache, computed from its configuration, its statistics and the
  registry, never by changing anything; the sizing warnings also on `cache-capacity`, for all the caches. A value the
  library does not report gives no warning (unknown is not unbounded). The statistics warnings (`CacheWarning`)
  are listed under Statistics. Each warning with a ratio has it (`value`) and its `threshold`.

  | Code | When |
  | --- | --- |
  | `unbounded` | Every tier of the cache is unbounded (EHCache 2: `maxEntriesLocalHeap="0"` without byte limit or pool; Caffeine without maximum) |
  | `heap-over-allocated` | Heap limits in bytes from 0.5 of the maximum heap (a cache's own, or all with the pools) |
  | `off-heap-over-allocated` | Off-heap limits from 0.8 of the maximum direct memory, which the JVM also uses for I/O buffers |
  | `disk-over-allocated` | Disk limits on a volume from 1.0 of its usable space (the worst volume for all the caches) |

## Statistics

- **Whether statistics are collected** (`enabled`): `true` or `false` when the provider tells, absent when it
  cannot. EHCache 2.6: the live statistics; EHCache 2.7+: absent (always collected, and no `statistics-toggle`).
  JCache: the complete configuration, or, when the implementation gives none (EHCache 3 for a cache made from
  its own configuration, which enables statistics by default), whether the statistics MBean is registered: JSR-107
  registers it exactly while statistics are enabled. Native Caffeine: whether the cache was built with
  `recordStats()` (`policy().isRecordingStats()`), set when it is built, so no `statistics-toggle`. The page shows
  absent as "Always on" for a cache without `statistics-toggle`, "Unknown" otherwise, and then offers both enable
  and disable.
- `CacheStatistics`: the metrics a provider supplies (`entries`, `heap-entries`, `off-heap-entries`,
  `disk-entries`, `heap-bytes`, `off-heap-bytes`, `disk-bytes`, `hits`, `misses`, `puts`, `removals`, `evictions`,
  `expirations`) and `enabled` (absent when unknown). JCache `entries`: only from the implementation, counted in
  constant time and whether statistics are collected or not (Caffeine's `estimatedSize()`, which may count expired
  entries not yet removed; none for EHCache 3). Native Caffeine: `entries` from `estimatedSize()`, and with
  `recordStats()` `hits`, `misses` and `evictions` from `stats()` (Caffeine's evictions include the entries that
  expired or were collected; it counts no puts or removals). **A metric that is not supplied is absent, never zero**;
  negative values (the "not available" of providers) are dropped. `hit-ratio` is derived from hits and misses when
  both are available and their sum is not zero.
- **Average times** (`CacheTiming`): `average-get-time`, `average-put-time`, `average-remove-time`, metrics with
  `unit="ms"` and a decimal value (the only metrics that are not counts), over the same period as the counters.
  Absent unless the operation was counted at least once (get: hits plus misses; put: puts; remove: removals),
  since JSR-107 reports 0 for an average without any operation. JCache: the MBean's `AverageGetTime`,
  `AveragePutTime`, `AverageRemoveTime`, read in the same `getAttributes` call as the counters (microseconds,
  converted, kept to the nanosecond). The page shows them in microseconds below one millisecond; the recent
  rates ignore them (`metric:not([unit])`), since an average that decreases is not a reset. A slow get on a cache
  with a disk tier is a sign that the heap tier is too small.
- **Derived values**, on `statistics`, each absent when one of its inputs is:
  - `entry-limit`: the maximum number of entries of the whole cache (`CacheAdminCache.entryLimit()`): by default
    the entry limit of the last tier (the one that holds every entry), only when no tier is limited in bytes, by
    weight or by a pool. EHCache 2: `maxEntriesLocalDisk` with a local disk tier, otherwise
    `maxEntriesLocalHeap`. JCache: EHCache 3 with a heap tier only, Caffeine with a maximum size.
  - `fill-ratio`: entries over `entry-limit`. Low for a long time: the cache was given more than it needs.
  - `eviction-ratio`: evictions over puts; high when entries are evicted before they are used again (the cache
    is too small).
  - `expiration-ratio`: expirations over puts; high when the time to live or to idle is shorter than the time
    between requests for the same entry.
- **Statistics warnings** (`CacheWarning`), each with the ratio it checks (`value`) and its `threshold`, so that the
  page shows why. Only after a minimum sample of 1000 requests (hits and misses) or puts, so that a cache that has
  just started gives none:

  | Code | When |
  | --- | --- |
  | `low-hit-ratio` | `hit-ratio` below 0.5 |
  | `high-eviction` | `eviction-ratio` from 0.5, and `hit-ratio` below 0.8 or unavailable |
  | `high-expiration` | `expiration-ratio` from 0.5, and `hit-ratio` below 0.8 or unavailable |

  The counters usually run from the creation of the cache: a warning describes that period, which the page says
  next to the recent rates.
- **Rates** are computed by the page, never kept on the server: the cache information page reads the XML of
  `GetCacheInfo` from a handler service (`/bzadmin/cache/info/sample.xml`: no envelope, no XSLT) every 5 seconds while it is visible (`poller`) and shows requests, hit ratio, puts,
  evictions, expirations and removals per second (or per minute) over the last minute (`counterWindow` in
  `rates.js`). A counter that decreases (statistics reset) starts the window again.
- Statistics never read entries: counts are the provider's own counters; byte sizes only when the provider tracks
  them (no `calculateInMemorySize`-style measurement).

## Output (provider-neutral)

`CacheOutput` writes the same schema for every provider. Library-specific configuration is represented as name/value properties.

```xml
<cache-providers>
  <cache-provider id="jcache" label="JCache" [failed="…"]>
    <cache-manager id="main" label="main" status="active|closed" [problem="ambiguous-manager"] total="2" truncated="false">
      <pool type="heap" max-bytes="268435456"/>
      <cache name="PSML" scope="local" capabilities="configuration statistics clear">
        <statistics [enabled="true"] [hit-ratio="0.5"] [entry-limit="1000"] [fill-ratio="0.1"] [eviction-ratio="0"]
                    [expiration-ratio="0"]>
          <metric name="hits" value="1"/>
        </statistics>
        <tier type="heap" max-entries="1000"/>
        <tier type="disk" max-bytes="8589934592" persistent="true" path="/var/cache/app"/>
        <warning code="high-eviction" value="0.62" threshold="0.5"/>
      </cache>
    </cache-manager>
  </cache-provider>
  <cache-capacity>
    <resource type="heap" limits="268435456" available="1073741824" ratio="0.25" unsized="3"/>
    <resource type="disk" limits="8589934592" available="4294967296" total="107374182400" ratio="2.0" path="/var/cache/app" unsized="0"/>
    <warning code="disk-over-allocated" value="2.0" threshold="1.0"/>
  </cache-capacity>
</cache-providers>

<cache-info provider="jcache" manager="main">
  <cache …>…</cache>
  <configuration><property name="…" value="…"/></configuration>
</cache-info>

<no-cache problem="unknown-cache" provider="…" manager="…" name="…"/>
```

- At most 1000 caches per manager (sorted by name ignoring case; `total`, `truncated`), as the other lists of data
  items (`build-contract.md`).
- Configuration values are cut at 1024 characters ("…"). Adapters supply text only: no secrets, no `toString()` of
  host objects (loaders, listeners, serializers).
- JSON: arrays `providers`, `managers`, `caches`, `metrics`; `capabilities` an array of names; `configuration` an
  array of `{name, value}`.

Problems and statuses: `missing-parameter` 400, `invalid-key` 400, `unknown-provider`/`unknown-manager`/`unknown-cache`
404, `ambiguous-manager`/`closed` 409, `unsupported` 501.

## Services

Parameters `provider`, `manager`, `cache` name the cache. Reads are GET (`VIEW`), operations POST (`OPERATE`, the
admin filter's CSRF check, audited).

| Service | Route | Generator | Notes |
| --- | --- | --- | --- |
| `bzadmin-cache-summary` | GET `/bzadmin/cache` | `ListCaches` | Every provider, manager and cache, with statistics |
| `bzadmin-cache-info` | GET `/bzadmin/cache/info` | `GetCacheInfo` | One cache with its configuration |
| `bzadmin-cache-info-sample` | GET `/bzadmin/cache/info/sample` | `GetCacheInfo` (handler) | The same, without envelope or XSLT, for the recent rates |
| `bzadmin-cache-entries` | GET `/bzadmin/cache/entries` | `ListCacheEntries` | `offset`, `limit`; `entries` capability |
| `bzadmin-cache-clear` | POST `/bzadmin/cache/clear` | `ClearCache` | One cache, or every cache without parameters (a partial identity is 400) |
| `bzadmin-cache-statistics` | POST `/bzadmin/cache/statistics` | `SetCacheStatistics` | `enable` (required); `statistics-toggle` capability |
| `bzadmin-cache-statistics-reset` | POST `/bzadmin/cache/statistics/reset` | `ResetCacheStatistics` | `statistics-reset` capability |
| `bzadmin-cache-entry-remove` | POST `/bzadmin/cache/entries/remove` | `RemoveCacheEntry` | `key` (required), `key-type` (`string` by default) |

The routes that named a cache by name only (`/bzadmin/cache/{name}/…`) are gone: they reached the cache of
`CacheManager.getInstance()` (creating that manager when absent) and could not tell managers apart.
