# Contributing

Read the [architecture and principles](docs/architecture.md) before changing integration behavior. The [developer reference](docs/development.md) maps services, generators, frontend components and test fixtures.

## Setup

Install JDK 11 and Node 22.15 or later. Use the Gradle wrapper. Frontend bundles are committed, so Java-only builds do not require Node; frontend changes require rebuilding them.

```sh
cd frontend
npm ci
npm run build
```

## Commands

### Backend (Gradle / Gretty)

```bash
# Run local dev server (Jetty 10, http:8999, https:8444, context /) with the overlay from sources
./gradlew :test-host:appRun

# Run the packaged overlay as a JAR in WEB-INF/lib
./gradlew :test-host:appRunJarOverlay

# Build the JAR overlay (output: overlay/build/libs/pso-bzadmin-<version>.jar)
./gradlew :overlay:jar

# Stage the public overlay for local qualification (no remote publication)
./gradlew :overlay:publishMavenPublicationToStagingRepository
# Install locally for evaluation
./gradlew :overlay:publishToMavenLocal

# Check for dependency updates
./gradlew dependencyUpdates
```

The local dev server runs with `-Dberlioz.mode=local`, which disables XSLT caching (`config-local.xml`). Admin pages are served at `/bzadmin/<section>.html`.

Run in the background (no stdin), `appRun` stops straight after starting; keep stdin open with `tail -f /dev/null | ./gradlew :test-host:appRun`.

### Frontend (npm, from the `frontend/` directory)

Node 22.15 or later (`engines` in `package.json`).

```bash
cd frontend
npm install          # first time setup
npm run dev          # webpack dev server with hot reload at localhost:9000
npm run build        # production build → outputs to overlay/src/main/resources/META-INF/resources/js/ and …/css/
npm run analyze      # production build plus a bundle report in frontend/analyzer/
npm run lint         # ESLint on .js files
npm run lint:fix     # ESLint with auto-fix
npm run format       # Prettier: rewrite files in src/
npm run format:check # Prettier: report unformatted files
npm test             # Node's test runner on src/js/**/*.test.js (the poller, the rates, the CPU usage, the thread deltas, the Berlioz path, the XML tokens, the reload scopes)
```

`npm run build` empties `js/` and `css/` under `META-INF/resources` first, and the CSS references its fonts and images relatively, so they work under any context path. `:overlay:processResources` depends on `checkFrontendBundles`, which fails unless those folders hold exactly the bundles of `version.txt`. Clean build: `npm ci && npm run build`, then Gradle.

Prettier owns formatting (`frontend/.prettierrc.json`); `eslint-config-prettier` is last in the ESLint config so ESLint only checks code quality.

The webpack dev server (`https://localhost:9000`) proxies every request to the Gretty backend at `https://localhost:8444`, so `./gradlew :test-host:appRun` must be running first. When webpack rebuilds it replaces the versioned files in `overlay/src/main/resources/META-INF/resources/`.

ESLint uses flat config (`frontend/eslint.config.js`, ESLint 10); the files to lint are set by its `files` pattern, not by CLI flags.

### Tests (Java 11)

```bash
./gradlew :overlay:test   # overlay tests: XSLT on captured generator XML, XSLT documentation, from the packaged JARs (without logback or cache library; runs logbackTest and cacheTest first)
./gradlew :overlay:logbackTest   # the logback adapter, with logback (src/logbackTest)
./gradlew :overlay:cacheTest   # the cache adapters, once per cache library set (src/cacheTest; runs cacheTestEhcache26/210/211, cacheTestJCacheEhcache3, cacheTestJCacheCaffeine, cacheTestCaffeine (native, without the JCache API), cacheTestAllProviders, cacheTestNoCache)
./gradlew :test-host:hostTest   # packaged-host tests: embedded Jetty runs the WAR with the JAR overlay and test fixtures
./gradlew :test-host:hostTestStaged   # release qualification: the same tests on a host resolved from the staged artifacts
```

`hostTestStaged` publishes to `build/staging-repo`, then a separate consumer build (`overlay/src/fixtures/consumer`, no `mavenLocal()`) resolves the overlay from there with the providers a host declares (Saxon 12 without a strict constraint), and the test host is assembled from those libraries.

