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

To fix in the next Berlioz release; each has a report draft.

| Defect | Effect | Overlay workaround |
| --- | --- | --- |
| `AppInitializer.configureLogger` resets the logback context before parsing the file | When the last logback file it tries fails to load (malformed XML, a file that cannot be read), the context stays as the reset left it: root at DEBUG and no appender, so nothing is logged anywhere, with one console line. Berlioz could configure the console with logback's `BasicConfigurator` when no file loads after a reset, and say so | The [logging configuration](logging-configuration.md) verdict `not-writing` |
| `AppInitializer.configureLogger` reports "logback config file OK" when Joran recorded errors | Joran reports most problems (an unknown appender class, a file appender that cannot open its file) as ERROR statuses, not exceptions, so the part in error is missing without warning. Berlioz could check the statuses since just before `doConfigure` (`StatusUtil.getHighestLevel(threshold)`) and print them (`StatusPrinter2.printInCaseOfErrorsOrWarnings(context, threshold)`), as logback's own initialisation does | The verdict `problems`, with the statuses since the last reset |
| A logging file that fails to load is followed by the next candidate | A malformed `logback-<mode>.xml` is silently replaced by `logback.xml`, with only a console line. Berlioz could report the configuration as failed | The view says that Berlioz goes on to a later candidate, without knowing whether it did |

### Fixed in Berlioz 0.14.6

Fixed on the Berlioz `dev` branch, not released yet. The overlay keeps its workaround until it requires
0.14.6, then removes or simplifies it as below.

