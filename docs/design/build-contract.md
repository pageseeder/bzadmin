# Build and compatibility contract

This contract defines runtime requirements, provider ownership, distribution and bounded-work rules. Version `0.14.0` is being prepared for public release; qualification must be repeated for each release artifact.

## Runtime compatibility

| Area | Contract | Tested | Evidence |
| --- | --- | --- | --- |
| Java | 11 or later. The overlay's bytecode is Java 11 (toolchain 11); so is Berlioz 0.14.5 (`org.gradle.jvm.version` 11). | Java 11 only (Temurin 11.0.32.1). Newer JDKs require release qualification. | Tests assert the runtime version (`bzadmin.java.expected`). |
| Berlioz | 0.14.5 or later in the 0.14 line (classpath services, JAR overlay stylesheets, group stylesheet fallback since 0.14.4; scoped `berlioz-reload`, load diagnostics and the generators and helpers of 0.14.5). | 0.14.5 | Host tests. |
| Servlet | `javax.servlet`, Servlet 4 API. Berlioz 0.14.5 is compiled against `javax.servlet` and declares no servlet dependency (container-provided). Jakarta containers (Tomcat 10+, Jetty 11+ without an EE 8 environment) are not supported. | Jetty 10 (Gretty 3.1.9 and embedded Jetty 10.0.26). Tomcat 9 is expected to work, untested. | Host tests. |
| XSLT processor | Host-provided; XSLT 2.0. Exactly one Saxon on the classpath. Saxon 12 is the reference (Saxon 13 requires Java 17). | Saxon-HE 12.10 | `StagedArtifactTest` (one Saxon, 12.x). |
| Logging | SLF4J 2 API (from Berlioz); any backend, or none. The Logs page's recent events and capture threshold need logback (the overlay's optional adapter, `compileOnly`); its log files too, unless the host lists them in `berlioz.admin.logs`. Without logback the page says so, and works. | logback 1.5.37 | Host tests; `NoLogbackHostTest` (host without logback); `logbackTest` (adapter). |
| Cache | Optional, host-provided: EHCache 2.x (adapter compiled against the 2.6 API; the overlay no longer brings EHCache), JCache 1.1 managers that the host registers (`JCacheManagers`), and native Caffeine 3 caches that the host registers (`CaffeineCaches`). None is needed. | EHCache 2.6.11, 2.10.9.2, 2.11.1.3.11; JCache with EHCache 3.10.9 and Caffeine 3.1.8; native Caffeine 3.1.8 | `cacheTest*` runs (adapters, per library); host tests (EHCache 2.11, Caffeine JCache and native Caffeine); `NoCacheHostTest` (host without cache library). |
| JSON output | A `javax.json` implementation (Berlioz's JSON output). | `org.glassfish:javax.json` 1.1.4 | Host tests (`.json`). |
| XML responses | `pso-berlioz-kickstart` for the `.xml` responses of the AJAX operations (fallback XML stylesheet). | 0.14.5 | Host tests. |

Hosts choose provider versions; the overlay does not require exact patch equality with the tested set.
The reference provider set is defined in the consumer fixture (`overlay/src/fixtures/consumer/build.gradle.kts`). The tested column records existing coverage, not qualification of the current development artifact.

## Publication and dependency ownership

The overlay publishes one JAR (`org.pageseeder.berlioz:pso-bzadmin`) with a POM and Gradle module metadata. Its published dependencies are:

| Dependency | Scope | Why | Until |
| --- | --- | --- | --- |
| `pso-berlioz` 0.14.5 | runtime | Generators run on Berlioz; states the minimum version. A host's newer version wins. Brings `pso-xmlwriter` and `slf4j-api`. | — |

Bastille and Cobble are not runtime dependencies.

Rules:

- The host owns the providers: the overlay never publishes a dependency on an XSLT processor, a logging
  implementation, the servlet API, a JSON implementation, a cache implementation or kickstart. It compiles against
  the servlet API, EHCache (2.6), the JCache API, logback and the JSpecify annotations with `compileOnly`.
- No transitive dependency selects a provider: the resolved runtime graph is `pso-berlioz`, `pso-xmlwriter` and
  `slf4j-api` only.
- A new published dependency needs a documented architectural reason and dependency review, and a check in the staged
  consumer (`StagedArtifactTest` checks one Saxon 12, no Cobble, no logging backend and no cache library in the POM
  and module).
- The published graph is `pso-berlioz` only. `./gradlew :overlay:checkDependencyBoundary` (run by `check` and before
  every publication) fails on any other module in the resolved runtime graph or the generated POM, and on a
  reference to `org.pageseeder.bastille` or `org.pageseeder.cobble` in the packaged classes or `services.xml`.
- The minimal host (`warMinimalHost`, `MinimalHostTest`: no logback, no cache library, no Bastille or Cobble) serves
  every admin route.

### Distribution

Version `0.14.0` is being prepared for public distribution under Apache License 2.0; publication is pending.

| Published file | Contents |
| --- | --- |
| `<name>-<version>.jar` | The overlay, with `META-INF/LICENSE`, `META-INF/NOTICE` and `META-INF/licenses/` (Quicksand OFL) |
| `<name>-<version>-sources.jar`, `-javadoc.jar` | Sources and Javadoc of the Java code, with the same license files (`sourcesElements`, `javadocElements` in the module metadata). The sources JAR is reproducible; the Javadoc JAR is not on JDK 11 (pages without dates, `-notimestamp`, but javadoc 11 writes its `*-search-index.zip` files with the current time) |
| `<name>-<version>.pom`, `.module` | Name, description, URL, inception year, organisation, Apache 2.0 license, developer, SCM; dependency `pso-berlioz` only |
| `<name>-<version>-cyclonedx.json` | CycloneDX 1.6 SBOM of the published runtime graph (`cyclonedxDirectBom`, `runtimeClasspath` only); not in the module metadata; it has a timestamp, so it is not reproducible |
| `*.asc` | Signatures generated by JReleaser for Central deployment |

- **Coordinates:** `org.pageseeder.berlioz:pso-bzadmin` throughout archives, metadata and staged qualification.
- **Signing:** JReleaser requires PGP signing for Central deployment. Local staging is unsigned.
  Keys are supplied through protected environment secrets; see [release procedure](../releasing.md).
- **Licensing:** Apache 2.0 for the overlay ([LICENSE](../../LICENSE)); [NOTICE](../../NOTICE) lists the
  third-party fonts (Quicksand, OFL 1.1; Roboto and Roboto Mono, Apache 2.0, as stated in the fonts).
  The documentation engine's Cobble origin is recorded in the [XSLT design documentation](xslt.md). Support and vulnerability reporting:
  [SECURITY.md](../../SECURITY.md).
- **Repository:** `https://github.com/pageseeder/bzadmin` supplies the public URL/SCM metadata.
  Release requires confirmed Maven namespace control and an actionable private security-reporting channel.

## Build

- **Gradle** 8.14.5 (wrapper). Gradle 9 is blocked by Gretty 3.1.9 (the `test-host` wrapper), which uses the
  deprecated `WarPluginConvention`; Gretty 4 only runs Jakarta containers. `test-host` applies `war` before Gretty
  (otherwise Gretty creates the `implementation` configuration, also deprecated). The versions plugin (0.66.0) also calls `defaultDependencies` on the configurations of signing, sources and Javadoc, a Gradle 9
  deprecation it has to fix. Nothing else is deprecated.
- **Java toolchain:** a JDK 11 must be installed (Gradle detects it; no automatic provisioning). The overlay
  compiles with toolchain 11, the `test-host` wrapper with `--release 11`, and the tests run on Java 11.
- **Repositories:** Maven Central, and the Terracotta repository for EHCache 2.11 (the wrapper and the
  consumer and the `cacheTestEhcache211` run; the overlay compiles against `ehcache-core` 2.6.11 from Maven Central). No `mavenLocal()`: removed from
  `settings.gradle.kts`, and the consumer fixture never had it, so neither a local build nor a CI job can
  pick up stale local artifacts. `publishToMavenLocal` remains for trying the overlay in other applications.
- **Reproducible JAR:** no file timestamps, stable order. The same commit and JDK give the same bytes.
- **Frontend:** Node 22.15 or later (`engines` in `frontend/package.json`: the most demanding tool is
  webpack-dev-server; the production build alone needs Node 20.9). The bundles are committed in
  `overlay/src/main/resources/META-INF/resources`, so the Gradle build does not need Node. Gradle checks that
  they have the names of `version.txt` (`checkFrontendBundles`); a release checks that a clean frontend build
  reproduces them (below).
- **CI:** GitHub Actions checks the frontend, backend and staged host. Release preparation is manual and uses a protected environment. See [release procedure](../releasing.md).

### Release sequence

```
(cd frontend && npm ci && npm run build && npm run lint && npm run format:check)
git status --porcelain overlay/src/main/resources/META-INF/resources   # must be empty: bundles reproduced
./gradlew clean :overlay:test :test-host:hostTest :test-host:hostTestStaged
shasum -a 256 build/staging-repo/org/pageseeder/berlioz/pso-bzadmin/<version>/*.{jar,pom,module,json}
```

Record qualification evidence and artifact checksums outside the public checkout; keep user-facing changes
in `docs/release/`. Publication and tagging require explicit authorization.

## Bounded-work policy

The roadmap's criterion is no known error in supported workflows; for admin pages, that includes not letting one
request read or render an unbounded amount of data. Applies to every overlay generator .

1. **Bound what grows with the data**, not with the deployment: log files, PSML trees, cache entries, raw
   values. Lists that grow with the deployment's code (services, stylesheets, libraries, generators, threads,
   system properties) are acceptable without a limit.
