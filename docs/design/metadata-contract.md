# Berlioz metadata contract

Berlioz 0.14.5 is the reference framework version. Each field below comes from actual generator output captured by `:test-host:hostTest` in `test-host/build/test-fixtures/root/` (`bzadmin_config.xml`, `bzadmin_services.xml`, `fixture_*.xml`), checked against the Berlioz serializers (`Service.writeTo`, `ListRuntimeServices`, `BerliozConfig.diagnosticSnapshot`, `ErrorHandlerServlet.diagnosticSnapshot`, `GetGlobalConfig`). Fields Berlioz does not report are listed as unavailable rather than guessed.

## Sensitivity

| Data | Risk | Handling |
| --- | --- | --- |
| Global property values | Secrets in configuration (e.g. `berlioz.control.key`) | Defence in depth, as the admin pages are expected behind authentication: `GetGlobalConfig` redacts by name (`Redaction.redact`: password, secret, key, token, auth…) before serialization. Verified: `berlioz.control.key` and `fixture.api-key` are `[REDACTED]` in `.src`, `.xml`, `.json` and HTML. |
| Generator parameter values in services | None by contract | Shown as declared . Parameters are static service configuration and must not hold secrets: static secrets belong in the global configuration, and dynamic values are URI references, not values. Name-based redaction would also hide ordinary parameters (`sort-key`, `author`…) that the page exists to diagnose. |
| Servlet and error handler configuration | Paths, init parameters | Berlioz snapshots are allowlists; stylesheet paths are only reported when safe (relative or classpath, no traversal). Used as is. |
| Option defaults | None (code constants) | `ListBerliozOptions` writes defaults only, never values. |

`GetServices` (disk copy of `services.xml` files) is no longer used: it only shows filesystem files.

## Configuration page (`bzadmin-config`)

| Content | Generator | Field | UI |
| --- | --- | --- | --- |
| `global-config` | Berlioz `GetGlobalConfig` | `properties/@source` | mode file (`config-{mode}.*`): "Merged from the default configuration file, if any, and …"; default file: "Configured in …"; absent: "No global configuration file" |
| | | `property/@name`, `@value` (redacted) | Option value, or "Other properties" grouped by prefix |
| `berlioz-options` | Berlioz `ListBerliozOptions` | `option/@property`, `@type`, `@default`, `@deprecated` (version), `@removal`, `@description` | Every Berlioz option: configured value, or `default` tag with the default; `deprecated` tag (since 0.14.0, removed in 1.0: `berlioz.http.get-via-post`, `berlioz.errors.problem`, `berlioz.xml.header.version`) |
| | (local catalog in `config.xsl`) | Description | One line per option, with markup, from the 0.14.5 `BerliozOption` documentation; an option missing from it (a later Berlioz) shows `option/@description` |
| `berlioz-config` | Berlioz `GetBerliozConfig` | `servlet/@name`, `@mappings`, `@media-type`, `@charset`, `@cache-control`, `@compression`, `@xslt-cache` | Servlets table |
| | | `servlet/@requires-control` (0.14.5) | `requires control` tag when `true` (other requests receive a 404) |
| | | `servlet[@initialised='false']` with `@name`, `@mappings` only (0.14.5: registered, not requested yet) | Listed after the initialised servlets with a `not initialised` tag |
| | | `stylesheet/@allocation` (`group`, `identity`…), `@source` (`filesystem`/`classpath`), `@path` (optional) | Stylesheet column |
| | | `fallback/@source`, `@path` (optional) | "Fallback: …" |
| | | `stylesheet/selection/@name` (`global`, group or service ID), `@source`, `@path` (optional) (0.14.5: selected so far) | List under the stylesheet |
| | | `xslt-processor/@factory`, `@vendor`, `@xslt-version`, `@product-name`, `@product-version` (0.14.5, `XsltProcessorInfo`) | Runtime panel |
| | | `container/@server-info`, `@servlet-api` (0.14.5, with an initialised servlet) | Runtime panel |
| `error-handler-config` | Berlioz `GetErrorHandlerConfig` | `servlet/@name`, `@mappings`, `@problem-xml`, `@detail` | Error handler table |
| | | `forward/@source`, `@extensions`, `@default-extension` | Forwarded extensions |
| | | `ignore/@extensions` | Ignored extensions |
| | | `stylesheet/@configured`, `@selected` | Stylesheet |

Unavailable:

- **Effective value and origin of global properties:** Berlioz reports configured properties and the option defaults, not the value it resolved or where it came from (file, deprecation alias, system property). The page shows "configured" or "default", nothing more.
- **Settings of servlets not yet initialised:** Berlioz only knows their registration (`BerliozXML`/`BerliozJSON` are lazy in kickstart) until their first request. The page says so.
- **Stylesheet path of filesystem allocations:** absolute paths such as `/xslt/html/{GROUP}.xsl` are withheld by Berlioz's safe-path rule.