| Defect | Fix in Berlioz | Once the overlay requires 0.14.6 |
| --- | --- | --- |
| `RedirectFilter` or `RelocationFilter` without a `config` parameter failed every request with a `NullPointerException` (500) | The filter passes requests through, as its startup warning says. A configuration file without rules is now logged as a warning, and every log message of both filters names the filter instance | The `no-config` report stays useful as information, but no longer warns of failing requests |
| An error dispatch was matched by method | When rendering an error (`javax.servlet.error.status_code` set), `BerliozServlet` looks the service up as GET (HEAD stays HEAD), so the error page and its message are kept for POST, PUT, DELETE, PATCH and QUERY. `ErrorHandlerServlet` handles every method (no more 405 for PUT and DELETE, 501 for PATCH). Resetting an error response keeps `WWW-Authenticate`, `Proxy-Authenticate`, `Allow` and `Retry-After`, which also restores the `Allow` header of a 405 with `berlioz.errors.handle=true` | Remove the documented [error page for 401](../access.md#error-page-for-401) |
| With `berlioz.errors.handle=false`, Berlioz set `javax.servlet.error.exception` before `sendError`, which Tomcat answered 500 | Berlioz sets `javax.servlet.error.exception` and `exception_type` only when it renders the error itself. The exception is passed in a new public attribute, `ErrorHandlerServlet.BERLIOZ_ERROR_EXCEPTION` (`org.pageseeder.berlioz.error_exception`), which `ErrorHandlerServlet` and `GetErrorDetails` read when the standard one is absent. This applies to Jetty as well | Remove the known limitation on Tomcat. Any overlay code that reads the exception of an error sent by Berlioz should fall back to `BERLIOZ_ERROR_EXCEPTION` |
| `redirect-mapping-1.0.dtd` declared `permanent (true \| false)` | The DTD declares `(yes \| no)`, as the schema and the parser do; a test validates against the shipped DTD. The public copy at `pageseeder.org/schema/berlioz/` is not part of the release and needs the same correction | The `invalid` report stays for files that really are invalid; `true` and `false` are now invalid under the DTD as well |

## Diagnostics of Berlioz's own state

### Redirect and relocation filters

`RedirectFilter.diagnosticSnapshot()` and `RelocationFilter.diagnosticSnapshot()`, with a generator,
as `BerliozConfig` and `ErrorHandlerServlet` already do: for each filter instance, its name, the
`config` parameter, whether the file exists, whether rules are loaded, when, how many, the load error,
and the loaded rules (from, to, permanent).

- **Correct:** the view would show the rules Berlioz applies, not a file that can differ until the
  next reload.
- **Efficient and less fragile:** the overlay reads and parses each file again, repeats the DOCTYPE
  handling of `ConfigLoader`, its 1,000,000-byte limit and its pattern parsing, and validates with
  `schema/*.xsd`, which is not a public API.
- **Secure:** the overlay resolves and reads files under `WEB-INF` with its own confinement rules;
  Berlioz already holds the loaded rules.
- **Removes:** `MappingFile`, `MappingParser` and the copied DOCTYPE handling in
  `org.pageseeder.bzadmin.redirect` ([redirects contract](redirects.md)).

### Reloads that reach every component

A reload with the `redirects` scope applies only to a filter that the reload request passes through,
so a filter mapped to some paths keeps its old rules after "Reload everything". Components that load
configuration could register with a reload registry, so that a reload applies to all of them, whatever
their mapping.

- **Correct:** one reload, consistent state.
- **Removes:** the `reload-not-covered` warning and its mapping analysis.

### Logging initialisation

A read-only snapshot of what `AppInitializer.configureLogger` did, available once Berlioz is set up, for example
`GlobalSettings.getLoggingSetup()` with a generator: the logging framework (`logback`, `log4j` or none), the
mode, and each candidate in the order tried, with its path and outcome (`absent`, `loaded`, `loaded-with-errors`,
`failed`, with the class and message of the exception), the file in use, and when.

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
- the reload scopes applied, for generators (only the `X-Berlioz-Reload` response header has them);
- the provenance of the effective cache control of a service;
- a stable schema for `ListRuntimeServices` and the other `@Beta` generators used.

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

### Control parameters on declared services only

Berlioz applies `berlioz-reload` before the service runs, on any request with the parameter and control
authorization. The overlay therefore rejects control parameters on every admin request except three POST
services, each with its own scopes (`AdminFilter.CONTROL_SERVICES`). Berlioz could let a service declare
the control it accepts, such as `control="reload:services"`, and apply control parameters only on such
services, and only for POST.

- **Secure:** a control parameter on an ordinary URL never triggers a reload, in any application, with
  or without the overlay. It would also cover error dispatches: an error page served by `BerliozServlet`
  receives the parameters of the request that failed, so an authorized `berlioz-reload` is applied again
  while the error page renders.
- **Removes:** the control-parameter policy of `AdminFilter`, keeping only its authorization.

### Redaction of values

`Redaction` redacts by name (password, secret, key, token…). Values that hold credentials whatever
their name are shown: a URL with user information (`https://user:password@host/`) in a system property
or a global property, or a token in a query string. The Redirects view now redacts targets itself
(`Targets.redact`), and the System page system properties (`SystemProperties.redact`: URL user
information, parameters with sensitive names, the program arguments of `sun.java.command`). Berlioz could add value-based redaction, such as `Redaction.redactUrl`, used for
properties and redirect targets alike, with the rules of the overlay's
[system properties](metadata-contract.md#system-properties).

- **Secure:** one policy in every generator that writes these values, for every output format.
- **Removes:** the overlay's own redaction of targets and its `GetSystemProperties`.

## Public helpers

- **Thread and JVM metrics:** Berlioz's `Threads` and `OptionalMetrics` helpers are package-private, so
  the overlay keeps its own `OptionalMetrics` and thread details. Making them public, or adding the
  thread origin and activity to Berlioz's `ListThreads`, would remove the copies.

## Not proposed

- **Filter registrations:** the Servlet API exposes them; the overlay reads them directly.
- **Cache providers, log files, PSML roots:** the host owns these integrations; Berlioz has no reason to
  know them.
- **Installation checks:** the overlay checks the mappings it needs at startup; Berlioz could warn about
  an HTML mapping without the XML one, but that is a convenience, not a gap.
