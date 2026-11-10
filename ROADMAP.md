# Roadmap

Berlioz Admin aims to provide safe, useful administration for Berlioz applications while leaving runtime libraries and authentication under the host's control. See [architecture and principles](docs/architecture.md).

Version `0.14.0` is the first public release, at `org.pageseeder.berlioz:pso-bzadmin`, with source and issue tracking on [GitHub](https://github.com/pageseeder/bzadmin). Versions loosely track Berlioz: while Berlioz is on 0.14.x, new features and fixes both ship in 0.14.x releases. This roadmap describes direction, not release dates or a promise that every proposal will ship. Discuss substantial features in an issue before implementation, as described in [contributing](CONTRIBUTING.md).

## Next: 0.14.x maintenance

[0.14.1](docs/release/0.14.1.md) is in preparation, with the Redirects tab, redaction of system property values and fixes for some known limitations of 0.14.0.

- Address the remaining [known limitations](docs/release/0.14.1.md#known-limitations) where they can be fixed within the overlay. The missing challenge for requests other than GET under container BASIC authentication comes from Berlioz's error handling; hosts can avoid it with an [error page for 401](docs/access.md#error-page-for-401) until Berlioz preserves the challenge.
- Java 11 and Jetty 10 remain the reference environment; from 0.14.1, the overlay and packaged-host tests also run on Java 17, 21 and 25, and the packaged-host tests on Tomcat 9.
- Extend CI beyond the current qualification workflow: staged-artifact tests on newer JDKs and Tomcat, root and non-root context paths, with and without optional providers, browser smoke tests and build reproducibility.
- Keep refining keyboard navigation, dialogs, errors, refresh behavior and accessibility based on feedback.

Fixes ship in new versions; see [security and support](SECURITY.md).

## Planned features

These add services or change published artifacts, so each is specified in the [feature contracts](docs/design/README.md) before implementation.

### Shared access module

Add a module to this project for the admin access and security code, published as its own artifact, so that other overlays can share it rather than maintain their own copies and security fixes are made once. It would provide the permissions, access, verdict, guards, fallback login and a filter that overlays can configure with their own routes, messages and audit logger.

Keep the class names that hosts already use in `web.xml` and in their access code, or provide a migration path. Overlays deployed in the same host share one copy of the module, so its compatibility rules must be defined. The module becomes the one published runtime dependency other than Berlioz, which requires updating the [build contract](docs/design/build-contract.md) and the dependency boundary check.

## Framework integration

Adopt released Berlioz APIs where they can replace overlay-specific diagnostics, stylesheet resolution and control handling. A future framework authorization hook could simplify generator guards. Keep existing protections until replacements are released, adopted and tested. The [proposals for Berlioz](docs/design/berlioz-proposals.md) list the defects found and what Berlioz could expose: the rules its redirect filters loaded, reloads that reach every component, an authorization hook for services, control parameters on declared services only, and value-based redaction.

The [shared access module](#shared-access-module) could move into Berlioz if Berlioz provides more specific direction for security.

Qualify a Berlioz 1.0 candidate when available before claiming compatibility.

## Later possibilities

- Richer cache statistics, capacity estimates and side-effect-free entry inspection.
- Clearer cache registration and integration guidance before freezing the public API.
- An option when registering a native Caffeine cache to hide its keys, so that the entries view shows only their number by type; an entry could still be removed by typing its key.
- PSML broken-link auditing.
- Logging configuration diagnostics, to reveal an application whose logging configuration did not load correctly: the file in use, its load status and the effective levels. Temporary debug levels and reloading the configuration need further discussion.
- Additional logging integrations, including Log4j2 recent events.
- Easier selection among many log files: group the file selector by the date in the file names, with current logs first, or add a filter to the list of files.
- Support exports and deeper XSLT package analysis.

## Scope boundaries

The overlay administers an existing application. It does not own authentication, create or close application caches, choose runtime providers, or execute stylesheets to document them. It is not intended to become a standalone monitoring platform.

Feature behavior and limits belong in the [documentation](docs/README.md). User-visible delivered changes belong in [release notes](docs/release/README.md).
