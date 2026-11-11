# Proposals for Berlioz

What Berlioz could fix or expose so that Berlioz Admin needs fewer workarounds. Berlioz's
responsibility is functional; these proposals only concern behavior and state that Berlioz already
owns, where handling them inside Berlioz is more correct, more efficient or more secure than
reconstructing them in an overlay. Each lists what the overlay would then remove. The overlay keeps
its current code until a replacement is released, adopted and tested
([architecture](../architecture.md)).

Berlioz 0.14.5 is the reference; fixes made since are marked with the release that will include them.
Items are grouped by kind, most valuable first.

## Principle

A proposal belongs in Berlioz when the overlay would otherwise:

- **copy Berlioz internals**: parse Berlioz's files a second time, repeat its rules, or depend on
  resources that are not a public API, so that it can drift from what Berlioz does;
- **guess Berlioz's state** that only Berlioz knows, such as what it loaded, when, and whether it failed;
- **repeat a security check** on every component, where one check inside Berlioz would cover them all,
  including components added later.

Administration policy stays in the overlay: who may see or operate, the fallback login, CSRF and
origin checks, audit, presentation, and the optional integrations that the host owns (caches, logging
backends, PSML). Berlioz must not depend on Berlioz Admin.

## Defects

None open. Defects to fix in the next Berlioz release are listed here, each with a report draft.

### Fixed in Berlioz 0.14.6

Fixed on the Berlioz `dev` branch, not released yet. The overlay keeps its workaround until it requires
0.14.6, then removes or simplifies it as below.