## Services page (`bzadmin-services`)

Content `runtime-services` from Berlioz `ListRuntimeServices` (`@Beta`; root element `live-services`):

| Field | Source | UI |
| --- | --- | --- |
| `live-services/@load-status` | `ServiceLoader.getLoadDiagnostics().status()`: `ready`, `pending`, `reload-failed`, `startup-failed` | Nothing when ready; alert callout otherwise ("Berlioz keeps running the last valid services" for `reload-failed`) |
| `live-services/@loaded`, `@failed`, `@retry-after` | ISO 8601, when known; `failed` and `retry-after` only while the last attempt has failed | Relative times: last load (Service configuration panel), failure and next attempt (alert) |
| `load-failure/@source`, `@line`, text | Cause of the last failed attempt, kept until the next successful load; `line` and the parser's message only when `berlioz.errors.detail` is not `minimal` | In the alert; without it, "See the logs for the cause" |
| `warning/@source`, `@line`, text | Warnings of the last successful load | Warning callout, except in the Lookup view |
| `source/@kind` (`classpath`, `filesystem`), `@name` | Service files of the last successful load, in load order; display names, never URLs | Service configuration panel (Services view), with the services each declares and replaces |
| `service/@id`, `@group`, `@method` | Registry, one element per method | Group sections, name, method. Ids repeat across methods and sources, so dialogs use generated ids |
| `service/@flags` | Optional | `tag` per flag |
| `service/@direct` | Only when `true` (`<handler>` services) | `direct` tag |
| `service/@cacheable`, `@cache-control` | Effective; `cache-control` only when cacheable | Cache column and dialog |
| `service/@supported` | Output types supported by all generators (`xml`, `json`) | Outputs column |
| `service/@source` | Display name of the file that declared the service | Dialog: "Declared in" |
| `service/overridden/@service`, `@source`, `@pattern` | Earlier declarations of the same method and pattern that the service replaced, in load order | `overrides` tag; dialog: Replaced declarations |
| `response-code/@use`, `@rule` | Effective rule | Dialog |
| `response-code/@origin` | Where the rule is declared: `service`, `group`, `service-config`, `default` | Dialog note |
| `url/@pattern` | Patterns mapped to the service | URLs |
| `generator/@class`, `@name`, `@target` (optional, absent for direct), `@type` (`xml`, `generator`…), `@cacheable`, `@affect-status`, `@supported` | Per generator | Generator tables (dialog, tree), by-generator view |
| `generator/parameter/@name`, `@value` | As declared | Parameters column |

The Lookup view separates the request form ("Find a service") from the result panel. A match labels the HTTP method, checked path, matched URL pattern and selected service, followed by extracted URI parameters (or an explicit absence) and service details. Expandable help explains URL normalization and the distinction between routing and executing the service. The matching generator reports the selected route, not competing candidates or precedence diagnostics.

Generator names share a presentation across service dialogs, tree and lookup tables, and application statistics. Known package roots (Berlioz, bzadmin, Bastille, Flint, Bridge, EHCache and Hibernate) use `[origin].ClassName`, with an accent-colored origin and a class-name button opening a native popover containing the selectable full class name. The popover stays outside table layout, supports Escape and outside-click dismissal, and includes a Close button. Unknown packages remain fully qualified. Origins describe namespaces, not verified JAR provenance. Guarded Berlioz generators are displayed as the delegated Berlioz class. Sorting and grouping retain the full normalized class identity. In the Generators view, headings show the simple class name and service count, with the origin and full package in smaller text alongside, wrapping below when necessary.

Unavailable:

- **Provenance of cache control** (service, group, configuration or default): Berlioz serializes the effective value only; the dialog says so. The response-code rule has its origin since 0.14.5.

## Application page (`bzadmin-application`)

Content `summary` from bzadmin `GetApplicationSummary` (also alone at `/bzadmin/application/summary`; like `GetGeneratorStatistics` since Berlioz 0.14.5, it has JSON output):

