# Redirects and forwards

Implemented in 0.14.1; reads Berlioz's snapshot of the loaded rules since Berlioz 0.14.6. User
documentation: [redirects and forwards](../guides/configuration.md#redirects-and-forwards).

A Redirects view on the Config page (`/bzadmin/config?view=redirects`) shows the state of Berlioz's
`RedirectFilter` (redirects) and `RelocationFilter` (forwards, called relocations in Berlioz) and lists
the rules each filter loaded. It requires `VIEW`, is read-only and side-effect free, and makes silent
misconfigurations visible: in each failure case below, Berlioz only logs and the filter has no effect.

## Berlioz behavior

The design depends on Berlioz 0.14.6:

- **Configuration.** Each filter takes a `config` init parameter, a path that Berlioz resolves against
  `WEB-INF` of the real path of the context, without checking that it stays there. Without the
  parameter, the filter passes requests through and logs a warning. Without a real path (an unexpanded
  WAR), the filter fails to initialize.
- **Loading.** Rules are loaded on the first request through the filter, not at startup. A reload with
  the `redirects` scope, through the Berlioz servlet or a filter, increments a counter; each filter loads
  its rules again on its next request once the counter has changed, whatever its mapping. A file that
  fails to load (missing, not a file, over 1,000,000 bytes, not well-formed, another DOCTYPE than
  Berlioz's, not valid against the schema) leaves the filter without rules until the next reload.
- **Rules.** `from` and `to` are URI patterns (`org.pageseeder.berlioz.furi.URIPattern`). A rule whose
  pattern is missing or does not parse is ignored; a `from` mapped twice is kept, with a warning in the
  log. The first rule that matches wins. Patterns match the request URI, which includes the context path.
- **Effect.** A redirect responds with its `status` (301, 302, 307 or 308; 302 by default, or 301 with
  the deprecated `permanent="yes"`) and `Cache-Control: max-age=86400, must-revalidate`; an unsafe target
  fails the request. A relocation forwards to its target with a `RequestDispatcher` and sets
  `Content-Location`; a target in a protected path such as `WEB-INF` is ignored.
- **Snapshot.** `RedirectFilter.diagnosticSnapshot()` reports every initialized filter instance: name,
  type, `config` (omitted when unsafe), `exists`, `status`, the time of the last load, `reload-pending`,
  a failure message (generic unless `berlioz.errors.detail` is not `minimal`), the counts, and at most
  2000 loaded and 2000 ignored rules, with targets redacted by `Redaction.redactUriPattern`. Reading it
  neither loads nor logs. It is a `ConfigurationSnapshot`, which only writes itself: the overlay captures
  that output into maps (`CapturedOutput`).

## Filter status

The filters are found in `ServletContext.getFilterRegistrations()` by class name, without loading their
classes, and matched to the snapshot by type and filter name. Each registration is reported separately,
with its filter name, URL patterns, servlet names and `config` parameter. The Servlet API does not expose
dispatcher types or filter order, so neither is shown.

| Status | Meaning |
| --- | --- |
| `not-mapped` | No registration of the filter. The view says what the filter does and how to map it; this is not an error. |
| `not-initialized` | Registered, but not in the snapshot: the container did not initialize it, or its initialization failed. |
| `no-config` | Mapped without a `config` parameter: the filter has no effect. |
| `not-loaded` | The filter has not had a request yet; `exists` says whether its file exists. |
| `failed` | The last load failed: the filter has no rules. `exists` and the failure message say why. |
| `loaded` | The rules of the last load, with their count. |

A filter that loaded can have `reload-pending`: a reload was requested and the filter loads its rules
again on its next request. Its rules can have warnings:

- `ignored-rule`, with the `reason` from Berlioz (`missing-from`, `invalid-from`, `missing-to`,
  `invalid-to`): Berlioz ignores the rule.
- `duplicate-from`: a `from` pattern used by an earlier rule; the later rule never applies. Berlioz gives
  each pattern as it compares them.
- `forwards-to-admin`: a relocation whose target is an admin URL. Container security constraints apply
  to the original URL, not to the forward, so a user is not asked to sign in; with the default
  `AdminFilter` mapping (`REQUEST` only), the filter does not run on the forward either, and the admin
  generators deny the request. The page then fails without an obvious cause. Relocation targets are
  dispatched relative to the context, so the warning applies when the literal start of `to`, before any
  template expression, is `/bzadmin` followed by `/`, `.` or the end, or the same under `/html/` or
  `/xml/` when Berlioz is mapped by prefix. A target that starts with an expression, such as `/{+path}`,
  is not reported, since its destination depends on the request.

The view says that it shows the loaded rules, and that patterns include the context path.

## Rules

Loaded and ignored rules are listed together in file order with their position, since the first match
wins. The loaded rules are in file order and the ignored ones have their position, so the loaded rules
take the positions in between; when the list of ignored rules is truncated, the loaded rules after it
have no position.

| Field | Redirects | Relocations |
| --- | --- | --- |
| `from` | Pattern, as text | Pattern, as text |
| `to` | Target pattern, as text, redacted | Target pattern, as text, redacted |
| Effect | `301`, `302`, `307` or `308` | Forward |
| Warnings | `ignored-rule`, `duplicate-from` | The same, and `forwards-to-admin` |

An ignored rule has no effect, and `from` or `to` only when present.

**Redaction.** Berlioz redacts targets (`Redaction.redactUriPattern`): the value of every query
parameter, except a value that is only a URI template expression such as `{q}`, and the password of the
user information, are replaced by `[REDACTED]`; the user name and the fragment are kept. Patterns are
never shown as links.

## Safe reading

- **Access.** `AdminGuard` requires `VIEW` before any work, in every format. Responses are `no-store`,
  with no ETag.
- **Path.** The view shows the `config` parameter of the registration, never an absolute path. Berlioz
  loads a file outside `WEB-INF` when the parameter points there, so the view shows its rules, as the
  filter applies them.
- **Bounds.** Berlioz lists at most 2000 loaded and 2000 ignored rules, with the counts and `limit`;
  values and failure messages are cut at 2048 characters by the overlay, followed by `…`.
- **No effect on the filters.** Reading the snapshot does not load, reload or change the filters' rules,
  and writes nothing to the log.

## Output

A generator `GetRedirectFilters` (package `org.pageseeder.bzadmin.redirect`, content `redirect-filters`)
runs in the `bzadmin-config` service, like the other Config views, and outputs XML and JSON:

```xml
<redirect-filters>
  <filter type="redirect" name="RedirectFilter" config="config/redirect.xml" status="loaded"
          exists="true" loaded="2026-11-12T10:00:00Z" rules="3" ignored="1">
    <mapping url-pattern="/*"/>
    <rule position="1" from="/" to="/html/home" effect="302"/>
    <rule position="2" from="/{+path}.psml" to="/html/{+path}" effect="301"/>
    <rule position="3" from="/{!x}" to="/x">
      <warning code="ignored-rule" reason="invalid-from"/>
    </rule>
    <rule position="4" from="/" to="/new?token=[REDACTED]" effect="302">
      <warning code="duplicate-from" position="1"/>
    </rule>
  </filter>
  <filter type="relocation" name="Forwards" config="config/relocation.xml" status="failed"
          exists="false" loaded="2026-11-12T10:00:00Z" failure="Unable to load the configuration file">
    <mapping url-pattern="/legacy/*"/>
  </filter>
</redirect-filters>
```

- **Filters.** One `filter` per registration, sorted by `type` (`redirect`, then `relocation`), then
  `name`. A type with no registration has one `filter` with only `type` and `status="not-mapped"`.
  `config` is absent for `no-config`. `mapping` has `url-pattern` or `servlet-name`.
- **Load.** `exists` when there is a `config` parameter; `loaded` (ISO 8601) and `failure` for
  `loaded` and `failed`; `reload-pending="true"` only when pending. `rules` (loaded rules), `ignored`
  (only when there are some) and `limit` (when a list is truncated) for `loaded`.
- **Rules.** Only for `loaded`. `effect` is `301`, `302`, `307`, `308` or `forward`, absent for an
  ignored rule. `duplicate-from` gives the `position` of the earlier rule. A value cut at the bound ends
  with `…`.
- **JSON.** The same names in camel case, with arrays for repeated elements:
  `{"filters": [{"type": "redirect", "status": "loaded", "mappings": [...], "rules": [{"position": 1,
  "from": "/", "to": "/html/home", "effect": "302", "warnings": [...]}]}]}`.

Field-level schemas and compatibility notes are in the [metadata contract](metadata-contract.md#redirects-view).

## Presentation

The Config page has a Redirects tab next to the existing views. Each registration has a panel with its
status and what it means, the configuration, mappings, load time and rule count, then a table of rules
(position, from, to, effect and warnings), following the existing table and status styles. The empty
state, when neither filter is mapped, explains the two filters. Long patterns wrap; the table scrolls
horizontally on narrow screens.

## Non-goals

- Editing rules, or a reload button of its own: the Config page's reload requests the `redirects`
  scope, which reaches every filter.
- Showing the file when it differs from the loaded rules, or before the first request: the view shows
  what the filter applies.
- Testing which rule matches a URL. `URIPattern` would allow it later, as a lookup like
  `/bzadmin/services/lookup`.
- Redirects or forwards made by the host's own code or by the container.
- Warning when a rule's `from` matches admin URLs, so that it would redirect or forward admin requests
  away from the overlay. Deferred: it would test every rule against the admin routes in each format and
  mapping, and a rule that catches the Config page also hides the warning. A later version may add it as
  `admin-url`.

## Tests

A test-host descriptor maps both filters, with fixture files for each status and warning: valid,
missing, outside `WEB-INF` (`../` and a link, both loaded), a link in `WEB-INF`, a directory, larger than
the limit, a Berlioz DOCTYPE (removed) and another one, `permanent="true"`, not well-formed, an
unparseable pattern, a duplicate `from`, a query token and a password, statuses 307 and 308, more rules
than listed, a value longer than the bound, relocations to `/bzadmin/`, `/bzadmin.html` and `/{+path}`
(not reported), and a filter mapped to its own path only. Another host maps neither. Tests check the
status and warnings, the redaction in every format, the bounds, that opening the view writes nothing to
the log and does not reload the filters, that a reload reaches a filter the reload request does not pass
through (`reload-pending`, then the new rules), and the access policy: `VIEW` required, no data without
the verdict, `no-store`.

## Decisions

- The view shows the loaded rules from Berlioz's snapshot, not the files (0.14.6). It no longer parses
  the files, confines them to `WEB-INF` or validates them with the schemas of the Berlioz JAR.
- The overlay captures the snapshot's output rather than depending on Berlioz internals, since
  `ConfigurationSnapshot` exposes only `writeTo` and `etag`.
- `forwards-to-admin` ships in 0.14.1; the `admin-url` warning is deferred (see [non-goals](#non-goals)).

## Implementation

Java: `overlay/src/main/java/org/pageseeder/bzadmin/redirect/` (`GetRedirectFilters`, with
`FilterRegistrations` and `Targets`) and `org.pageseeder.bzadmin.berlioz.CapturedOutput`; the servlet
context reaches the generator through the verdict of `AdminFilter` (`AdminGuard.context`). Presentation:
the Redirects view of `overlay/src/main/resources/META-INF/berlioz/xslt/html/bzadmin/config.xsl`. Tests:
`RedirectsTest` on the test host `web-redirects.xml`, with fixtures written at test time;
`CapturedOutputTest` and `TargetsTest`. The dev host maps both filters with example rules
(`test-host/src/main/webapp/WEB-INF/config/redirects.xml`, `relocations.xml`).

## Upstream

The proposals that Berlioz 0.14.6 implemented for this view, and the defects found while building it,
are in the [proposals for Berlioz](berlioz-proposals.md#redirect-and-relocation-filters).