`hostTest` uses its own `integrationTest` source set with only JUnit and Jetty on the classpath (Berlioz would also discover services on parent classloaders), and is not named `integrationTest` because Gretty would start the dev server around it. Test fixtures (a second JAR overlay, a filesystem stub, a services module, `config-test.xml` and the host's authentication filter `FixtureAuthentication` a forwarding servlet `FixtureForward` (`/fixture-forward/*` → `/bzadmin/*`), `FixtureInclude` (`/fixture-include/<path>` includes `<path>.src`) and `FixtureError` (`/fixture-error/<code>`; error pages 418 → `/bzadmin/config.src`, 451 → `/bzadmin/cache/clear.src`) in `src/fixtures/java`; the fixture JAR's `fixture-admin-*` services use bzadmin generators outside `/bzadmin/*`, packaged in the fixture JAR, never published) are in `overlay/src/fixtures/`. The test host uses a web descriptor from `test-host/src/integrationTest/resources/org/pageseeder/bzadmin/` (`web.xml` by default; others for a missing, mis-mapped or misordered `AdminFilter`, the filter without authentication `web-filter-only.xml`, and a host unaware of bzadmin `web-accidental.xml`), not the WAR's, with `web-override.xml` over each so that `*.src` responds without Berlioz control authorization (kickstart's `BerliozSource` requires it since Berlioz 0.14.5); `BerliozHost.startWithoutLogback()` runs the same host without logback (`warNoLogbackHost`, `hostTest` only: `NoLogbackHostTest`), and `startMinimal()` without logback or any cache library (`warMinimalHost`, `web-no-cache.xml`: `MinimalHostTest`). `FixtureStaticCache` (in `web.xml`, `web-container.xml`, `web-fallback.xml`) records the static files served in the `static-web-caching` EHCache 2 cache under `GET_<path>`, for the operation tests. `BerliozHost` sends requests as the fixture user `operator` (`post` adds `X-Requested-With`; `send`, `postRaw` and `raw` send exactly the given headers, `postRaw` and `raw` over a socket for `Origin` and paths as given) (header `X-Fixture-User`) unless `as(user)` says otherwise (`viewer`, `role-operator`, `role-viewer`, `user`, `revoked` (an `AdminAccess` without permission), `null` for anonymous). The host examples of `docs/access.md` are `HostExample`s, each with its descriptor: `FILTER` (`web.xml`), `CONTAINER` (`web-container.xml`: container BASIC with `realm.properties`, over a real HTTPS connector whose keystore the `testKeystore` task makes, plus a plain connector, `plain()`), `FALLBACK` (`web-fallback.xml`: the fallback login, mode `test-fallback` with `config-test-fallback.xml`, behind a TLS-terminating proxy simulated with `X-Forwarded-*` headers, `direct()` without them); `BerliozHost.start(example)` identifies the same user names per example (BASIC password `fixture-secret-password-<user>`). `AccessPolicyTest.FilterAfterAuthentication` and `OperationDispatchTest.Cases` run for every example: put shared policy cases there. The test host runs with `berlioz.mode=test` (unless its descriptor sets the context parameter), and the secrets in its global configuration start with `fixture-secret` so tests can check that no format exposes them. Tests tagged `known-defect` assert a current failure; replace them when fixing it.


## Making changes

Preserve unrelated working-tree changes. Keep Java generator output, XSLT templates and frontend consumers consistent. Add regression coverage for behavior changes, particularly permissions, redaction, path confinement, resource limits and optional providers. See the developer reference for fixture conventions.

Run the checks appropriate to the change. Packaged-host tests verify deployment behavior; staged-host tests additionally verify published dependency metadata. Use the release sequence in the [build contract](docs/design/build-contract.md) for release qualification. A prior candidate's results do not qualify a new artifact.

## Documentation

Update guides and reference when behavior changes; update design documentation when a contract or rationale changes. Keep planned features in the roadmap and user-visible delivered changes in release notes. Public documentation must stand on its own without private implementation records.

For active work, keep the plan, status, decisions, validation evidence and next action in one working document outside the public checkout. Record the artifact/revision, environment, commands, results and checks not run. On completion, transfer lasting knowledge into maintained public documentation. Do not commit session transcripts, private infrastructure details, credentials or execution journals.

## Copyright, authorship and project domains

The root `LICENSE` and `NOTICE` record the project licence and copyright ownership. Per-file copyright
headers are optional for company-owned code; follow the surrounding files. Where a header is used, use
`Copyright <applicable years> Allette Systems` and `SPDX-License-Identifier: Apache-2.0`, preserving known
earlier years. Keep the HTML copyright metadata consistent with `NOTICE`; do not calculate its year at runtime.

Keep accurate author and contributor names. Individual credit is separate from copyright ownership.
Public metadata uses names without personal email addresses unless the contributor has chosen a public
contact. Preserve third-party copyright, licence and attribution notices, including those in fonts and
generated tooling. The company-owned documentation engine's Cobble history is recorded in
[the XSLT design documentation](docs/design/xslt.md).

Use `pageseeder.org` for project-owned open-source identifiers and links. Change legacy namespaces with
their declarations and consumers together, and verify the affected stylesheets. Do not invent replacement
email addresses or change third-party domains.

## Proposing a contribution

Explain the problem, resulting behavior and validation in the pull request. Discuss substantial new features before implementation so their scope can be agreed. Report security issues using [SECURITY.md](SECURITY.md), rather than a public issue.
