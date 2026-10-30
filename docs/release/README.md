# Release notes

Version `0.14.0` is being prepared as the first public release.
The project has not made its first public release. A version number alone does not indicate publication or qualification.

## 0.14.0

See the [stable release notes](0.14.0.md) for features, upgrade requirements and known limitations.
This release promotes the JAR-overlay beta series to stable distribution. Changes since
`0.14.0-beta.2` are source/documentation attribution cleanup, the PageSeeder XSLT helper namespace,
stable version and bundle metadata, and a Caffeine test-fixture timing fix; no new administration features are introduced.

## 0.14.0-beta.2

Historical development candidate. Upgrading from `0.14.0-beta.1` requires Berlioz 0.14.5.

- Requires Berlioz 0.14.5. The admin pages use its scoped `berlioz-reload`, service load diagnostics (sources, warnings, failure and retry, declaring source and overrides, response-code origin), servlet and stylesheet selection diagnostics, option catalog, thread contention details and non-blocking CPU sample. Reload buttons report the scopes Berlioz applied (`X-Berlioz-Reload`). The System page has a Properties view, with the values of sensitive names redacted.

- PSML browsing uses a shared context path, folder tree and selected-content panels, with document source in a full-width panel below.

- Generator names use consistent compact library labels, with keyboard-accessible popovers for full class names that leave table and dialog sizes unchanged. The Generators view separates class names from smaller package labels.

- Clarified navigation names and service-dialog titles, removed excessive table landmarks, and corrected uptime and system-help markup. Scrollable table groups enter the tab order only while needed.

- Admin pages reflow at narrow widths, with keyboard-scrollable data tables and stronger muted-text contrast. XSLT documentation links use unique anchors, move keyboard focus to their destination, and remain available without JavaScript.

- The live CPU and memory charts poll `/bzadmin/system/cpu/sample` and `/bzadmin/system/runtime/sample`, and the recent cache rates `/bzadmin/cache/info/sample`, which return the generator's output only, without the page envelope or an XSLT transformation.

- Changing the configured fallback admin username now rejects previously cached credentials for the old username, even when the configured password stays the same.

- Native Caffeine 3 caches appear on the Cache page once the host registers them with `CaffeineCaches.register`. EHCache 2.11.1 is supported.

- The XSLT documentation shows global parameters and variables in their own sections, with navigation links, and identifies those whose value is set by content. Template priorities are computed correctly for patterns with several alternatives.

- The System page shows OS, Java and encoding details, warns when the default encoding is not UTF-8, and reports how many property values were redacted.

- Admin pages have a "Skip to content" link, mark the current navigation link, clear resolved messages, and use accessible labels and popovers in place of tooltips for unavailable values, metric definitions and thread names. The Application summary is reorganised into metrics, runtime and integrations, and the theme colours were refined in light and dark modes.

## JAR-overlay beta series

The beta series replaces the WAR overlay with a JAR, requires Berlioz 0.14.5 (0.14.4 up to `0.14.0-beta.1`), and denies administration until the host explicitly grants access. The host owns runtime providers; Bastille and Cobble are not runtime dependencies.

The interface provides configuration and service inspection, optional cache administration, read-only PSML browsing, bounded log reading, source-level XSLT documentation and application/JVM diagnostics.

See [installation](../getting-started.md), [upgrading and rollback](../guides/upgrading.md), [compatibility](../design/build-contract.md) and the [roadmap](../../ROADMAP.md).

Future release notes should identify the released version, user-visible changes, breaking changes, upgrade requirements and known limitations. Qualification reports and execution journals are maintained separately from public release notes.