2. **Bounds are constants** in the generator (`static final`, documented with `{@value}` in its Javadoc). They are
   not configurable until a host needs it; then as `bzadmin.*` global properties, with the constant as default
   and as maximum.
3. **Size parameters clamp** (`req.parameter(…).asInt().clamp(1, MAX).defaultValue(DEFAULT)`): an oversized
   request gets the maximum, not an error.
4. **Names and paths come from an allow-list:** a request names an item the page has listed (a log file, a cache,
   a folder), never a path to open. Files are also confined by real path (no symlink escape) and extension.
   Unknown names give 404 with a `no-*` element (`no-log`, `no-cache`); a missing required parameter gives 400.
5. **Truncation is reported** in the output (`truncated="true"`, or the total next to the page), and the page
   says so. Never cut silently.
6. **Reading is bounded before parsing**: seek or read a bounded byte range, cap line or value length, and
   stop at the item limit; do not read everything then keep the tail.
7. **When a provider API forces whole-collection work** (for example EHCache `getKeys()`), bound the output,
   and record the remaining cost as a known limitation until the adapter can avoid it.
8. **Each bound has a test** that exceeds it, on the packaged host (as in `AdminOperationsTest`).
9. **Expected failures are 4xx** with a defined element, not 500; unexpected failures stay visible.

