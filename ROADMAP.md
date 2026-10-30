# Roadmap

Berlioz Admin aims to provide safe, useful administration for Berlioz applications while leaving runtime libraries and authentication under the host's control. See [architecture and principles](docs/architecture.md).

Version `0.14.0` is the first public release, at `org.pageseeder.berlioz:pso-bzadmin`, with source and issue tracking on [GitHub](https://github.com/pageseeder/bzadmin). This roadmap describes direction, not release dates or a promise that every proposal will ship. Discuss substantial features in an issue before implementation, as described in [contributing](CONTRIBUTING.md).

## Next: 0.14.x maintenance

- Address [known limitations](docs/release/0.14.0.md#known-limitations) of 0.14.0 where they can be fixed within the overlay, such as fallback login rate limiting and the response to unauthenticated POSTs under container BASIC authentication.
- Qualify Tomcat 9 and newer JDKs before advertising them as tested. Java 11 and Jetty 10 remain the reference environment.
- Extend CI beyond the current qualification workflow: a JDK and container matrix, root and non-root context paths, with and without optional providers, browser smoke tests and build reproducibility.
- Keep refining keyboard navigation, dialogs, errors, refresh behavior and accessibility based on feedback.

Fixes ship in new versions; see [security and support](SECURITY.md).

## Framework integration

Adopt released Berlioz APIs where they can replace overlay-specific diagnostics, stylesheet resolution and control handling. A future framework authorization hook could simplify generator guards. Keep existing protections until replacements are released, adopted and tested.

Qualify a Berlioz 1.0 candidate when available before claiming compatibility.

## Later possibilities

- Richer cache statistics, capacity estimates and side-effect-free entry inspection.
- Clearer cache registration and integration guidance before freezing the public API.
- PSML broken-link auditing.
- Additional logging integrations, including Log4j2 recent events.
- Support exports and deeper XSLT package analysis.

## Scope boundaries

The overlay administers an existing application. It does not own authentication, create or close application caches, choose runtime providers, or execute stylesheets to document them. It is not intended to become a standalone monitoring platform.

Feature behavior and limits belong in the [documentation](docs/README.md). User-visible delivered changes belong in [release notes](docs/release/README.md).
