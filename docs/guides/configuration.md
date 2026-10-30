# Logs and stylesheet customization

## Log files

Set the global property `berlioz.admin.logs` to comma-separated files or directories relative to the application's data folder. When it is absent, logback file appenders supply the locations if logback is available. Only allowed files are listed; reads are bounded and do not follow escaping symbolic links. See [logs design](../design/logs.md).

Recent events require logback. File reading works without a logging backend when locations are configured explicitly. Changing the recent-event capture threshold does not change application logger levels.

## PSML documents

The read-only repository uses `bastille.psml.root` (the property name is retained for compatibility; Bastille is not required). See [PSML design](../design/psml.md) for root configuration and bounds.

## Stylesheet overrides

No host stylesheet is needed: when `WEB-INF/xslt/html/bzadmin.xsl` does not exist, Berlioz uses the
group stylesheet of the JAR (`META-INF/berlioz/xslt/html/bzadmin.xsl`). A host can still override
it with that file, for example to import the overlay's and change some templates:

```xml
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:import href="classpath:META-INF/berlioz/xslt/html/bzadmin.xsl"/>
</xsl:stylesheet>
```

Earlier Berlioz 0.14 versions required this file; hosts upgrading to 0.14.4 can delete it.
