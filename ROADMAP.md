# Roadmap

Berlioz Admin aims to provide safe, useful administration for Berlioz applications while leaving runtime libraries and authentication under the host's control. See [architecture and principles](docs/architecture.md).

Version `0.14.0` is the first public release, at `org.pageseeder.berlioz:pso-bzadmin`, with source and issue tracking on [GitHub](https://github.com/pageseeder/bzadmin). Versions loosely track Berlioz: while Berlioz is on 0.14.x, new features and fixes both ship in 0.14.x releases. This roadmap describes direction, not release dates or a promise that every proposal will ship. Discuss substantial features in an issue before implementation, as described in [contributing](CONTRIBUTING.md).

## Next: 0.14.x maintenance

[0.14.1](docs/release/0.14.1.md) was released on 18 November 2026, with the Redirects tab, redaction of system property values and fixes for some known limitations of 0.14.0.

- Address the remaining [known limitations](docs/release/0.14.1.md#known-limitations) where they can be fixed within the overlay. 0.14.1 requires Berlioz 0.14.6, which keeps the container's challenge for every method and the status of errors sent on Tomcat, so the [error page for 401](docs/access.md#error-page-for-401) is no longer needed.
- Make Jetty 12's EE 8 environment on Java 17 the reference container for the next release. Move the packaged-host and staged-host qualification tests to embedded Jetty 12 before changing the compatibility claim; retain Java 11 as the overlay's compilation and compatibility baseline and keep the Tomcat 9 qualification. Gretty still runs the local development host on Jetty 10, so document that difference rather than treating it as Jetty 12 evidence.
- Keep refining keyboard navigation, dialogs, errors, refresh behavior and accessibility based on feedback.
- Add a package filter to the Application performance and status-code tables. Keep their headline metrics application-wide and label that scope when the rows are filtered.
- Let a host configure its main Java package root for a consistent, distinct `[app]` alias in Logs, Application and Services. Keep the full class or logger name available, and retain the built-in aliases for common packages.
- Show simple key type names in cache configuration with the full class name available, and make cache warnings easier to scan.
- Add a text filter for the bounded Recent logs view. Show the relative sizes of listed log files by day using dates in their file names; keep current and undated files separate, and describe file size rather than bytes written that day.

Fixes ship in new versions; see [security and support](SECURITY.md).

## Planned features

Specify new services and changes to published behavior or artifacts in the [feature contracts](docs/design/README.md) before implementation.

### Bounded log analysis

Analyse selected request logs in known NCSA formats, starting with Jetty's default extended NCSA format and basic NCSA. Show tables and pie charts for HTTP status codes, IP ranges and User Agent strings, with filters for IP, User Agent and status. Group large sets of values into leading entries and an Other category. Custom request-log patterns are outside the first version.

For regular application logs in recognized timestamp-and-level formats, starting with the development host's Logback pattern, show log-level counts and proportions over a selected week, with daily trends. Scan files in bounded chunks, report which files and bytes were covered, and distinguish partial results from whole-period totals. Keep parsing and aggregation read-only, without retaining raw log data on the server.

### Session activity

Use servlet session lifecycle events for hourly counts of sessions created and destroyed, kept in a bounded seven-day history with 24-hour and seven-day views. Show an open-session estimate and observed peak where meaningful. These are per-application-instance indicators since collection began, not counts of distinct users or a persistent analytics record; show gaps after a restart rather than filling them with zeroes. Request volume by hour from the log analysis can provide a better guide to quiet maintenance windows.

### Mode-specific admin access

Allow `berlioz.admin.access=off|view|operate` in mode-specific configuration, with `operate` as the default for existing hosts. This setting caps access; it never grants permission that the host did not grant. `off` makes admin requests that reach Berlioz Admin return 404 before its login, redirects or generator execution, including direct generator invocation. `view` permits authorized reads but refuses every admin operation with 403, even for a user granted `OPERATE`. `operate` retains the host's existing `VIEW` and `OPERATE` decisions. Enforce the limit in the filter and generator verdict, including alternate dispatches and control operations; show the effective value on Application Overview and explain disabled operations in the UI.

Log capture continues in every setting. Host authentication or a container constraint that responds before the admin filter can still answer first; hosts needing an unconditional external 404 must block the routes before that layer. This setting does not govern host services or Berlioz control parameters on public URLs.

### Security tab

Add one read-only Security tab that encourages good deployment practice using a small set of observable checks. Show the effective admin access ceiling and installation mappings; flag permissive Berlioz control-network configuration, URL session tracking, explicitly insecure cookie settings, full error detail and profiling where relevant. Reuse the Config Login view's fallback-login status: draw attention to a plain password or active local-proxy setting in production, and report incomplete or invalid configuration without showing the password or hash. Surface a failed configuration load with a link to Config, where the cause and whether Berlioz kept the last valid settings or fell back to defaults are shown. Show the session timeout and whether a control key is configured as facts, without imposing an arbitrary timeout or key-strength score. Reuse existing diagnostics where possible, and link each finding to the relevant guide or existing view. Use individual Attention, Observed and Verify with host results, without an overall security score or an assurance that the application is secure.

Allow `berlioz.admin.environment=development|production` in mode-specific configuration as an optional hint for the Security tab. When unset, report facts and avoid environment-specific warnings; do not infer the environment from `berlioz.mode` or a hostname. In development, describe expected diagnostic and local-HTTP settings without presenting them as production warnings; continue to flag clear access and mapping problems. Show the configured hint prominently so an incorrect hint cannot silently make the page appear safe. The hint does not change permissions, the `berlioz.admin.access` ceiling or the behavior of any operation.

Do not infer effective browser cookies, externally reachable control URLs or transport guarantees from settings that cannot establish them; explain what the host must verify. Opening the tab must not change state, send probe requests or disclose secret values.

### Shared access module

Add a module to this project for the admin access and security code, published as its own artifact, so that other overlays can share it rather than maintain their own copies and security fixes are made once. It would provide the permissions, access, verdict, guards, fallback login and a filter that overlays can configure with their own routes, messages and audit logger.

Keep the class names that hosts already use in `web.xml` and in their access code, or provide a migration path. Overlays deployed in the same host share one copy of the module, so its compatibility rules must be defined. The module becomes the one published runtime dependency other than Berlioz, which requires updating the [build contract](docs/design/build-contract.md) and the dependency boundary check.

## Framework integration

Adopt released Berlioz APIs where they can replace overlay-specific diagnostics, stylesheet resolution and control handling. Keep existing protections until replacements are released, adopted and tested. From 0.14.1, the overlay uses what Berlioz 0.14.6 exposes: the rules its redirect filters loaded, reloads that reach every filter, value-based redaction, the logging startup and the configuration load status. The [proposals for Berlioz](docs/design/berlioz-proposals.md) list what remains: an authorization hook for services, which could simplify generator guards, and diagnostics of Berlioz's own configuration and reload results.

The [shared access module](#shared-access-module) could move into Berlioz if Berlioz provides more specific direction for security.

Propose a shared, opt-in error-alerting helper for the Berlioz ecosystem, in a companion artifact usable without Berlioz Admin. Berlioz could expose a hook after logging configuration so the helper attaches an async appender once and survives reconfiguration. Start with host-provided Logback and a Google Chat webhook; consider Slack and other backends separately. Bound its queue, delivery time and message size, rate-limit alerts, and report delivery failures without exposing the webhook URL. The host continues to choose and configure its logging backend; the admin Logs Configuration view could report whether the helper is attached and healthy.

Qualify a Berlioz 1.0 candidate when available before claiming compatibility.

## Later possibilities

- Additional provider metrics or measured cache use beyond the configured limits already shown, only where reads are side-effect free. Assess separately whether bounded inspection of entry values can meet that rule.
- Clearer cache registration and integration guidance before freezing the public API.
- An option when registering a native Caffeine cache to hide its keys, so that the entries view shows only their number by type; an entry could still be removed by typing its key.
- On-demand PSML reference auditing from a single document's detail view, a selection of documents, or a content subfolder: report `<xref>` targets under `_external` or `_local` that should be published, and targets that do not resolve. Use the same bounded check for one file or a batch, with progress and incomplete-result reporting for batches; consider checking local `<image src>` targets once their published asset roots are defined.
- Changing logging levels temporarily and reloading the logging configuration, from the Configuration view of the Logs page (0.14.1): operations with side effects that need further discussion.
- Log4j2 capture for the Recent events view and configuration diagnostics, separate from the proposed error-alerting helper.
- A filename or directory filter for the log file selector if its date groups are insufficient for directories with many files; distinct from filtering recent event text.
- Bounded exports of admin views.
- Deeper XSLT 3 package analysis beyond the current source-level documentation.
- A Services Exposure view based on the runtime service inventory, if it can distinguish observable concerns from host authorization, CSRF and user-data behavior that a service declaration cannot prove.
- Source-level XSLT security indicators and effective parser restrictions, only where they can be reported without executing host stylesheets or mistaking the overlay's parser settings for the host's.
- A bounded CycloneDX export of visible host libraries, separate from the release SBOM of the overlay runtime graph, with explicit coverage and missing metadata and no outbound vulnerability lookup.
- A fixed-size, per-instance Audit view under Logs with structured operation targets, collection start time and the existing audit logger retained as the durable integration point.
- A Security view of request-log analysis for probe paths, scanner User Agents and 401, 403 and 429 patterns, with the same work limits and coverage reporting as the analysis and with proxy and false-positive limitations explained.

## Scope boundaries

The overlay administers an existing application. It does not own authentication, create or close application caches, choose runtime providers, or execute stylesheets to document them. It is not intended to become a standalone monitoring platform.

Feature behavior and limits belong in the [documentation](docs/README.md). User-visible delivered changes belong in [release notes](docs/release/README.md).
