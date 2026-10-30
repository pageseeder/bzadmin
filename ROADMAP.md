# Roadmap

Berlioz Admin aims to provide safe, useful administration for Berlioz applications while leaving runtime libraries and authentication under the host's control. See [architecture and principles](docs/architecture.md).

Version `0.14.0` is being prepared for public release. This roadmap describes direction, not release dates or a promise that every proposal will ship.

## Next: a stable public release

- Stabilize the interface: keyboard navigation, dialogs, errors, refresh behavior and maintenance operations.
- Qualify the final candidate as an actual Maven and Gradle dependency, at root and non-root context paths, with and without optional providers.
- Define supported containers and JDKs. Jetty 10 and Java 11 are the reference environment; Tomcat 9 and newer JDKs require qualification before being advertised as tested.
- Complete security, bounded-work, lifecycle and accessibility checks, and review the candidate's dependencies.
- Finalize public source and package destinations, package metadata, signing and a private vulnerability-reporting contact. Confirm asset provenance and publish installation, upgrade and release information for the selected artifact.

The public build coordinates are `org.pageseeder.berlioz:pso-bzadmin`. Preparing distribution materials does not mean a public release has occurred.

## Framework integration

Adopt released Berlioz APIs where they can replace overlay-specific diagnostics, stylesheet resolution and control handling. A future framework authorization hook could simplify generator guards. Keep existing protections until replacements are released, adopted and tested.

Qualify a Berlioz 1.0 candidate when available before claiming compatibility.

## Later possibilities

- Richer cache statistics, capacity estimates and side-effect-free entry inspection.
- Clearer cache registration and integration guidance before freezing the public API.
- PSML broken-link auditing.
- Additional logging integrations, including Log4j2 recent events.
- Support exports and deeper XSLT package analysis.
- Automated CI for the documented qualification sequence.

## Scope boundaries

The overlay administers an existing application. It does not own authentication, create or close application caches, choose runtime providers, or execute stylesheets to document them. It is not intended to become a standalone monitoring platform.

Feature behavior and limits belong in the [documentation](docs/README.md). User-visible delivered changes belong in [release notes](docs/release/README.md).
