# Roadmap

Berlioz Admin aims to provide safe, useful administration for Berlioz applications while leaving runtime libraries and authentication under the host's control. See [architecture and principles](docs/architecture.md).

Version `0.14.0` is the first public release, at `org.pageseeder.berlioz:pso-bzadmin`, with source and issue tracking on [GitHub](https://github.com/pageseeder/bzadmin). Versions loosely track Berlioz: while Berlioz is on 0.14.x, new features and fixes both ship in 0.14.x releases. This roadmap describes direction, not release dates or a promise that every proposal will ship. Discuss substantial features in an issue before implementation, as described in [contributing](CONTRIBUTING.md).

## Next: 0.14.x maintenance

- Address [known limitations](docs/release/0.14.0.md#known-limitations) of 0.14.0 where they can be fixed within the overlay, such as fallback login rate limiting and the response to unauthenticated POSTs under container BASIC authentication.
- Qualify Tomcat 9 and newer JDKs before advertising them as tested. Java 11 and Jetty 10 remain the reference environment.
- Extend CI beyond the current qualification workflow: a JDK and container matrix, root and non-root context paths, with and without optional providers, browser smoke tests and build reproducibility.
- Keep refining keyboard navigation, dialogs, errors, refresh behavior and accessibility based on feedback.
- **Prefix mapping.** Support applications that map Berlioz by prefix, the legacy setup, as well as by extension, the recommended setup. The two supported setups are `*.html` with `*.xml`, as Berlioz Kickstart maps them, and `/html/*` with `/xml/*`, mapped by the host. Pages already build most links from the mapping of the request, but XML endpoints used by operations and live updates still assume the `.xml` extension. Build those URLs for the setup in use, `/bzadmin/foo.xml` or `/xml/bzadmin/foo`, and make the operations independent of the stylesheet the host configures for XML responses.
- **Mapping warnings.** Warn in the console at startup when Berlioz Admin is installed but cannot run: the XML mapping that goes with the HTML mapping is missing (`*.xml` with `*.html`, or `/xml/*` with `/html/*`), or `AdminFilter` is not declared or does not cover the admin services. Report only what can be detected and do not guess. If the missing XML mapping can only be detected on the pages, show affected controls as disabled with the reason, as the control buttons already do.
- **Options set to their default.** On the Config page, mark Berlioz options configured with the same value as their default, and count them in the key metrics, so unnecessary settings are easy to find and remove. Redacted values cannot be compared and remain unmarked.
- **Memory explanations.** Explain the rows of the Memory areas and pools table: Eden, Survivor and Old generation, Metaspace, Compressed Class Space and the code cache, and what their usage means, such as why a full Eden is normal while an Old generation that stays full after collections is not. Pool names depend on the garbage collector, so explanations match known names and other pools simply have none.
- **Internal API paths.** Move the services that return only data, the live samples and the POST operations, to `/bzadmin/api/`, as Berlioz direct services without the envelope or XSLT, so they are clearly separate from the pages. These endpoints are internal to bzadmin, and hosts should not rely on them. Remove the routes that nothing uses. Pages keep their URLs, including their `.xml` and `.json` representations.

Fixes ship in new versions; see [security and support](SECURITY.md).

## Planned features

These add services or change published artifacts, so each is specified in the [feature contracts](docs/design/README.md) before implementation.

### Redirects and forwards

Add a tab to the Config page for Berlioz's `RedirectFilter` (redirects) and `RelocationFilter` (forwards, called relocations in Berlioz).

- **Status.** Show each filter's state from its container registration: not mapped, mapped without a `config` parameter, configuration file missing or invalid, or active. The filters only warn in the log and have no effect in the first failure cases, so this makes a silent misconfiguration visible.
- **Rules.** Read the file named by the filter's `config` parameter and list its rules: from, to and, for redirects, whether they are permanent. Berlioz does not expose the rules it has loaded and loads them on first use, so the page says that it shows the file, which can differ from the loaded rules until the next reload.
- **Safe reading.** The read is a side-effect-free `VIEW` operation, limited to files under `WEB-INF`, bounded in size and number of rules, and parsed without DTDs or external entities. Only the expected attributes are output, targets are shown as text rather than links, and query string values are redacted because a target can contain a token.

### Shared access module

Add a module to this project for the admin access and security code, published as its own artifact, so that other overlays can share it rather than maintain their own copies and security fixes are made once. It would provide the permissions, access, verdict, guards, fallback login and a filter that overlays can configure with their own routes, messages and audit logger.

Keep the class names that hosts already use in `web.xml` and in their access code, or provide a migration path. Overlays deployed in the same host share one copy of the module, so its compatibility rules must be defined. The module becomes the one published runtime dependency other than Berlioz, which requires updating the [build contract](docs/design/build-contract.md) and the dependency boundary check.

### System information exposure review

Review what the System page discloses to `VIEW` users. Berlioz redacts system properties by name only, so values are shown for properties such as `java.class.path`, `user.home` and `sun.java.command`, whose value includes any program arguments, and for any property holding a URL with embedded credentials.

Decide what should be hidden, shortened or limited to `OPERATE`, weighing diagnostic value against what paths and user names reveal. Apply the same policy to every output format, and to the other pages that show paths, and propose changes that belong in Berlioz's `Redaction` upstream.

## Framework integration

Adopt released Berlioz APIs where they can replace overlay-specific diagnostics, stylesheet resolution and control handling. A future framework authorization hook could simplify generator guards. Keep existing protections until replacements are released, adopted and tested.

The [shared access module](#shared-access-module) could move into Berlioz if Berlioz provides more specific direction for security.

Qualify a Berlioz 1.0 candidate when available before claiming compatibility.

## Later possibilities

- Richer cache statistics, capacity estimates and side-effect-free entry inspection.
- Clearer cache registration and integration guidance before freezing the public API.
- PSML broken-link auditing.
- Logging configuration diagnostics, to reveal an application whose logging configuration did not load correctly: the file in use, its load status and the effective levels. Temporary debug levels and reloading the configuration need further discussion.
- Additional logging integrations, including Log4j2 recent events.
- Easier selection among many log files: group the file selector by the date in the file names, with current logs first, or add a filter to the list of files.
- Configurable capacity for recent log events, which are limited to the last 256: a global property with a maximum, applied after startup without losing the events already captured.
- Support exports and deeper XSLT package analysis.

## Scope boundaries

The overlay administers an existing application. It does not own authentication, create or close application caches, choose runtime providers, or execute stylesheets to document them. It is not intended to become a standalone monitoring platform.

Feature behavior and limits belong in the [documentation](docs/README.md). User-visible delivered changes belong in [release notes](docs/release/README.md).
