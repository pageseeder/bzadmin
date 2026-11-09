# Redirects and forwards

Status: proposed for 0.14.1, not implemented. This contract is agreed before implementation; see
[open questions](#open-questions).

A Redirects view on the Config page (`/bzadmin/config?view=redirects`) shows the state of Berlioz's
`RedirectFilter` (redirects) and `RelocationFilter` (forwards, called relocations in Berlioz) and lists
the rules of their configuration files. It requires `VIEW`, is read-only and side-effect free, and makes
silent misconfigurations visible: in each failure case below, Berlioz only logs a warning and the filter
has no effect.

## Berlioz behavior

The design depends on Berlioz 0.14.5 behavior, which these filters do not expose through an API:

- **Configuration.** Each filter takes a `config` init parameter, a path that Berlioz resolves against
  `WEB-INF` of the real path of the context, without checking that it stays there. If the file does not
  exist, the filter logs a warning and has no effect. Without the parameter, it logs that it has no
  effect, but every request through it fails with a `NullPointerException` (500), because the loader
  is called with no file.
- **Loading.** Rules are loaded on the first request through the filter, not at startup, and again
  after a reload with the `redirects` scope, which only applies to a request that passes through the
  filter. If the file is invalid, the filter logs an error and has no rules, until the next reload.
- **Parsing.** `ConfigLoader` removes a DOCTYPE that contains `-//Berlioz` or `//Weborganic`, from
  `<!DOCTYPE ` to the first `>` after it, with a warning; it rejects any other DOCTYPE, resolves no
  external entity, and validates against the schema in the Berlioz JAR (`schema/redirect-mapping-1.0.xsd`, `schema/relocation-mapping-1.0.xsd`).
  For redirects, `permanent` must be `yes` or `no`: the DTD in the Berlioz JAR says `true` or `false`,
  but it is not used, and `permanent="true"` makes the whole file invalid.
- **Rules.** `from` and `to` are URI patterns (`org.pageseeder.berlioz.furi.URIPattern`). A pattern
  that does not parse is ignored with a warning; a `from` mapped twice is kept, with a warning. The first
  rule that matches wins. Patterns match the request URI, which includes the context path.
- **Effect.** A redirect responds with 301 when `permanent="yes"`, otherwise 302, and
  `Cache-Control: max-age=86400, must-revalidate`; an unsafe target fails the request. A relocation
  forwards to its target with a `RequestDispatcher` and sets `Content-Location`; a target in a protected
  path such as `WEB-INF` is ignored.
- **No inspection.** `RedirectConfig` and `RelocationConfig` do not expose their rules, and the filters
  do not expose their configuration, so the view reads the file, which can differ from the loaded rules.

## Filter status

The filters are found in `ServletContext.getFilterRegistrations()` by class name, without loading their
classes. Each registration is reported separately, with its filter name, URL patterns, servlet names and
`config` parameter. The Servlet API does not expose dispatcher types or filter order, so neither is shown.

| Status | Meaning |
| --- | --- |
| `not-mapped` | No registration of the filter. The view says what the filter does and how to map it; this is not an error. |
| `no-config` | Mapped without a `config` parameter: every request through the filter fails (500). |
| `missing` | The file does not exist: the filter has no effect. |
| `unreadable` | The file cannot be read safely: no real path for the context, outside `WEB-INF`, not a regular file, larger than the bound, or an I/O error. The reason is given. Berlioz may still load it. |
| `invalid` | The file is not well-formed, has a DOCTYPE that Berlioz does not remove, or does not match the schema: the filter has no rules once it loads it. The parser message is given. |
| `active` | The file is valid, with the number of rules. |

An `active` filter can also have warnings:

- `ignored-rule`: a rule whose pattern does not parse, which Berlioz ignores.
- `duplicate-from`: a `from` pattern used by an earlier rule; the later rule never applies.
- `forwards-to-admin`: a relocation whose target is an admin URL. Container security constraints apply
  to the original URL, not to the forward, so a user is not asked to sign in; with the default
  `AdminFilter` mapping (`REQUEST` only), the filter does not run on the forward either, and the admin
  generators deny the request. The page then fails without an obvious cause. Relocation targets are
  dispatched relative to the context, so the warning applies when the literal start of `to`, before any
  template expression, is `/bzadmin` followed by `/`, `.` or the end, or the same under `/html/` or
  `/xml/` when Berlioz is mapped by prefix. A target that starts with an expression, such as `/{+path}`,
  is not reported, since its destination depends on the request.
- `reload-not-covered`: no URL pattern of the filter covers the reload operation
  (`/bzadmin/api/config/reload`), so reloading from the Config page does not reload its rules.

The view says that it shows the file, which can differ from the loaded rules until the next reload, and
that patterns include the context path.

## Rules

Rules are listed in file order with their position, since the first match wins:

| Field | Redirects | Relocations |
| --- | --- | --- |
| `from` | Pattern, as text | Pattern, as text |
| `to` | Target pattern, as text, redacted | Target pattern, as text, redacted |
| Effect | 301 (`permanent="yes"`) or 302 | Forward |
| Warnings | `ignored-rule`, `duplicate-from` | The same, and `forwards-to-admin` |

Only these attributes are output; other content is not, and fails schema validation anyway.

**Redaction.** A target can contain a token. In `to`, the value of every query parameter is replaced by
`[redacted]`, except a value that is only a URI template expression such as `{q}`; the user information
of an absolute URL is removed; a fragment is kept. Patterns are never shown as links.

## Safe reading

- **Access.** `AdminGuard` requires `VIEW` before any work, in every format. Responses are `no-store`,
  with no ETag.
- **Path.** The `config` parameter is resolved against `WEB-INF` as Berlioz does, then normalized; the
  real path, with links resolved, must be in the real path of `WEB-INF`. Links are followed, since hosts
  may link configuration files, but a link to a file outside `WEB-INF` is `unreadable`. The view shows
  the parameter, not the absolute path.
- **Bounds.** The file is read only if it is at most 1,000,000 bytes, the limit of Berlioz's loader, so
  that `too-large` means the filter loads no rules from it either; parsing stops at 100,000 elements. At most
  2000 rules are listed, with the total and the truncation reported; values are cut at 2048 characters,
  followed by `…`.
  Parser messages are cut at 512 characters.
- **Parser.** The overlay parses the file itself, with SAX: it removes a Berlioz DOCTYPE exactly as
  `ConfigLoader` does, then disallows any DOCTYPE, external entities and schemas. It validates with the schema from the Berlioz JAR, so that the verdict matches the filter's,
  and parses patterns with `URIPattern`. Berlioz's own loader is not called, because it logs on every load
  and the view must not write to the log when it is opened. The schema resource is not a public Berlioz
  API: if another Berlioz version moves or changes it so that it is missing or cannot be compiled, the
  view does not fail. The file is then checked for well-formedness and patterns only, the output says
  `schema="unavailable"`, and the view says that the file was not validated.
- **No effect on the filters.** Reading the file does not load, reload or change the filters' rules.

## Output

A generator `GetRedirectFilters` (package `org.pageseeder.bzadmin.redirect`, content `redirect-filters`)
runs in the `bzadmin-config` service, like the other Config views, and outputs XML and JSON:

```xml
<redirect-filters schema="validated">
  <filter type="redirect" name="RedirectFilter" config="config/redirect.xml" status="active"
          rules="4" listed="4">
    <mapping url-pattern="/*"/>
    <rule position="1" from="/" to="/html/home" effect="302"/>
    <rule position="2" from="/{+path}.psml" to="/html/{+path}" effect="301"/>
    <rule position="3" from="/{!x}" to="/x" effect="302">
      <warning code="ignored-rule"/>
    </rule>
    <rule position="4" from="/" to="/new?token=[redacted]" effect="302">
      <warning code="duplicate-from" position="1"/>
    </rule>
  </filter>
  <filter type="relocation" name="Forwards" config="config/relocation.xml" status="unreadable"
          reason="outside-web-inf">
    <mapping url-pattern="/legacy/*"/>
    <warning code="reload-not-covered"/>
  </filter>
</redirect-filters>
```

- **Filters.** One `filter` per registration, sorted by `type` (`redirect`, then `relocation`), then
  `name`. A type with no registration has one `filter` with only `type` and `status="not-mapped"`.
  `config` is absent for `no-config`. `mapping` has `url-pattern` or `servlet-name`.
- **Statuses.** `reason` is given for `unreadable`: `no-real-path`, `outside-web-inf`, `not-file`,
  `too-large` or `io-error`; `message` is given for `invalid` and `io-error`. `rules` (rules in the file)
  and `listed` are given when the file was parsed, `active` or not; `listed` is less than `rules` when the
  list is truncated.
- **Warnings.** `reload-not-covered` is a `warning` of the filter, given whatever its status. The others
  are `warning`s of a rule; `duplicate-from` gives the `position` of the earlier rule.
- **Rules.** `effect` is `301`, `302` or `forward`. A value cut at the bound ends with `…`.
- **JSON.** The same names in camel case, with arrays for repeated elements:
  `{"schema": "validated", "filters": [{"type": "redirect", "status": "active", "mappings": [...],
  "warnings": [...], "rules": [{"position": 1, "from": "/", "to": "/html/home", "effect": "302",
  "warnings": [...]}]}]}`.

Field-level schemas and compatibility notes go in the [metadata contract](metadata-contract.md) with
the implementation.

## Presentation

The Config page gets a Redirects tab next to the existing views. Each filter type has a section with its
status, the registration details and warnings, then a table of rules (position, from, to, effect),
following the existing table and status styles. The empty state, when neither filter is mapped, explains
the two filters and links to the Berlioz documentation. Long patterns wrap; the table scrolls
horizontally on narrow screens.

## Non-goals

- Editing rules, or a reload button of its own: the Config page's reload already requests the
  `redirects` scope, and the view says whether it reaches each filter.
- Showing the rules Berlioz has loaded, which needs a Berlioz API.
- Testing which rule matches a URL. `URIPattern` would allow it later, as a lookup like
  `/bzadmin/services/lookup`.
- Redirects or forwards made by the host's own code or by the container.
- Warning when a rule's `from` matches admin URLs, so that it would redirect or forward admin requests
  away from the overlay. Deferred: it would test every rule against the admin routes in each format and
  mapping, and a rule that catches the Config page also hides the warning. A later version may add it as
  `admin-url`.

## Tests

A test-host descriptor maps both filters, with fixture files for each status and warning: valid,
missing, outside `WEB-INF` (`../`), a link out of `WEB-INF`, larger than the bound, a Berlioz DOCTYPE (removed) and another one (invalid),
`permanent="true"`, an unparseable pattern, a duplicate `from`, a query token, relocations to
`/bzadmin/`, `/bzadmin.html` and `/{+path}` (not reported), and a mapping that does not cover the reload
operation. Another host
maps neither. Tests check the status and warnings, the redaction in every format, the bounds, that
opening the view writes nothing to the log and does not change the filters' behavior, and the access
policy: `VIEW` required, no data without the verdict, `no-store`.

## Decisions

- Links are followed when their real path stays in `WEB-INF`.
- The bounds are 1,000,000 bytes per file, as Berlioz, and 2000 listed rules.
- Validation uses the schema of the Berlioz JAR, so the verdict matches the filter's, and degrades to
  no validation, without failing, if a Berlioz update removes or changes it.
- `forwards-to-admin` ships in 0.14.1; the `admin-url` warning is deferred (see [non-goals](#non-goals)).

## Open questions

1. **Upstream.** Propose to Berlioz: a way to inspect the loaded rules and their load status,
   correcting the DTD for `permanent`, and a filter without `config` that has no effect instead of
   failing every request. Track them with the other upstream items in the
   [metadata contract](metadata-contract.md).
