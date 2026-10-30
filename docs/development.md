# Developer reference

### Gradle modules

- `overlay/` — the overlay (`java-library`), with its files in `src/main/resources/`, organised as a JAR overlay:
  - `META-INF/berlioz/services.xml` — the services, which Berlioz discovers on the classpath
  - `META-INF/berlioz/xslt/html/bzadmin.xsl` and `bzadmin/` — the stylesheets; `processResources` replaces `@VERSION@`
  - `META-INF/resources/` — JS, CSS, fonts and images, which the servlet container serves from the context root

  Its Java code (`src/main/java`, Java 11) is generators: XSLT documentation in `org.pageseeder.bzadmin.xslt`, and the Berlioz option catalog in `org.pageseeder.bzadmin.berlioz`. Its only published dependency is `pso-berlioz` (no Bastille dependency); the host selects the XSLT processor, the logging backend and any cache library. `./gradlew :overlay:checkDependencyBoundary` (part of `check`, and run before publishing) fails on any other module in the runtime graph or POM, or a reference to Bastille or Cobble classes in the JAR.
- `test-host/` — the wrapper Berlioz application for local development (`war` + Gretty): `SecurityHeadersFilter`, `web.xml`, default services, PSML, logging and caching config, plus the runtime libraries (Saxon, logback, kickstart, ehcache…). The app gets services and XSLT from the classpath. Gretty puts `overlay/build/resources/main` on it as a directory, so after editing XSLT or services run `./gradlew :overlay:processResources` (or keep `./gradlew -t :overlay:processResources` running); with XSLT caching off, changes show without a restart. Jetty only serves `META-INF/resources` from JARs, so Gretty serves `overlay/src/main/resources/META-INF/resources` as an extra resource base (nothing is copied into the WAR). It applies to `appRunJarOverlay` too, before the JAR's resources; the host tests check the assets served from the JAR.
- `appRunJarOverlay` (group `pageseeder`) is a Gretty `AppStartTask` that runs the `war` (the `bzadmin` JAR in `WEB-INF/lib`) instead of the in-place webapp. It deletes Gretty's `webapps-exploded` folder first, because Gretty reuses it between runs.
- No host stylesheet stub: since Berlioz 0.14.4, when the group stylesheet `WEB-INF/xslt/html/{GROUP}.xsl` does not exist, Berlioz uses `META-INF/berlioz/xslt/html/{GROUP}.xsl` from the classpath; a file in the host still overrides it. The XSLT page lists the host's `WEB-INF/xslt` folder merged with the classpath stylesheets under `META-INF/berlioz/xslt/`, mapped the same way.
- The root `build.gradle.kts` sets `group` and `version` for both modules; repositories are in `settings.gradle.kts` (Maven Central and Terracotta, no `mavenLocal()`).
- Supported runtimes, published dependencies (the host owns every provider), the release sequence and the bounded-work policy for generators: `docs/design/build-contract.md`.

### Request flow

```
Browser → Berlioz servlet → Generator(s) → XML → XSLT → HTML
```

Berlioz routes URLs to **services** defined in `overlay/src/main/resources/META-INF/berlioz/services.xml`. Each service runs one or more **generators** (Java classes of the overlay, or of `pso-berlioz` through `org.pageseeder.bzadmin.guard`) that produce XML fragments. The XSLT layer in `bzadmin.xsl` (and its included sub-stylesheets) transforms the combined XML into the final HTML page. A service polled by the frontend for data can instead declare one `<handler>`: Berlioz writes its output only (XML or JSON, never HTML), without the envelope or XSLT, whatever the extension (the live samples `/bzadmin/system/cpu/sample` and `/bzadmin/system/runtime/sample`, and `/bzadmin/cache/info/sample` for the recent rates of a cache). It is a separate service from the page's, its class is guarded like any generator (`DirectInvocationTest` reads `<handler>` too), and `AccessPolicyTest.HANDLERS` lists it for the access tests (not `AdminRoutes`, whose routes have an HTML page).

### XSLT structure

All in `overlay/src/main/resources/META-INF/berlioz/xslt/html/`:

- `bzadmin.xsl` — root stylesheet: sets up HTML shell, navigation, and loads sub-stylesheets
- `bzadmin/_functions.xsl` — shared XPath functions (imported, so callable anywhere)
- `bzadmin/<section>.xsl` — one file per admin section (config, services, cache, files, xslt, application, system, logs)