| Field | Source | UI |
| --- | --- | --- |
| `application-summary/@mode` | `GlobalSettings.getMode()` | `mode` tag |
| `component[@name='bzadmin']/@version` | `org/pageseeder/bzadmin/bzadmin.properties` (`@VERSION@` replaced by `processResources`) | "Unknown" when absent |
| `component[@name='berlioz']/@version` | `GlobalSettings.getVersion()` (JAR manifest; `unknown` → absent) | |
| `component[@name='java']/@version`, `@product`, `@vendor` | `Runtime.version()`, `java.vm.name`, `java.vendor` | |
| `component[@name='xslt']/@factory`, `@product`, `@version`, `@vendor`, `@xslt-version` | Berlioz `XsltProcessorInfo.current()`: the factory Berlioz uses and the processor's `xsl:product-name`, `xsl:product-version`, `xsl:vendor`, `xsl:version` (Saxon: `SAXON`, `HE 12.10`, `Saxonica`, `3.0`; the JDK processor only has the factory) | "SAXON HE 12.10 (Saxonica)" and an `XSLT 3.0` tag; absent component: "None" |
| `component[@name='container']/@product`, `@servlet-api` | `Environment.getServerInfo()`, `getServletApiVersion()` of the request (0.14.5) | Absent: "Unknown: the container does not identify itself to Berlioz" |
| `logging/@backend`, `@recent-events`, `@log-files` | `LoggingBackends`; `berlioz.admin.logs` set → `property`, else `backend` | |
| `cache-provider/@id`, `@label`, `@managers` (or `@failed`) | `CacheRegistry.global().providers()` | None: "No cache library detected" |
| `access/@roles`, `@fallback-login` | Whether `berlioz.admin.role.*` / `berlioz.admin.username` are set (never their values) | Sources of access |
| `status/@services`, `@configuration`, `@properties`, `@xslt-cache` | `ServiceLoader.getLoadStatus()`; configuration file + property count (`none`, `empty` = empty or failed to load, `loaded`) | Application status |

The Overview presents Mode, Services, Configuration and Berlioz version as key metrics above runtime components and detected integrations. Native info popovers explain each metric and link to Services, Configuration and Logs. Services ready and configuration loaded have green ticks (loaded does not mean validated). Failed service loads and empty configuration have warning icons and styling; pending services, absent configuration and unavailable versions remain neutral; empty configuration remains explicitly ambiguous. Runtime factory classes are expandable. Integrations use a two-column table with row headings for Logs, Caches, XSLT and Access. Summaries link to Logs and Caches, and XSLT cache settings are shown on the XSLT page. Access mechanisms remain in expandable help and do not claim to identify the mechanism that authorised the request.

Sensitivity: versions and names only; no path, environment variable, role name, user name or configured value.

## System page (`bzadmin-system`)

Content `system-summary` from bzadmin `GetSystemSummary` (also alone at `/bzadmin/system/summary`), Java 11 `java.lang.management` only. Units: bytes, milliseconds, loads as a fraction from 0 to 1 (shown as %); a value the JVM does not provide is absent, never zero.

| Field | Source | Absent when |
| --- | --- | --- |
| `@processors` | `OperatingSystemMXBean.getAvailableProcessors()` | — |
| `runtime/@start`, `@uptime-ms` | `RuntimeMXBean` | — |
| `runtime/@load-average` | `getSystemLoadAverage()` | negative (Windows) |
| `cpu/@process-load`, `@system-load`, `@process-time-ms` | Attributes `ProcessCpuLoad`, `CpuLoad` (Java 14+) then `SystemCpuLoad`, `ProcessCpuTime` (ns) of `java.lang:type=OperatingSystem`, read through the MBean server (`OptionalMetrics`: no `com.sun.management` linkage) | missing, unreadable, negative or NaN; no `cpu` element when none |
| `memory-area[@type]/@init`, `@used`, `@committed`, `@max` | `MemoryMXBean` heap and non-heap | `init`/`max` undefined (-1): "Undefined" |
| `memory-pool/@name`, `@type`, usage, `@peak-used` | `MemoryPoolMXBean` (valid pools only) | as above |
| `garbage-collector/@name`, `@collections`, `@time-ms`, `@pools` | `GarbageCollectorMXBean` (valid only) | -1: "Not available" |
| `threads/@live`, `@daemon`, `@peak`, `@started`, a count per `Thread.State` | `ThreadMXBean`; states from `getThreadInfo(ids, 0)` (no stack) | — |

