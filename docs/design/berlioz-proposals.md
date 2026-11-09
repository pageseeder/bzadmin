# Proposals for Berlioz

What Berlioz could fix or expose so that Berlioz Admin needs fewer workarounds. Berlioz's
responsibility is functional; these proposals only concern behavior and state that Berlioz already
owns, where handling them inside Berlioz is more correct, more efficient or more secure than
reconstructing them in an overlay. Each lists what the overlay would then remove. The overlay keeps
its current code until a replacement is released, adopted and tested
([architecture](../architecture.md)).

Berlioz 0.14.5 is the reference. Items are grouped by kind, most valuable first.

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
| `RedirectFilter` or `RelocationFilter` without a `config` parameter | Every request through the filter fails with a `NullPointerException` (500), although it logs that it has no effect | The Redirects view reports it as `no-config` |
| An error dispatch is matched by method | For a POST or other non-GET request, the error page loses its detail ("Only the following are allowed: GET,HEAD") and `res.reset()` removes the headers set before the error, including the `WWW-Authenticate` challenge of container BASIC authentication | A documented [error page for 401](../access.md#error-page-for-401) |
| `redirect-mapping-1.0.dtd` declares `permanent (true \| false)` | The schema that Berlioz validates against requires `yes` or `no`, so a file written to the DTD is invalid and the filter has no rules | The Redirects view reports it as `invalid` |

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
  or without the overlay.
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
