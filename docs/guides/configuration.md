# Logs, redirects and stylesheet customization

## Log files

Set the global property `berlioz.admin.logs` to comma-separated files or directories relative to the application's data folder. When it is absent, logback file appenders supply the locations if logback is available. Only allowed files are listed; reads are bounded and do not follow escaping symbolic links. See [logs design](../design/logs.md).

In the Files view, the selector lists files without a date in their name first, such as the live `app.log`, then dated files by month (`app-2026-11-06.log`) or by year (`app-2026-11.log`), newest first. A date has `-` or `_` between its parts; names such as `app-20261106.log` are listed with the undated files.

Recent events require logback. File reading works without a logging backend when locations are configured explicitly. Changing the recent-event capture threshold does not change application logger levels.

The Configuration view says whether logging writes events, and why not when it does not: appenders that are not started, loggers with no output, logback's error and warning messages, and the logging files that Berlioz tried at startup, with the one in use. Every view of the Logs page shows a notice when logging loses events or writes nowhere. It requires logback 1.5; with another logging framework, it shows only what Berlioz did at startup. It is read-only: it does not change levels or reload the configuration. See [logging configuration](../design/logging-configuration.md).

By default, the Recent view holds the last 256 events. To hold more or fewer, set `berlioz.admin.logs.recent-capacity` to a number from 1 to 4096; a value out of range is brought within it, and one that is not a number is ignored, with a warning in the log. The capture starts before Berlioz loads its configuration, so the number applies once the configuration is loaded, and again whenever it is reloaded; the most recent events that fit are kept. Each event holds at most about 8,000 characters of message and stack trace, so 4096 events take at most around 80 MB, and usually a few megabytes.

```xml
<berlioz>
  <admin>
    <logs recent-capacity="1000"/>
  </admin>
</berlioz>
```

## PSML documents

The read-only repository uses `bastille.psml.root` (the property name is retained for compatibility; Bastille is not required). See [PSML design](../design/psml.md) for root configuration and bounds.

## Redirects and forwards

The Redirects tab of the Config page (`/bzadmin/config.html?view=redirects`) shows each registration of
Berlioz's `RedirectFilter` (redirects) and `RelocationFilter` (forwards) in `web.xml`, and the rules it
loaded from the file named by its `config` parameter, relative to `WEB-INF`. It needs no configuration
and only `VIEW` permission; it changes nothing.

The tab shows the rules Berlioz loaded, as Berlioz reports them. A filter loads its rules on its first
request, and again on its next request after **Reload everything**, whatever its mapping. After editing
a file, reload: the tab shows `reload pending` until the filter's next request, then the new rules.
Patterns match the request path including the context path, and the first rule that matches wins.
Passwords and query parameter values in targets are redacted.

| Status | Meaning | Fix |
| --- | --- | --- |
| `loaded` | Berlioz applies these rules. | — |
| `not-loaded` | The filter has had no request yet. | — |
| `no-config` | The filter has no `config` parameter: it has no effect. | Add the parameter, or remove the filter. |
| `failed` | Berlioz could not load the file: the filter has no effect. The file does not exist or is not a file, is larger than 1,000,000 bytes, is not well-formed, has another DOCTYPE than Berlioz's, or does not match its schema. With `berlioz.errors.detail` other than `minimal`, the message says why. | Fix the file, then reload. For redirects, use `status` (`301`, `302`, `307` or `308`); the deprecated `permanent` must be `yes` or `no`, not `true`. |
| `not-initialized` | The container did not initialize the filter. | See the startup log. |

Warnings:

- **ignored:** a pattern is missing or does not parse, and Berlioz ignores the rule.
- **same as #n:** the pattern of an earlier rule is the same, so this rule never applies.
- **redirected again by #n:** the same filter redirects the target again with rule n, so the browser
  makes one more request. Redirect to the final target instead.
- **redirect loop via #n:** following the redirects comes back to a rule already followed, so the
  browser stops with a "too many redirects" error. Change one of the targets, then tell users to clear
  their cache for those URLs, since browsers keep the redirects for a day.

The view follows only targets whose path has no template expression, such as `/home.html` but not
`/html/{+path}`, within the same filter and its URL patterns. Forwards, absolute URLs and chains from one
filter to another are not followed: see [chains and loops](../design/redirects.md#chains-and-loops).
- **forwards to admin:** a forward to an admin URL. Container security constraints do not apply to
  forwards, and `AdminFilter` is mapped to direct requests by default, so the forwarded request is
  denied (403). Link or redirect to the admin URL instead; or, with a host authentication filter, map it
  and `AdminFilter` with `FORWARD` as well, as [mapping the filter](../access.md#1-map-the-filter) explains.

Berlioz sends its redirects with `Cache-Control: max-age=86400`, so browsers keep following the old
target of a changed rule for a day. See the [redirects contract](../design/redirects.md).

## Stylesheet overrides

No host stylesheet is needed: when `WEB-INF/xslt/html/bzadmin.xsl` does not exist, Berlioz uses the
group stylesheet of the JAR (`META-INF/berlioz/xslt/html/bzadmin.xsl`), and likewise for `xslt/xml/bzadmin.xsl`,
which removes the Berlioz header from XML responses. A host can still override
it with that file, for example to import the overlay's and change some templates:

```xml
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:import href="classpath:META-INF/berlioz/xslt/html/bzadmin.xsl"/>
</xsl:stylesheet>
```

Earlier Berlioz 0.14 versions required this file; hosts upgrading to 0.14.4 can delete it.