Reference budgets. Rows marked in force describe the implementation; the general single-value cap remains a proposal, not a implemented behavior.

| Work | Budget | Status |
| --- | --- | --- |
| Raw log | 1000 lines by default, at most 5000; 8192 characters per line; last 4 MB of the file, at most 4 MB added since a cursor, or the 4 MB before a `before` cursor; never past the size of the file when opened | In force (`GetLogEntries`, `LogTail`; ) |
| XSLT documentation | 256 stylesheets per document, including imports and includes | In force (`XsltDocumentationResolver`) |
| Cache keys | 10 000 keys read per request, 1000 returned (100 by default), in the provider's order (no sort); no value read; JCache keys not listed (a JCache iterator reads each entry as a get) | In force; EHCache 2's `getKeys()` copies the key list of the cache (the only way it lists keys): known limitation |
| Recent log events | 256 events held; 8192 characters of message and throwable per event, 8 throwables of 16 stack trace elements, 256 characters per logger or thread name | In force |
| Lists of data items (log files, folder entries, cache names) | 1000 per response | In force for log files (: 10 000 entries read, the 1000 most recent files listed per directory; the others can be read by name) and folder entries; cache names |
| Trees (PSML folders) | No tree per response: one folder level per request (the tree loads folders on demand) | In force |
| PSML repository (`org.pageseeder.bzadmin.psml`) | One folder level per listing: 10 000 entries read, 1000 returned; paths of depth 32; metadata parse 4 MB, XML depth 128, 100 000 elements; source 1 MB | In force; used by the `files` routes |
| Single values shown in full | 8192 characters, then truncated | Proposed |
| Concurrent expensive reads (raw logs, PSML) | Raw logs: 4 at the same time, a fifth waits up to 2 s, then 503 `<no-log reason="busy">`; PSML: not limited | In force; PSML: each request is bounded by the repository budgets |

### Current state of the routes

| Route | Generator | Bounded |
| --- | --- | --- |
| `logs/raw` | overlay `GetLogEntries` (by ID or name) | Yes |
| `logs`, log file list | overlay `ListLogFiles` | Yes (the 1000 most recent files per directory) |
| `logs/recent` | overlay `GetRecentEvents` (logback adapter) | Yes (256 events of bounded text) |
| `files`, `files/folders`, `files/document` (PSML) | overlay `ListPsmlFolder`, `GetPsmlDocument` | Yes (repository budgets) |
| `cache`, `cache/info` | overlay `ListCaches`, `GetCacheInfo` | Yes (1000 caches per manager; statistics from counters, never by reading entries) |
| `cache/entries` | overlay `ListCacheEntries` | Yes (10 000 keys read, 1000 returned; EHCache 2 copies its key list) |
| `xslt` | overlay `ListXsltFiles` | Grows with the deployment's stylesheets |
| `xslt/doc` | overlay `GetXsltDocumentation` | Yes (256 stylesheets) |
| `system/threads`, `system/thread` | overlay `ListThreads`, `GetThreadInfo` | Output limited to 2000 threads; listing captures all live stacks for classification before limiting output. Single-thread output: 256 frames. |
| `system/*`, `application/*`, `config`, `services` | Berlioz, overlay | Grow with the JVM and deployment; long values (`java.class.path`) shown in full |