The `@VERSION@` token in XSL files is replaced at build time by Gradle with the version from `version.txt`.

### Frontend structure

- `frontend/src/js/bzadmin.js` — webpack entry point; imports the CSS, registers the custom elements, and imports all page modules
- `frontend/src/js/lib/` — shared helpers: `dom.js` (`on` delegated listener, `fadeOut`, `dim`, `flash` status message) and `http.js` (`request`, `fetchDocument`, `post`, `swapFragment`, and `reloadFragment`, which wraps `swapFragment` with `dim` and an error `flash`), `poll.js` (`poller`: live updates that never overlap, pause while the page is hidden and can stop after a `duration` (the System page's live charts: 5 minutes); tested by `poll.test.js`), `rates.js` (`counterWindow`: rates of cumulative counters over a sliding window; tested by `rates.test.js`), `cpu.js` (`cpuUsage`: CPU usage between two `<cpu-sample>`s; tested by `cpu.test.js`), `threads.js` (`threadDeltas`: CPU share and allocation rate of each thread between two refreshes of the threads table; tested by `threads.test.js`), and `berlioz-path.js` (`toBerliozPath`: a URL reduced to the path Berlioz matches, without origin, context path, prefix or, when mapped by extension, the extension; tested by `berlioz-path.test.js`), `xml-tokens.js` (`tokenize`: XML source split into typed tokens for highlighting, tolerant of truncated or malformed sources; tested by `xml-tokens.test.js`), and `reload.js` (`reloadScopes`: the scopes Berlioz lists in the `X-Berlioz-Reload` response header; tested by `reload.test.js`). The HTTP helpers send `X-Requested-With: XMLHttpRequest` (the admin filter's CSRF check), post `URLSearchParams` (form-encoded, not `FormData`) and throw on non-2xx responses. `fetchDocument` and `postControl` parse with `XMLHttpRequest` (`responseType = 'document'`), not `DOMParser.parseFromString`, a Trusted Types sink.
- `frontend/src/js/components/` — custom elements (light DOM, `export default class`, registered in `bzadmin.js`): `<svg-icon>`, `<sortable-table>` (wraps a `<table>`; `replaceRows(tbody)` swaps in fetched rows and keeps the current sort), `<pie-chart>`, `<line-chart>`, `<xml-source>` (wraps `<pre><code>` with XML text and highlights it with spans built from `tokenize`; sources over 512K characters stay plain). Chart colours come from `--chart-*` custom properties, highlighting colours from `--code-*`.
- `frontend/src/js/pages/bzadmin-*.js` — per-page JS; each file runs only if `document.querySelector('.page-bzadmin-<section>')` matches (the body class comes from the Berlioz service id)
- `frontend/src/css/bzadmin.css` — plain CSS entry point (no Sass; uses native nesting). css-loader inlines its `@import`s into the single bundle. Each import is placed in a cascade layer: `tokens` (settings, dark mode), `reset`, `base` (element defaults), `lib` (`css/lib/` grid and UI, which replaced Foundation), `layout`, `components`, `pages` (`css/bzadmin/`). Fonts are unlayered.
  - The layer order is declared in `layers.css`, which must stay the first import: css-loader hoists imported content above the importing file's own rules, so an `@layer` statement in `bzadmin.css` itself would end up last.
  - Later layers win regardless of specificity, so element-level defaults go in `base` and class rules that override `lib` go in `components` or above.
  - Native nesting can't join selector names (`&-title`); write such selectors out in full.
  - Colours: use the role tokens in `settings.css` (`--surface`, `--background`, `--border`, `--text`, `--text-strong`, `--text-muted`, `--text-on-color` for text on blue or status backgrounds, `--link`/`--link-visited`, `--accent`/`--accent-strong` for blue text and icons that are not links), or one of the neutral greys (`--white`, `--super-light-neutral`, `--lightest-neutral` … `--darkest-neutral`, `--black`). Dark mode reverses the greys in `darkmode.css`, so `--white` becomes dark grey there: use `--surface` or `--text-on-color` rather than `--white` directly, and the roles follow automatically.

The build outputs a single versioned bundle: `bzadmin-<version>.min.js` and `bzadmin-<version>.min.css`.

The frontend is plain ES modules with no runtime dependencies (no jQuery, no Babel/core-js; `browserslist` targets `baseline widely available`). Page module conventions:
- Attach handlers with `on(root, …)` on a container that is not replaced, so they survive fragment swaps; behaviour that must attach to swapped-in content belongs in a custom element.
- Never build HTML from server data with `innerHTML`; use `textContent`/`createElement` or move nodes from a parsed document.
- Use native `confirm()` for destructive actions and `flash()` instead of `alert()`.
- Error messages from `flash()` remain visible until dismissed or replaced by a result for
  the same anchor. Success messages remain temporary. Use `clearFlash(anchor)` after a
  successful retry with no status message; `reloadFragment()` does this automatically.
- Use autonomous custom elements only (no `is="…"`; Safari doesn't support customised built-ins).

The XSLT markup and the frontend are coupled: tables to sort are wrapped in `<sortable-table>` (`data-sort` on cells for sort keys, `data-sort="none"` on headers to exclude columns), trees are `<details>/<summary>` with a `caret` `<svg-icon>`, PSML and XSLT sources are `<xml-source><pre><code>`, the system page uses `<pie-chart values="…" labels="…">` and `<line-chart>`, and fragment swaps target ids such as `#services-details` and `#log-content`. Change both sides together.

`AdminFilter` adds bzadmin's own CSP (`AdminFilter.CSP`: `script-src 'self'; style-src 'self'`, no inline script or style) to the host's, so no `eval`-based code (webpack dev builds use `devtool: 'source-map'`), no `style` attribute or element (CSS is extracted in development too; set styles through `element.style`), and no Trusted Types sink (`innerHTML`, `DOMParser`): the `test-host` wrapper's own policy has `require-trusted-types-for 'script'; trusted-types 'none'`. Mutations are POSTs sent by script (`post`), never plain form submissions or links.

Library manifest buttons use “Show manifest of …” as their accessible names, matching
the visible Show label. Each manifest dialog references its visible heading with
`aria-labelledby` and a unique heading ID.

### Design tokens

`frontend/src/css/settings.css` defines the stable colour palette and shared design tokens;
`darkmode.css` overrides semantic colour and elevation roles for `prefers-color-scheme: dark`.
Use semantic roles (`--surface`, `--surface-subtle`, `--surface-selected`, `--text`,
`--text-muted`, field and border roles) in components rather than neutral palette shades.
Accent surfaces and table headings have separate roles so pale light-mode backgrounds do
not carry over into dark mode. Status text uses `--success-text`, `--warning-text` and
`--error-text`; filled success/error controls use their fill, hover and on-fill roles.
Chart and meter fills may use palette colours independently of text colours.
Dark mode uses a dark page canvas, lighter `--surface` content, `--surface-raised` for
panels and key metrics, and `--surface-floating` for dialogs and popovers. Panel shadows
use a restrained dark shadow and faint light edge. The page gradient is light-mode only.

Use `--space-1` through `--space-8` (2, 4, 8, 12, 16, 20, 24 and 32px at the default root
size) for padding, margins and gaps, with `--panel-padding` and `--table-cell-padding` for
shared component rules. `--page-gutter` and `--layout-gap` retain the header's 15px/30px
alignment. Text-relative spacing, dense log rows and artwork offsets can remain local.
Use `--radius-xs`, `--radius-sm` and `--radius-md` for 2px, 4px and 8px corners,
`--shadow-panel`/`--shadow-popover` for elevation, `--overlay-backdrop` for dialog scrims,
and `--duration-fast`/`--duration-normal` for transitions. Geometry stays the same in both
themes. Review both colour schemes when adding a foreground/background pair.

Use `--surface-hover`, `--field-label-background` and `--meter-track` for backgrounds
rather than reusing border colours. Primary actions and navigation hover have paired
foreground/background roles; disabled buttons use explicit text, background and border
roles without suppressing pointer hover explanations. Keyboard focus uses an offset
`:focus-visible` outline across links, controls and summaries. The shared page shell starts
with a “Skip to content” link, visible on focus, targeting the main landmark without JavaScript.
Selected primary and secondary navigation links expose `aria-current="page"`; secondary
view changes update it only after the new content loads successfully.
Unavailable key metric and cache table values use a dash with `role="img"` and `aria-label="Not available"`,
retaining the hover hint without relying on it for the accessible name.
The cache capacity table explains “Not sized in bytes” through a heading information
button and popover rather than a tooltip. Cache manager status icons use `role="img"`
and a status-specific accessible label, retaining the hover hint. Cache statistics explain
eviction and expiration ratios with information-button popovers beside their labels.
The Heap information popover explains Peak memory in the Memory areas and pools table;
the Peak column heading does not rely on a tooltip. The Threads table information popover
also defines Δ CPU, Δ Allocated and Blocked / waited instead of heading tooltips. Thread names
and Origin component names have no tooltips or help cursor; full thread details remain
available in the eye-button dialog.
System uptime uses `<time>` with an ISO duration, compact text hidden from assistive
technology, and expanded wording in visually hidden text. The time role is not named. It does not display a startup timestamp or tooltip.
Key metric meters use `aria-labelledby` to reference the visible label text, excluding
adjacent information buttons. Label IDs remain attached when live values change.
The landmark uses `tabindex="-1"` so it can receive focus without adding a tab stop. Paused chart legends and
secondary log text remain readable through text roles rather than opacity. Loading and
paused chart opacity are separate tokens. Reduced motion sets CSS transition durations
to zero; the JavaScript message fade also respects the user's motion preference.

Live CPU and memory charts use `aria-details` to reference their adjacent tables, which
show current, minimum and maximum values for each series. Memory history uses percent
of maximum heap; current memory is also shown in MB. Min/max cover only the retained
samples and exclude missing values. A short sample count (`history-target`) is connected
with `aria-describedby`. Clearing or resizing the buffer resets the count and extrema.
Neither the tables nor the count are live regions, avoiding announcements on every poll.
Sample counts do not imply elapsed time because polling can pause.

### Java

Every Java package declares `@org.jspecify.annotations.NullMarked` in `package-info.java`.
Reference types are non-null by default; mark parameters, return values, fields and type arguments
that can be null with JSpecify `@Nullable` (for nullable arrays, annotate the array dimension).
Keep these contracts consistent across interfaces, implementations and test fixtures. JSpecify is
a compile-only dependency for every Java source set and is not added to the runtime distribution.
Capture nullable accessor results once and check the local value before passing it to a non-null
API. For cache lookups, handle the captured `problem()` first, then use `requireValue()` to obtain
the successful value; `get()` remains nullable for callers that only need to inspect the result.

The overlay's own generators in `org.pageseeder.bzadmin.xslt`: `ListXsltFiles` lists the stylesheets of the XSLT folder and of JAR overlays (`source` is `file`, `classpath` or `override`), and `GetXsltDocumentation` documents one and every stylesheet it imports or includes, from the folder or the classpath. Like Berlioz, a `path` with no file in the XSLT folder refers to `META-INF/berlioz/<path>` on the classpath. It runs `xsltdoc.xsl` (taken over from Cobble 0.3.2, `xsltdoc` format) with a resolver set per transformation that only reads `.xsl`/`.xslt` files in the XSLT folder or on the classpath and denies other schemes; expected failures return 422 with an `xsltdoc-error` element. See `docs/design/xslt-resolution-decision.md`. The documentation describes the sources (`xsltdoc/@scope="source"`, stated on the page), not a compiled model: XSLT 3.0 packages (`stylesheet/@package`), modes, accumulators, `use-package` (not loaded), `expose`, the global context item, `visibility` and static parameters are documented; `use-when` and shadow attributes are not evaluated (an import with only `_href` has `followed="no"`); other XSL declarations are `<unsupported name>`. `ListXsltFiles` gives `copies` when several classpath locations provide a stylesheet (Berlioz and the documentation use the first). `GetXsltSource` (`/bzadmin/xslt/source?path=…`, linked from the documentation, also when it fails) returns the source as text (at most 1 MiB, `location` `file` or `classpath`); both resolve the path with `XsltStylesheet.locate`.

Log files (`org.pageseeder.bzadmin.log`, no logging backend needed to read them): `LogFiles` registers the directories and files of the global property `berlioz.admin.logs` (comma-separated, relative to the application data folder), or when it is not set the directories of logback's file appenders (through the logging adapter); `ListLogFiles` lists their `*.log` files (no links, hidden names or other files; registered files whatever their name) with an opaque `id` (16 hex digits of the SHA-256 of the real path); a request names a file by that ID or by `name` (`app.2026-10-23.log`, or `logs/app.2026-10-23.log` with its directory), which is only compared with the names of the files found, never resolved as a path. Past 1000 files in a directory, the most recent are listed, and the others can still be read by name or ID. `LogTail` reads bytes split at line feeds (UTF-8 safe), never past the size when opened; its `LogCursor` (`identity.position.line`) continues from a previous read, and `reset` tells the page when the file was rotated or truncated; `LogTail.readBefore` reads the lines before the first line of a read (`log/@before`, each line has its byte `position`). The raw log page has "Show earlier lines" above the lines (`#earlier-log`) and "Show new lines" below (`#follow-log`); it keeps at most 5000 lines, and when it drops some it moves the other cursor to the first line dropped or kept (`cursorAt` in `bzadmin-logs.js`). `LogReads` allows 4 reads at the same time.

Recent events (same package, no Bastille): logback is optional (`compileOnly`); `LoggingBackends` detects it through SLF4J and only then loads `LogbackBackend` (otherwise `UnsupportedLoggingBackend`: the page says so, `<no-recent-logs backend>` is 200 on GET and 501 on the threshold POST). `LogbackBackend` attaches a lock-free appender to the root logger (events the application logs, after its levels; not those of non-additive loggers), re-attaches it after a context reset, and is detached by `AdminFilter.destroy()` (`init()` starts it). `RecentEvents` is a ring of 256 events with a capture threshold (`EventLevel`, distinct from the loggers' levels: `GetRecentEvents` reports both, `threshold` and `root-level`); `RecentEvent` holds bounded text only. `SetRecentEventsThreshold` sets the threshold (`OPERATE`). The Logs page has two views (`bzadmin-secondary-nav`): Recent (`/bzadmin/logs`, with the logging framework and its version, `recent-logs/@version`) and Files (`?view=files`, also for `/bzadmin/logs/raw`), where a file is shown in `#log-content`.

Caches (`org.pageseeder.bzadmin.cache`, no Bastille; contract in `docs/design/cache-contract.md`): a cache is named by provider + manager + cache (parameters `provider`, `manager`, `cache`), never by name alone. `CacheRegistry.global()` asks the providers on each request: `CacheProviders` detects EHCache 2 (`Ehcache2Provider`: the live managers of `CacheManager.ALL_CACHE_MANAGERS`, never `getInstance()`; compiled against 2.6, newer statistics through `Ehcache2Statistics`) the JCache API (`JCacheProvider`: only the managers the host registers with `JCacheManagers.register(id, manager)`; statistics from the JSR-107 MBean) and native Caffeine (`CaffeineProvider`: only the caches the host registers with `CaffeineCaches.register(manager, name, keyType, cache)`, under managers it names; statistics from `stats()` with `recordStats()`, reset from a snapshot; keys listed with `asMap().keySet().iterator()`, which records no access), all `compileOnly`; hosts can register other `CacheAdminProvider`s. Capabilities per cache (`configuration`, `statistics`, `clear` are basic; `statistics-toggle`, `statistics-reset`, `entries`, `remove-entry` optional). Browsing and listing entries never change a cache or its statistics; only the POST operations do; an unavailable metric is absent, never zero. Keys are typed (`CacheKey`: text, integer, long; never `toString()` round trips), listed in bounded ranges (`CacheEntries`, EHCache 2 and native Caffeine). Statistics have derived ratios (`hit-ratio`, `fill-ratio` against `CacheAdminCache.entryLimit()`, `eviction-ratio`, `expiration-ratio`) and warnings after a minimum sample (`CacheWarning`). Caches report their configured tiers (`CacheTier`: heap, off-heap, disk, each limited in entries, bytes, weight or by a manager pool, `CacheAdminManager.pools()`); JCache reads them through `unwrap` (`JCacheNative`: `Ehcache3Native` resource pools, `CaffeineNative` maximum and estimated size; EHCache 3 and Caffeine `compileOnly`, loaded only when present); `CaffeinePolicy` reads a Caffeine maximum for both Caffeine adapters without linking the JCache API. `CacheCapacity` adds up the limits in bytes against the maximum heap, direct memory and volume space (`CacheResources`), for the sizing warnings and the overview's `<cache-capacity>`; the cache information page shows recent rates from successive reads of its XML (`rates.js`). Generators `ListCaches`, `GetCacheInfo`, `ListCacheEntries` (GET), `ClearCache`, `SetCacheStatistics`, `ResetCacheStatistics`, `RemoveCacheEntry` (POST); problems are `<no-cache problem>` (400/404/409/501). The admin code never creates or closes a manager or cache. Test host: `FixtureCacheManagers` (a `PSML` cache in the default and `fixture` EHCache 2 managers and the Caffeine JCache manager `fixture-caffeine`; the native Caffeine cache `pages` of the manager `fixture-native`, in `web.xml`, `web-container.xml`, `web-fallback.xml`); `warNoCacheHost` with `web-no-cache.xml` (`NoCacheHostTest`).

The PSML page (`/bzadmin/files`, `files/folders` for the tree, `files/document`) uses the overlay's read-only repository `org.pageseeder.bzadmin.psml` (`ListPsmlFolder`, `GetPsmlDocument`; no Bastille): one folder level per request in ranges, the tree loads folders on demand, sources are text. An expected failure is `no-psml-*` with 400/403/404; `bzadmin.xsl` shows the denial page only for forbidden content without an element.

Diagnostics ( fields in `docs/design/metadata-contract.md`): `org.pageseeder.bzadmin.application.GetApplicationSummary` (Application page and `/bzadmin/application/summary`: versions of bzadmin (`bzadmin.properties`, filtered by `processResources`), Berlioz, Java, the XSLT processor Berlioz uses (`XsltProcessorInfo`) and the servlet container (`Environment.getServerInfo()`); the detected integrations and the last load) and `org.pageseeder.bzadmin.system.GetSystemSummary` (System page and `/bzadmin/system/summary`: Java 11 management beans; JDK-specific CPU metrics by MBean attribute name in `OptionalMetrics`, never `com.sun.management`). Units are bytes and milliseconds; an unavailable value is absent, never zero. The System page has five views (`view`: `cpu` by default, `threads`, `memory`, `files`, `properties`, swapped into `#system-details`), each with at most four key metrics of its own (`#system-metrics`, read again with its Refresh button from the same view; the live charts update them) instead of a table repeating them: CPU (process CPU, load average, CPU time, GC time), Threads (live, peak, blocked, deadlocked: `threads/@deadlocked` of the summary), Memory (heap used and committed, non-heap, GC time), File system (free space), Properties (from Berlioz's `GetSystemProperties`, which writes `[REDACTED]` for sensitive values, shown as a `redacted` tag, and sorts by name: the count with the redacted ones, the OS (`os.name`; `os.version`, `os.arch`), Java (`java.version`; `java.vm.name`, `java.vendor`) and the encoding (`file.encoding`, a warning unless UTF-8; `user.timezone`)). A row of key metrics is a `<dl class="key-metrics">` of `bzadmin-key-metric` templates (`bzadmin.xsl`, shared with the Cache, Config (global properties), Services (except Lookup), XSLT, Application (Statistics, `#statistics-metrics`, replaced with the table by Refresh) and Logs (both views) pages). The Threads view lists the threads when it opens (a button without script; `/bzadmin/system/threads`, `#threads-details`) with the overlay's `ListThreads` (replaced Berlioz's): each thread has an `origin` guessed by `ThreadOrigin` (component from the JVM's names and groups, the bottom non-JDK stack frame, the thread name, else the context class loader; role acceptor, selector, request, idle, running, internal), its `activity` (top non-JDK frame), `lock` and owner, blocked/waited counts, CPU times and allocated bytes (`ThreadDetails`; allocation through the threading MBean by operation name), without stacks; the eye button shows one thread with its stack (`GetThreadInfo`, `/bzadmin/system/thread?id=`, `#thread-info`) in `#thread-dialog`. Its live CPU chart polls Berlioz's `GetCpuSample` (`/bzadmin/system/cpu`: cumulative process CPU and GC time, uptime, machine load) and computes the machine, process and GC usage between successive samples (`cpu.js`, tested by `cpu.test.js`).

Overlay replacements with the same XML as the generators they replace, adding bounds or redaction: `org.pageseeder.bzadmin.log.GetLogEntries` (raw log: bounded lines/bytes/line length, a `cursor` for the lines added since, a `before` cursor for the lines before). Overlay generators implement Berlioz's `Generator` and write to `OutputWriter`, so they have XML and JSON output (arrays with `ContextOption.JSON_ONLY` keep the XML without a wrapper); only `GetXsltDocumentation`, whose output is an XSLT result, is an `XmlGenerator`. Operations that change state are POST services; GET services pin such parameters (e.g. `reset=false`), since service parameters take precedence over request parameters.

Access (fail-closed, `org.pageseeder.bzadmin.access`; contract in `docs/design/access.md`): the host sets an `AdminAccess` request attribute (or uses container roles listed in `berlioz.admin.role.view`/`.operate`) and maps `AdminFilter` to `/bzadmin/*` after its authentication; the filter denies with 403 or sets an `AdminVerdict`. Every generator of a bzadmin service checks it (`DirectInvocationTest` calls each generator of `services.xml` without a verdict; the access API for new services is in `docs/design/access.md`): bzadmin's own call `AdminGuard.allows(req, VIEW or OPERATE)` first in `generate` (and `getETag`) and return `AdminGuard.denied(out)`; Berlioz generators are used through one-line delegating classes in `org.pageseeder.bzadmin.guard` (`org.pageseeder.bzadmin.guard.<path>.<Name>` wraps `org.pageseeder.<path>.<Name>`; the XSLT shows the delegate with `f:generator-class`). A new service never references a Berlioz generator directly. The filter also treats every method other than GET/HEAD as an operation (`OPERATE`, and `X-Requested-With: XMLHttpRequest` with same-origin `Sec-Fetch-Site`/`Origin`, else 403), rejects Berlioz control parameters (400) except on a POST to their admin service (`AdminFilter.CONTROL_SERVICES`, which gets `CONTROL_AUTHORIZED_ATTRIBUTE`), and keeps `Cache-Control: no-store` and its CSP on every response it handles. Third source of access: the fallback BASIC login (`berlioz.admin.username`, `berlioz.admin.password`: a PBKDF2 hash made by `AdminPassword`, or the password itself for local development, told apart by `AdminPassword.isHash` (`$<scheme>$` prefix; an invalid hash denies); HTTPS, or plain HTTP on loopback only (`AdminFilter.isLoopback`: loopback client and server addresses, `localhost`/loopback host, no forwarding header), else 403; 401 challenge otherwise). Operations, rejected control parameters and failed logins go to the `org.pageseeder.bzadmin.audit` logger (no query or parameter value). Host installation: `docs/access.md`. So operations are POST services and reads are GET (even lookups such as `service/match`).

Berlioz's `ListBerliozOptions` lists the options of the Berlioz version with their type, default, description and deprecation, for the Config page, which also uses Berlioz's `GetGlobalConfig`, `GetBerliozConfig` and `GetErrorHandlerConfig`. The Services page uses Berlioz's `ListRuntimeServices` (generator parameters are shown as declared: they must not contain secrets), with its load diagnostics: the cause of a failed load in the status alert, the warnings, the service files in load order (Services view) and, per service, its file, the declarations it replaced and where its response-code rule is declared. Its views are `group-by` values (none, `generator`, `tree`, `lookup`) swapped into `#services-details`. The Lookup view (`?group-by=lookup`) has a form to find the service matching a URL and method with Berlioz's `GetMatchingService` (`bzadmin-services-lookup`, `/bzadmin/services/lookup?url=…&method=…`, the same view with the result in `#service-lookup-result`; 400 without `url`, so the tab is a view of the Services page): the frontend reduces the URL with `toBerliozPath`, swaps the result in and puts the lookup URL in the address bar; without script the form opens that page. The older `/bzadmin/service/match` (target `ajax`) stays for tools. `GetBerliozControl` reports whether the user can use the control buttons (`authorized`: the `OPERATE` permission, since the filter authorises control parameters on the control POSTs) and the XSLT cache mode. The "Clear XSLT cache" (XSLT page, `berlioz-reload=xslt`), "Reload services" (Services page, `berlioz-reload=services`) and "Reload everything" (Config page, `berlioz-reload=true`) buttons come from the `bzadmin-control-form` template in `bzadmin.xsl`: always shown, disabled with the reason when they would have no effect. The frontend (`postControl` in `http.js`) posts `berlioz-reload` with its scope to `/bzadmin/xslt/cache/clear.xml`, `/bzadmin/services/reload.xml` or `/bzadmin/config/reload.xml`, whose `berlioz-control/@authorized` tells whether the user can operate and whose `X-Berlioz-Reload` header lists the scopes Berlioz applied (the servlet applies `config`, `services`, `xslt` and `etags`; `redirects` only when the host maps the Berlioz redirect filters): each button checks its scope, and the Config page lists the scopes applied; the reload responses also have the resulting `live-services/@load-status`, and the Config one the reloaded properties (Berlioz keeps the last valid ones, without reporting it, when the configuration fails to load). Prefer Berlioz generators; add overlay generators only for what Berlioz does not provide. Field mapping and unavailable metadata: `docs/design/metadata-contract.md`.

In `test-host/` (not part of the overlay), `LocalAdminAccessFilter` grants admin access to loopback requests (local development only), `StaticCacheControlFilter` sets `Cache-Control` on static files (init parameter `cache-control`; it replaced Bastille's `StaticCachingFilter`), `DevCacheManagers` (a listener) creates the cache managers at startup and closes them (the admin pages never create one): the default EHCache 2 manager from `ehcache.xml`, and the JCache managers `dev-ehcache3` (EHCache 3) and `dev-caffeine` (Caffeine), each with a `static-files` cache, registered with `JCacheManagers` (the test hosts get these libraries from the WAR); `DevStaticCacheFilter` serves the static files from those caches and EHCache 2's `static-web-caching` (each sees the same gets and puts, to compare the libraries on the cache page; no validators, so conditional requests get the whole file); `WEB-INF/config/logback-local.xml` writes `app.log` (rolled daily) and `audit/audit.log` to `test-host/build/dev-logs` (system property `bzadmin.dev.logs`, set by Gretty), and the `devLogSamples` task (run before every `AppStartTask`) adds older dated `app.<date>.log` files with errors, stack traces, an over-long line and more lines than a page shows, for the Files view of the Logs page; the home page explains the application, from `WEB-INF/psml/content/home.psml` read by `GetPsmlContent` through EHCache 2's `PSML` cache (replaced when the file changes) and rendered by `default.xsl` with `style/home.css`, and `SecurityHeadersFilter` injects CSP (with per-request nonce), `Permissions-Policy`, `Referrer-Policy`, and `X-Frame-Options` headers. The nonce is stored as request attribute `berlioz-nonce` and picked up by the XSLT via `header/security/@nonce`.

### Packaging

The `bzadmin` JAR (`./gradlew :overlay:jar`) is the overlay in its resource layout, and the only distribution: the legacy WAR overlay is no longer built (Berlioz unpacks WAR overlays after the classloader is created, so they cannot add the overlay's generators). It is published with `maven-publish` as `org.pageseeder.berlioz:pso-bzadmin`; the archive base name is `pso-bzadmin`. The publication also has sources and Javadoc JARs, a CycloneDX SBOM (`-cyclonedx.json`, runtime graph only) and, when prepared by JReleaser for Central, required PGP signatures; every JAR has `META-INF/LICENSE`, `NOTICE` and `licenses/` (Apache 2.0; Quicksand font OFL) from the root. See the distribution section of `docs/design/build-contract.md`.

Everything in `test-host/` (default services, PSML content, Gretty config) is only for local development.

## Versioning

Version is stored in `version.txt` (e.g. `0.14.0`). It is read by the root `build.gradle.kts`, substituted into XSL files via `@VERSION@` token replacement, and must match the filenames of the compiled JS/CSS assets.

Webpack names both bundles from `version.txt`. Keep `frontend/package.json` `version` in step with it for consistency, although the build does not use it.

To prepare a release: update `version.txt`, `frontend/package.json` and its lockfile, run `npm run build` in `frontend/`, then run `./gradlew :overlay:jar` (or publish).

### Accessible reflow and documentation navigation

Content grids use zero-minimum tracks so column gaps cannot force a minimum page width.
The shared header stacks on narrow viewports. Data tables retain their two-dimensional
layout inside contextual groups rather than widening the page. These are not landmarks;
JavaScript includes them in the tab order only while they overflow horizontally.
Muted light-mode text meets 4.5:1 against the page canvas as well as white surfaces.

XSLT documentation renders every section without JavaScript. The enhanced navigation
reveals one section, keeps fragment history, and moves focus to the linked heading.
Aggregate declaration views use separate IDs from the stylesheet-specific declarations;
function IDs identify the declaration node, including overloads and repeated names.

Landmarks use the native page header, main and footer. Administration, Views, XSLT
documentation and pagination navigation have distinct accessible names. Service dialogs
reference their visible title, matching the library and thread dialogs. System help
popover paragraphs are rendered within main, outside headings. Simple tables keep native
header cells; no ARIA grid or tab roles are added to ordinary tables or view links.