The thread list is no longer part of the page: it loads on demand ("Show threads", `/bzadmin/system/threads`), from the overlay's `ListThreads` (it replaced Berlioz's, with the same `thread` attributes): per thread, the real thread group, `in-native`, `deadlocked`, `blocked-count`, `waited-count`, `allocated-bytes` (JDK-specific `getThreadAllocatedBytes`, invoked by name on the threading MBean), `times` (nanoseconds, only when thread CPU time is enabled; never enabled by bzadmin), `lock` (with its owner), `activity` (the top non-JDK frame) and `origin` (`ThreadOrigin`: `component` from the JVM's names or groups, the bottom non-JDK frame, the name, or the context class loader, as `basis`; `role` acceptor, selector, request, idle, running or internal; `loader`). It reads every stack to classify them but writes none (2000 threads at most). One thread with its stack (256 frames, the monitors each frame holds, the synchronizers held): the overlay's `GetThreadInfo` (`/bzadmin/system/thread?id=`; 400 without a valid ID, 404 for a thread that ended), shown in a dialog. The page computes the CPU share of one processor and the allocation rate of each thread between refreshes (`frontend/src/js/lib/threads.js`). The live polls are handler services (`<handler>`: the output of the generator only, `<runtime>` or `<cpu-sample>`, without the envelope or XSLT, whatever the extension): memory from `GetRuntimeInfo` (`/bzadmin/system/runtime/sample`), CPU from `GetCpuSample` (`/bzadmin/system/cpu/sample`); `/bzadmin/system/runtime` and `/bzadmin/system/cpu` remain generator services. The live CPU poll uses Berlioz's `GetCpuSample` (from 0.14.5, first written in the overlay) instead of `GetCPUTime` (deprecated in 0.14.5), which slept 100 ms per request on a request thread, measured only that window of each second, truncated to whole percents of one core and summed the CPU time of the live threads only (so mostly 0): `<cpu-sample>` has cumulative counters, and the page computes the usage between successive samples (`frontend/src/js/lib/cpu.js`); while the chart runs, the CPU table shows the latest values.

| Field | Source | Unavailable |
| --- | --- | --- |
| `cpu-sample/@processors`, `@uptime-ms` | `OperatingSystemMXBean.getAvailableProcessors()`, `RuntimeMXBean.getUptime()` | — |
| `@process-time-ns` | `ProcessCpuTime` (read through the MBean server, like `OptionalMetrics`); process share = Δ / (Δuptime × processors) | absent |
| `@gc-time-ms` | sum of `GarbageCollectorMXBean.getCollectionTime()` (elapsed, not CPU time); GC share = Δ / Δuptime | absent when no collector reports it |
| `@system-load` | `CpuLoad`, `SystemCpuLoad` before Java 14 (not cumulative) | absent |
| `@load-average` | `OperatingSystemMXBean.getSystemLoadAverage()` | absent (Windows) |

## Supported framework version

Berlioz 0.14.5 is the reference and minimum version. Recheck these contracts before claiming compatibility with Berlioz 1.0.

- **APIs of 0.14.5 in use:** `ReloadScope` and `ControlParameter` (`AdminFilter`: scoped `berlioz-reload`, deprecated aliases rejected), `ReloadScope.HEADER` (the frontend reports the scopes applied), `StylesheetLocation.select`, `countClasspathCopies` and `resolveApplicationClassLoader`, `XsltResolvers.locate`, `XsltProcessorInfo.current()`, `Environment.getServerInfo()` and `getServletApiVersion()`; the generators `ListBerliozOptions`, `GetCpuSample`, and `GetSystemProperties` and `GetGeneratorStatistics` as `Generator`s. `ControlNetwork.matches` is not used: the plain-HTTP fallback login needs a direct loopback request (`AdminFilter.isLoopback`), not the configured control network.
- **Captured output:** the metadata fixtures `fixtures/config.xml`, `services.xml` and `services-reload-failed.xml` are copied from the 0.14.5 captures of `:test-host:hostTest` (`test-host/build/test-fixtures/root/`, written by `BerliozHost`: `bzadmin_config.xml`, `bzadmin_services.xml`, `bzadmin_services_view_reload-failed.xml`). `services-lookup.xml` (0.14.4) and `config-global-only.xml` are older: the host tests capture no successful lookup of `/bzadmin/config`, and `GetGlobalConfig` output is unchanged.
- **Deprecated generators:** none used. `GetLiveServices` (alias of `ListRuntimeServices`) and `GetCPUTime` (replaced by `GetCpuSample`) are deprecated for removal in 1.0, as are the control parameters `reload-services`, `clear-xsl-cache` and `reset-etags`, which `AdminFilter` rejects.
- **`@Beta` generators in use:** `ListRuntimeServices`, `GetMatchingService`, `GetRuntimeInfo`, `GetFileSystemInfo`, `ListLibraries`, `GetCpuSample`, `GetSystemProperties`; their schemas may change before 1.0.
- **Thread diagnostics:** Berlioz's `ListThreads` and `GetThreadInfo` report the contention details since 0.14.5, but the overlay keeps its own (`ListThreads`, `GetThreadInfo`, `ThreadDetails`) for the origin and activity of each thread; Berlioz's `Threads` and `OptionalMetrics` helpers are package-private, so the overlay keeps its own `OptionalMetrics`.

Missing upstream (to propose for Berlioz 1.0, as tracked in the roadmap): provenance of effective values; whether a configuration reload failed (Berlioz keeps the last valid configuration and only logs it); the reload scopes applied, for generators (only the response header has them); a stable `ListRuntimeServices` schema.

## Legacy WAR overlay

WAR overlays are not supported: a WAR overlay cannot add classes, so it could not run the guarded generators. The Config page now expects the options.
