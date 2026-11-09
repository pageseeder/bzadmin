# Logs, redirects and stylesheet customization

## Log files

Set the global property `berlioz.admin.logs` to comma-separated files or directories relative to the application's data folder. When it is absent, logback file appenders supply the locations if logback is available. Only allowed files are listed; reads are bounded and do not follow escaping symbolic links. See [logs design](../design/logs.md).

Recent events require logback. File reading works without a logging backend when locations are configured explicitly. Changing the recent-event capture threshold does not change application logger levels.

## PSML documents

The read-only repository uses `bastille.psml.root` (the property name is retained for compatibility; Bastille is not required). See [PSML design](../design/psml.md) for root configuration and bounds.

## Redirects and forwards

The Redirects tab of the Config page (`/bzadmin/config.html?view=redirects`) shows each registration of
Berlioz's `RedirectFilter` (redirects) and `RelocationFilter` (forwards) in `web.xml`, and the rules of
the file named by its `config` parameter, relative to `WEB-INF`. It needs no configuration and only
`VIEW` permission; it reads the files and changes nothing.

The tab shows the files, not the rules Berlioz has loaded: Berlioz loads them on the first request
through the filter and again on **Reload everything**. After editing a file, the tab shows the change at
once, and the filter applies it after a reload. Patterns match the request path including the context
path, and the first rule that matches wins. Query parameter values in targets are redacted.

| Status | Meaning | Fix |
| --- | --- | --- |
| `active` | Berlioz loads these rules. | — |
| `no-config` | The filter has no `config` parameter. In Berlioz 0.14.5, every request through it fails (500), although the log says it has no effect. | Add the parameter, or remove the filter. |
| `missing` | The file does not exist: the filter has no effect. | Check the path, relative to `WEB-INF`. |
| `invalid` | Berlioz loads no rule from the file, which is not well-formed, has another DOCTYPE than Berlioz's, or does not match its schema. The message says why. | Fix the file, then reload. For redirects, `permanent` must be `yes` or `no`, not `true`. |
| `unreadable` | The file is outside `WEB-INF`, is not a file, is larger than 1,000,000 bytes (Berlioz loads no rule from it either), or cannot be read. | Move the file under `WEB-INF`, or split it. |

Warnings:

- **ignored:** a pattern does not parse, and Berlioz ignores the rule.
- **same as #n:** the pattern of an earlier rule is the same, so this rule never applies.
- **forwards to admin:** a forward to an admin URL. Container security constraints do not apply to
  forwards, and `AdminFilter` is mapped to direct requests by default, so the forwarded request is
  denied (403). Link or redirect to the admin URL instead; or, with a host authentication filter, map it
  and `AdminFilter` with `FORWARD` as well, as [mapping the filter](../access.md#1-map-the-filter) explains.
- **reload not covered:** the mapping of the filter does not include the Config page's reload
  operation, so **Reload everything** does not reload its rules. Map the filter to `/*`, or restart the
  application after editing the file.

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