| Defect | Fix in Berlioz | Once the overlay requires 0.14.6 |
| --- | --- | --- |
| `RedirectFilter` or `RelocationFilter` without a `config` parameter failed every request with a `NullPointerException` (500) | The filter passes requests through, as its startup warning says. A configuration file without rules is now logged as a warning, and every log message of both filters names the filter instance | The `no-config` report stays useful as information, but no longer warns of failing requests |
| An error dispatch was matched by method | When rendering an error (`javax.servlet.error.status_code` set), `BerliozServlet` looks the service up as GET (HEAD stays HEAD), so the error page and its message are kept for POST, PUT, DELETE, PATCH and QUERY. `ErrorHandlerServlet` handles every method (no more 405 for PUT and DELETE, 501 for PATCH). Resetting an error response keeps `WWW-Authenticate`, `Proxy-Authenticate`, `Allow` and `Retry-After`, which also restores the `Allow` header of a 405 with `berlioz.errors.handle=true` | Remove the documented [error page for 401](../access.md#error-page-for-401) |
| With `berlioz.errors.handle=false`, Berlioz set `javax.servlet.error.exception` before `sendError`, which Tomcat answered 500 | Berlioz sets `javax.servlet.error.exception` and `exception_type` only when it renders the error itself. The exception is passed in a new public attribute, `ErrorHandlerServlet.BERLIOZ_ERROR_EXCEPTION` (`org.pageseeder.berlioz.error_exception`), which `ErrorHandlerServlet` and `GetErrorDetails` read when the standard one is absent. This applies to Jetty as well | Remove the known limitation on Tomcat. Any overlay code that reads the exception of an error sent by Berlioz should fall back to `BERLIOZ_ERROR_EXCEPTION` |
| `AppInitializer.configureLogger` reset the logback context before parsing the file, so a last file that failed to load (malformed XML, a file that cannot be read) left root at DEBUG with no appender: nothing was logged | When no logback file that exists can be loaded, Berlioz configures a console appender at INFO and says so on the console and in a warning | The [logging configuration](logging-configuration.md) verdict `not-writing` no longer arises from a failed file after Berlioz's reset; keep it for configurations that write nowhere |
| `AppInitializer.configureLogger` reported "logback config file OK" when Joran recorded errors. With logback 1.5, an appender with an unknown class leaves the whole file unapplied (root at DEBUG, no appender), not only that appender | Berlioz prints the WARN and ERROR statuses added by the file, reports it as loaded with errors and logs a warning; the file is kept, as logback does | Keep the verdict `problems`: Berlioz reports on the console and in the log, which the overlay does not read |
| A logging file that failed to load was silently replaced by the next candidate, for example `logback.xml` after a malformed `logback-<mode>.xml` | Berlioz says on the console that it uses the later file and logs a warning naming the failed one | The view can refer to that warning; knowing which file is in use still needs the [logging initialisation](#logging-initialisation) snapshot |
| An error dispatch applied `berlioz-reload` again: an error page receives the parameters of the request that failed, so an authorized reload that caused the error, such as `berlioz-reload=services` with an invalid services file, was requested again while the error page rendered | Reload scopes are never applied while an error is rendered (`javax.servlet.error.status_code` set), by `BerliozServlet`, `RedirectFilter` and `RelocationFilter` alike. `berlioz-profile` still applies | Nothing to remove: the overlay has no workaround. An error on a control POST no longer reloads twice |
| Rules reloaded by `RedirectFilter` or `RelocationFilter` were held in a field that was not safely published, so other request threads were not guaranteed to see them | The rules are published through a `volatile` field | Nothing to remove |
| `redirect-mapping-1.0.dtd` declared `permanent (true \| false)` | The DTD declares `(yes \| no)`, as the schema and the parser do; a test validates against the shipped DTD. The public copy at `pageseeder.org/schema/berlioz/` is not part of the release and needs the same correction | The `invalid` report stays for files that really are invalid; `true` and `false` are now invalid under the DTD as well |

## Implemented in Berlioz 0.14.6

Implemented on the Berlioz `dev` branch, not released yet (Berlioz theme 18, items 11 and 12). The overlay
keeps its code until it requires 0.14.6.

### Redirect and relocation filters

`GetRedirectConfig` (content `redirect-config`) and `RedirectFilter.diagnosticSnapshot()` (the same as
`RelocationFilter.diagnosticSnapshot()`) report every initialized filter instance, sorted by type, then
name: `name`, `type` (`redirect` or `relocation`), `config` (the parameter, relative to `WEB-INF`,
omitted when unsafe), `exists`, `status` (`no-config`, `not-loaded`, `loaded`, `failed`), `loaded` (time
of the last load), `reload-pending`, `failure` (generic, or the loader's message and its cause when
`berlioz.errors.detail` is not `minimal`), `rule-count`, `limit` (when more than 2000 rules), and the
rules loaded: `from`, `to` and, for redirects, `permanent`. Targets are redacted with the overlay's
rules (user information removed, query values other than a template expression `[REDACTED]`). XML and
JSON; one snapshot per request for the content and the ETag.

Once the overlay requires 0.14.6:

- **Removes:** `MappingFile`, `MappingParser`, the copied DOCTYPE handling and the use of
  `schema/*.xsd` in `org.pageseeder.bzadmin.redirect`, and `Targets.redact` for rule targets. The view
  shows the rules Berlioz applies, not the file.
- **Keeps:** the filter registrations from the Servlet API (`not-mapped`, URL patterns, servlet names),
  matched to the snapshot by filter name; the rule positions, `duplicate-from` and `forwards-to-admin`,
  computed from the loaded rules.
- **Changes:** `missing`, `unreadable` and `invalid` become `failed` with `exists` and the failure
  message; `not-loaded` is new (a filter loads its rules on its first request). `ignored-rule` is lost:
  Berlioz drops a rule whose pattern does not parse and only logs it. If the view must keep it, keep the
  parser for that warning only, or propose that Berlioz report ignored rules.

### Reloads that reach every filter

A reload with the `redirects` scope applied only to the filters that the reload request passed
through. Berlioz now keeps a reload counter, incremented once per request with the authorized
`redirects` scope, by `BerliozServlet` or by a filter; each filter reloads its rules on its next request
once the counter has changed. `X-Berlioz-Reload` lists `redirects` for a reload through the servlet when
a filter with a `config` file is initialized. The reload is lazy: an invalid file shows on the filter's
next request, as `status="failed"` in the snapshot, not in the reload response; `reload-pending` shows
a reload not applied yet. A registry of reloadable components, which would reload on the control request
itself, was set aside as more mechanism than the problem needs (2026-11-11).

Once the overlay requires 0.14.6:

- **Removes:** the `reload-not-covered` warning and its mapping analysis (`FilterRegistrations.matches`
  for the reload path).
- **Changes:** "Reload everything" reports `redirects` whenever the host maps a filter with a
  `config` file, whatever its mapping.

## Diagnostics of Berlioz's own state

### Logging initialisation

A read-only snapshot of what `AppInitializer.configureLogger` did, available once Berlioz is set up, for example
`GlobalSettings.getLoggingSetup()` with a generator: the logging framework (`logback`, `log4j` or none), the
mode, and each candidate in the order tried, with its path and outcome (`absent`, `loaded`, `loaded-with-errors`,
`failed`, with the class and message of the exception), the file in use, and when.

Berlioz configures logging because the configuration differs for each mode: production may turn off DEBUG for
some packages and add appenders that send alerts, such as SMTP. Berlioz keeps that involvement minimal, but only
Berlioz knows what it loaded, so it should say so. What matters to developers is a configuration that works in
development and fails on staging or production: the snapshot shows that on the server itself.

- **Correct:** the overlay would show which file loaded and which failed before it. logback does not keep its
  configuration file (since 1.5.27, only when `scan` is on, and a reset clears it), no status marks Berlioz's
  reset, and logback's configuration events are documented as intended for internal testing.
- **Less fragile:** the overlay copies the order of the candidates, which can change with Berlioz.
- **Removes:** the overlay's copy of the candidate order in the
  [logging configuration](logging-configuration.md#configuration-files).

### Configuration and reload results

Already tracked in the [metadata contract](metadata-contract.md):

- the effective value of each global property and its origin (file, deprecation alias, system
  property);
- whether a configuration reload failed: Berlioz keeps the last valid configuration and only logs it;
- the provenance of the effective cache control of a service;
- a stable schema for `ListRuntimeServices`.

## Security inside Berlioz

### An authorization hook for services

Every Berlioz generator used by an admin service is wrapped in a delegate that checks the verdict of
`AdminFilter` before calling it (12 delegates in `org.pageseeder.bzadmin.guard`), so that a generator
invoked outside the filter's route denies. A hook in Berlioz, checked before any generator of a service
or group runs, would do this once: for example an authorizer declared on a services group, called with
the request and the service, failing closed when it denies or fails.

- **Secure:** a generator added to an admin service later is covered without a new delegate; direct
  invocation goes through the same check.
- **Efficient:** one check per request instead of one per generator.
- **Removes:** the guard delegates, and the class names shown through them.

### Redaction of values

`Redaction` redacts by name (password, secret, key, token…). Values that hold credentials whatever
their name are shown: a URL with user information (`https://user:password@host/`) in a system property
or a global property, or a token in a query string. The Redirects view now redacts targets itself
(`Targets.redact`; from Berlioz 0.14.6, the [filter snapshot](#redirect-and-relocation-filters) redacts
them with the same rules), and the System page system properties (`SystemProperties.redact`: URL user
information, parameters with sensitive names, the program arguments of `sun.java.command`). Berlioz could add value-based redaction, such as `Redaction.redactUrl`, used for
properties and redirect targets alike, with the rules of the overlay's
[system properties](metadata-contract.md#system-properties).

- **Secure:** one policy in every generator that writes these values, for every output format.
- **Removes:** the overlay's `GetSystemProperties`.

## Rejected

Proposed and declined on 2026-11-11. The overlay keeps the code named in each item.

- **Control parameters on declared services only:** let a service declare the control it accepts, such
  as `control="reload:services"`, and apply control parameters only on such services, and only for POST.
  Which admin URLs accept control is administration policy, which stays in the overlay
  ([principle](#principle)). Berlioz already closes control parameters by default and requires
  authorization for them. A service attribute would add configuration to Berlioz and break every
  application that uses `?berlioz-reload=` today. Its strongest argument, control applied again while an
  error page renders, is fixed in 0.14.6 ([defects](#fixed-in-berlioz-0146)). The overlay keeps its
  control-parameter policy (`AdminFilter.CONTROL_SERVICES`).
- **Reload scopes applied, for generators:** the generators that run on a control request are admin
  generators, and the `X-Berlioz-Reload` response header already gives the client the scopes applied;
  the frontend reads it.
- **Stable schemas for every `@Beta` generator used:** too open-ended to commit to. A stable
  `ListRuntimeServices` schema is planned for Berlioz 1.0; another generator can be proposed on its own,
  with the fields the overlay relies on.
- **Public thread and JVM metric helpers:** make Berlioz's package-private `Threads` and
  `OptionalMetrics` public, or add the thread origin and activity to Berlioz's `ListThreads`. Thread
  classification is a presentation heuristic, which Berlioz already decided stays in the overlay, and
  public helpers would become API that Berlioz must keep stable for a few lines of MBean access. A metric
  that Berlioz generators lack can be proposed as a field of the generator. The overlay keeps
  `OptionalMetrics`, `ThreadOrigin`, `ThreadDetails`, `ListThreads` and `GetThreadInfo`.

## Not proposed

- **Filter registrations:** the Servlet API exposes them; the overlay reads them directly.
- **Cache providers, log files, PSML roots:** the host owns these integrations; Berlioz has no reason to
  know them.
- **Installation checks:** the overlay checks the mappings it needs at startup; Berlioz could warn about
  an HTML mapping without the XML one, but that is a convenience, not a gap.
