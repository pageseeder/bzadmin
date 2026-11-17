<?xml version="1.0"?>
<!--
  Display the logs of the application, in two views: the recent events captured by the logging
  adapter ('logs-recent', the default), and the log files ('logs-files', view=files), one of which
  is shown in #log-content ('logs-raw', also a page of its own in the Files view).
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!-- The views of the logs: the recent events, or the log files (also for a log file) -->
<xsl:template match="content[@name='logs-recent']|content[@name='logs-raw']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="f:logs-view(.)"/>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="f:to-path('/bzadmin/logs')"/>
    <xsl:with-param name="label"  select="'Recent'"/>
    <xsl:with-param name="active" select="$view = ''"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(f:to-path('/bzadmin/logs'), '?view=files')"/>
    <xsl:with-param name="label"  select="'Files'"/>
    <xsl:with-param name="active" select="$view = 'files'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(f:to-path('/bzadmin/logs'), '?view=config')"/>
    <xsl:with-param name="label"  select="'Configuration'"/>
    <xsl:with-param name="active" select="$view = 'config'"/>
  </xsl:call-template>
</xsl:template>

<!--
  The view of the logs page: 'files' (the log files, and a log file), 'config' (the logging
  configuration), or '' for the recent events
-->
<xsl:function name="f:logs-view" as="xs:string">
  <xsl:param name="content" as="element(content)"/>
  <xsl:variable name="root" select="$content/root()/root"/>
  <xsl:variable name="view" select="string($root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:sequence select="if ($root/@service = 'bzadmin-logs-raw' or $view = 'files') then 'files'
                        else if ($view = 'config') then 'config' else ''"/>
</xsl:function>

<!--
  The logging configuration: the whole Configuration view, or a notice above the other views when
  logging does not write events or loses some
-->
<xsl:template match="content[@name='logs-config']" mode="bzadmin-content">
  <xsl:variable name="view" select="f:logs-view(.)"/>
  <xsl:for-each select="logging-configuration">
    <xsl:choose>
      <xsl:when test="$view = 'config'">
        <xsl:apply-templates select="." mode="bzadmin-logs-config"/>
      </xsl:when>
      <xsl:when test="@verdict = ('problems', 'not-writing')">
        <section>
          <div class="alert callout logging-notice" role="alert">
            <p><svg-icon name="warning"/> <b><xsl:value-of select="f:logging-verdict(@verdict)"/></b>
            <xsl:text> </xsl:text><a href="{f:to-path('/bzadmin/logs')}?view=config">See the logging configuration</a>.</p>
          </div>
        </section>
      </xsl:when>
    </xsl:choose>
  </xsl:for-each>
</xsl:template>

<!-- The sentence that states a verdict of the logging configuration -->
<xsl:function name="f:logging-verdict" as="xs:string">
  <xsl:param name="verdict" as="xs:string"/>
  <xsl:sequence select="if ($verdict = 'writing') then 'Logging writes events.'
                   else if ($verdict = 'problems') then 'Logging has problems: some events may be lost.'
                   else if ($verdict = 'not-writing') then 'Logging writes events nowhere: no appender writes.'
                   else if ($verdict = 'unsupported') then 'The logging configuration can only be inspected with logback.'
                   else 'The logging configuration cannot be inspected with this version of logback: it needs logback 1.5.'"/>
</xsl:function>

<!-- The description of an issue of the logging configuration -->
<xsl:function name="f:logging-issue" as="item()*">
  <xsl:param name="issue" as="element(issue)"/>
  <xsl:variable name="file" select="concat('[', $issue/@location, ']/', $issue/@path)"/>
  <xsl:choose>
    <xsl:when test="$issue/@type = 'stopped-appender'">The appender <code><xsl:value-of select="($issue/@name, $issue/@appender)[1]"/></code> is not started: logback drops its events.</xsl:when>
    <xsl:when test="$issue/@type = 'no-output'">The logger <code><xsl:value-of select="$issue/@logger"/></code> has no output: no appender that writes is attached to it or, while it is additive, to its ancestors.</xsl:when>
    <xsl:when test="$issue/@type = 'error-status'"><xsl:value-of select="$issue/@count"/> error status message<xsl:if test="$issue/@count != 1">s</xsl:if> since the last reset.</xsl:when>
    <xsl:when test="$issue/@type = 'warning-status'"><xsl:value-of select="$issue/@count"/> warning status message<xsl:if test="$issue/@count != 1">s</xsl:if> since the last reset.</xsl:when>
    <xsl:when test="$issue/@type = 'console-only'">Every appender that writes is a console appender: under a container, the console may go nowhere visible.</xsl:when>
    <xsl:when test="$issue/@type = 'statuses-dropped'">logback reported <xsl:value-of select="$issue/@count"/> status messages and keeps 300: some are not shown.</xsl:when>
    <xsl:when test="$issue/@type = 'startup-file-failed'">At startup, <code><xsl:value-of select="$file"/></code> failed to load, and Berlioz used a later file.</xsl:when>
    <xsl:when test="$issue/@type = 'startup-file-with-errors'">At startup, <code><xsl:value-of select="$file"/></code> loaded with <xsl:value-of select="$issue/@count"/> problem<xsl:if test="$issue/@count != 1">s</xsl:if>: see the status messages.</xsl:when>
    <xsl:when test="$issue/@type = 'startup-console-default'">At startup, no logging file could be loaded: Berlioz logs to the console at INFO.</xsl:when>
    <xsl:otherwise><xsl:value-of select="$issue/@type"/></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  The Configuration view: the verdict and its issues, the appenders and loggers, the status messages,
  and what Berlioz did at startup
-->
<xsl:template match="logging-configuration" mode="bzadmin-logs-config">
  <xsl:variable name="config" select="."/>
  <section class="panel logging-verdict">
    <div class="callout{if (@verdict = ('problems', 'not-writing')) then ' alert' else ''}">
      <p><svg-icon name="{if (@verdict = 'writing') then 'ok' else if (@verdict = ('problems', 'not-writing')) then 'warning' else 'info-circle'}"/>
      <xsl:text> </xsl:text><b><xsl:value-of select="f:logging-verdict(@verdict)"/></b>
      <xsl:if test="@version"><xsl:text> </xsl:text><span class="muted"><xsl:value-of select="@framework"/><xsl:text> </xsl:text><xsl:value-of select="@version"/></span></xsl:if></p>
      <xsl:if test="@verdict = 'unsupported'"><p>The logging framework of this application is <code><xsl:value-of select="@framework"/></code>.</p></xsl:if>
      <xsl:if test="issue">
      <ul class="logging-issues">
        <xsl:for-each select="issue">
          <li class="{@severity}"><span class="tag{if (@severity = 'error') then ' warning' else ''}"><xsl:value-of select="@severity"/></span><xsl:text> </xsl:text><xsl:sequence select="f:logging-issue(.)"/></li>
        </xsl:for-each>
      </ul>
      </xsl:if>
    </div>
    <p class="muted">"Writes" means that logback will try to write: it does not say that the output is read. An appender that fails on every write without stopping is reported as writing, with its error status messages.</p>
  </section>

  <xsl:if test="appender">
  <section class="panel">
    <h3>Appenders</h3>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Appenders"><table class="basic-table logging-appenders" aria-label="Appenders">
      <thead><tr><th>Name</th><th>Class</th><th>State</th><th>File</th><th>Loggers</th></tr></thead>
      <tbody>
        <xsl:for-each select="appender">
          <tr data-id="{@id}">
            <td><xsl:value-of select="@name"/><xsl:if test="@holder"><span class="muted"> in <xsl:value-of select="($config/appender[@id = current()/@holder]/@name, current()/@holder)[1]"/></span></xsl:if></td>
            <td title="{@class}"><xsl:sequence select="f:abbr-classname(@class)"/></td>
            <td>
              <xsl:choose>
                <xsl:when test="@capture = 'true'"><span class="tag">recent events</span></xsl:when>
                <xsl:when test="@started = 'false'"><span class="tag warning">not started</span></xsl:when>
                <xsl:when test="@writes = 'true'"><span class="tag success">writes</span></xsl:when>
                <xsl:otherwise><span class="tag">does not write</span></xsl:otherwise>
              </xsl:choose>
            </td>
            <td><xsl:if test="@file"><code><xsl:value-of select="@file"/></code></xsl:if></td>
            <td><xsl:value-of select="tokenize(@loggers, ' ')" separator=", "/></td>
          </tr>
        </xsl:for-each>
      </tbody>
    </table></div>
  </section>
  </xsl:if>

  <xsl:for-each select="loggers[logger]">
  <section class="panel">
    <h3>Loggers</h3>
    <p class="muted">The root and the loggers with a level or appenders of their own<xsl:if test="number(@total) gt count(logger)">: the first <xsl:value-of select="count(logger)"/> of <xsl:value-of select="@total"/></xsl:if>.</p>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Loggers"><table class="basic-table logging-loggers" aria-label="Loggers">
      <thead><tr><th>Name</th><th>Level</th><th>Effective level</th><th>Additive</th><th>Appenders</th><th>Output</th></tr></thead>
      <tbody>
        <xsl:for-each select="logger">
          <tr>
            <td><code><xsl:value-of select="@name"/></code></td>
            <td><xsl:value-of select="@level"/></td>
            <td><xsl:value-of select="@effective-level"/></td>
            <td><xsl:value-of select="if (@additive = 'true') then 'Yes' else 'No'"/></td>
            <td><xsl:value-of select="for $id in tokenize(@appenders, ' ') return string(($config/appender[@id = $id]/@name, $id)[1])" separator=", "/></td>
            <td><xsl:choose>
              <xsl:when test="@output = 'true'"><span class="tag success">yes</span></xsl:when>
              <xsl:when test="@effective-level = 'OFF'"><span class="tag">off</span></xsl:when>
              <xsl:otherwise><span class="tag warning">none</span></xsl:otherwise>
            </xsl:choose></td>
          </tr>
        </xsl:for-each>
      </tbody>
    </table></div>
    <xsl:if test="../turbo-filter">
    <p>Turbo filters, which can accept or deny events whatever the levels: <xsl:for-each select="../turbo-filter"><code title="{@class}"><xsl:sequence select="f:abbr-classname(@class)"/></code><xsl:if test="position() != last()">, </xsl:if></xsl:for-each>.</p>
    </xsl:if>
  </section>
  </xsl:for-each>

  <xsl:for-each select="statuses">
  <section class="panel">
    <h3>Status messages</h3>
    <p class="muted">The warnings and errors that logback reported <xsl:value-of select="if (@since = 'reset') then 'since its last reset' else 'since it started'"/>, most recent first:
      <xsl:value-of select="@errors"/> error<xsl:if test="@errors != 1">s</xsl:if>, <xsl:value-of select="@warnings"/> warning<xsl:if test="@warnings != 1">s</xsl:if>, <xsl:value-of select="@infos"/> information message<xsl:if test="@infos != 1">s</xsl:if>.</p>
    <xsl:if test="status">
    <div class="table-scroll" tabindex="0" role="group" aria-label="Status messages"><table class="basic-table events logging-statuses" aria-label="Status messages">
      <thead><tr><th class="time">Time</th><th class="level">Level</th><th>Origin</th><th>Message</th></tr></thead>
      <tbody>
        <xsl:for-each select="status">
          <tr class="{lower-case(@level)}">
            <td><xsl:sequence select="f:last-modified(@time)"/></td>
            <td><svg-icon name="{if (@level = 'ERROR') then 'error' else 'warning'}" title="{@level}"/></td>
            <td title="{@origin}"><xsl:if test="@origin"><xsl:sequence select="f:abbr-classname(@origin)"/></xsl:if></td>
            <td><xsl:value-of select="@message"/>
              <xsl:for-each select="cause"><br/><span class="muted">Caused by: <xsl:value-of select="@class"/><xsl:if test="@message">: <xsl:value-of select="@message"/></xsl:if></span></xsl:for-each>
            </td>
          </tr>
        </xsl:for-each>
      </tbody>
    </table></div>
    </xsl:if>
  </section>
  </xsl:for-each>

  <xsl:apply-templates select="startup" mode="bzadmin-logs-config"/>

  <xsl:if test="resets or scan">
  <section class="panel">
    <h3>Resets and scan</h3>
    <xsl:for-each select="resets">
    <p><xsl:choose>
      <xsl:when test="@count = 0">No reset of the logger context observed since bzadmin started.</xsl:when>
      <xsl:otherwise><xsl:value-of select="@count"/> reset<xsl:if test="@count != 1">s</xsl:if> of the logger context observed, the last <xsl:sequence select="f:last-modified(@last)"/>. Berlioz resets it once for each logback file it tries; a <code>scan</code> reconfiguration resets it too.</xsl:otherwise>
    </xsl:choose></p>
    </xsl:for-each>
    <xsl:for-each select="scan">
    <p><xsl:choose>
      <xsl:when test="@enabled = 'true'">logback watches <xsl:value-of select="tokenize(@files, ' ')" separator=", "/> for changes (<code>scan</code>).</xsl:when>
      <xsl:otherwise>logback does not watch its configuration for changes.</xsl:otherwise>
    </xsl:choose></p>
    </xsl:for-each>
  </section>
  </xsl:if>
</xsl:template>

<!--
  What Berlioz did at startup, from its snapshot
-->
<xsl:template match="startup" mode="bzadmin-logs-config">
  <section class="panel logging-startup">
    <h3>Startup</h3>
    <xsl:choose>
      <xsl:when test="@initialised = 'false'">
        <p>Berlioz did not configure logging: it was not started by its <code>AppInitializer</code>.</p>
      </xsl:when>
      <xsl:otherwise>
        <p class="muted">What Berlioz did when the application started<xsl:if test="@time">, <xsl:sequence select="f:last-modified(@time)"/></xsl:if><xsl:if test="@mode"> (mode <code><xsl:value-of select="@mode"/></code>)</xsl:if>.</p>
        <xsl:if test="@current = 'false'">
        <div class="warning callout"><p>Logging was reconfigured after startup, so this record may no longer apply.</p></div>
        </xsl:if>
        <p><xsl:choose>
          <xsl:when test="@configuration = 'file'">Berlioz configured <xsl:value-of select="@framework"/> with <code>[<xsl:value-of select="candidate[@in-use = 'true']/@location"/>]/<xsl:value-of select="candidate[@in-use = 'true']/@path"/></code>.</xsl:when>
          <xsl:when test="@configuration = 'console-default'">No logging file could be loaded: Berlioz logs to the console at INFO.</xsl:when>
          <xsl:when test="@configuration = 'native'">Berlioz looked at none of its logging files (<code>berlioz.logging=native</code>): the backend uses its own configuration.</xsl:when>
          <xsl:otherwise>Berlioz found none of its logging files: logback uses its own configuration, perhaps a <code>logback.xml</code> on the classpath.</xsl:otherwise>
        </xsl:choose>
        <xsl:if test="@backend and @backend != 'logback'"> SLF4J uses <code><xsl:value-of select="@backend"/></code><xsl:if test="@logger-factory"> (<code><xsl:value-of select="@logger-factory"/></code>)</xsl:if>.</xsl:if></p>
        <xsl:for-each select="native-setting">
        <p>The system property <code><xsl:value-of select="@name"/></code> is set to <code><xsl:value-of select="@value"/></code>, a hint that Berlioz does not check.</p>
        </xsl:for-each>
        <xsl:if test="candidate">
        <div class="table-scroll" tabindex="0" role="group" aria-label="Logging files considered"><table class="basic-table logging-candidates" aria-label="Logging files considered">
          <thead><tr><th>File</th><th>Framework</th><th>Outcome</th><th>Problems</th></tr></thead>
          <tbody>
            <xsl:for-each select="candidate">
              <tr>
                <td><code>[<xsl:value-of select="@location"/>]/<xsl:value-of select="@path"/></code><xsl:if test="@in-use = 'true'"><xsl:text> </xsl:text><span class="tag success">in use</span></xsl:if></td>
                <td><xsl:value-of select="@framework"/></td>
                <td><span class="tag{if (@outcome = ('failed', 'loaded-with-errors')) then ' warning' else if (@outcome = 'loaded') then ' success' else ''}"><xsl:value-of select="@outcome"/></span>
                  <xsl:if test="@framework = 'log4j' and @in-use = 'true'"><xsl:text> </xsl:text><span class="tag">deprecated</span></xsl:if></td>
                <td>
                  <xsl:if test="@failure"><p><xsl:value-of select="@failure"/></p></xsl:if>
                  <xsl:for-each select="problem"><p><span class="muted"><xsl:value-of select="upper-case(@level)"/></span><xsl:text> </xsl:text><xsl:value-of select="@message"/></p></xsl:for-each>
                  <xsl:if test="@problem-count and not(problem)"><xsl:value-of select="@problem-count"/>: the details are in the startup log.</xsl:if>
                </td>
              </tr>
            </xsl:for-each>
          </tbody>
        </table></div>
        </xsl:if>
      </xsl:otherwise>
    </xsl:choose>
  </section>
</xsl:template>

<!--
  Displays the log files that can be read (Files view), with a form to show one in #log-content
-->
<xsl:template match="content[@name='logs-files']" mode="bzadmin-content">
<xsl:if test="f:logs-view(.) = 'files'">
<section>
  <xsl:apply-templates select="log-files[descendant::log-file]" mode="bzadmin-logs-key-metrics"/>
</section>
<section class="panel">
  <p class="muted">
    <xsl:choose>
      <xsl:when test="log-files/@origin = 'configured'">The directories and files listed in the global property <code>berlioz.admin.logs</code>.</xsl:when>
      <xsl:when test="log-files/@origin = 'detected'">The directories of the file appenders of the logging framework. To list others, set the global property <code>berlioz.admin.logs</code>.</xsl:when>
    </xsl:choose>
  </p>
  <xsl:if test="descendant::log-file">
  <form id="switch-logs" action="{f:to-path('/bzadmin/logs/raw')}">
    <label for="switch-logs-file">Select a log file to view:</label>
    <div class="input-group">
      <select id="switch-logs-file" class="input-group-field" name="id">
        <xsl:apply-templates select="log-files" mode="bzadmin-logs-options"/>
      </select>
      <div class="input-group-button">
        <input type="submit" class="button" value="Show"/>
      </div>
    </div>
  </form>
  </xsl:if>
  <xsl:if test="not(descendant::log-file)">
  <p>
    <xsl:choose>
      <xsl:when test="log-files/@origin = 'configured'">No log files in the directories and files of <code>berlioz.admin.logs</code>.</xsl:when>
      <xsl:otherwise>No log files: list their directories or files in the global property <code>berlioz.admin.logs</code>.</xsl:otherwise>
    </xsl:choose>
  </p>
  </xsl:if>
  <xsl:for-each select="descendant::log-directory[@truncated = 'true']">
  <p>Only the <xsl:value-of select="count(log-file)"/> most recent of the <xsl:value-of select="@total"/> log files of <i><xsl:value-of select="@name"/></i> are listed: open the others by name, <code><xsl:value-of select="f:to-path('/bzadmin/logs/raw')"/>?name=<xsl:value-of select="@name"/>/…</code>.</p>
  </xsl:for-each>
</section>
<xsl:if test="descendant::log-file">
<section id="log-content" class="panel">
  <div class="table-scroll" tabindex="0" role="group" aria-label="Log files"><sortable-table>
  <table class="basic-table log-files-table" aria-label="Log files">
    <thead>
      <tr>
        <th>Name</th>
        <th>Directory</th>
        <th class="size number">Size</th>
        <th class="modified">Last modified</th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="descendant::log-file">
        <xsl:sort select="@datetime" order="descending"/>
        <tr>
          <td><a href="{f:to-path('/bzadmin/logs/raw')}?id={@id}" class="log-file-link" data-id="{@id}"><xsl:value-of select="@name"/></a></td>
          <td title="{parent::log-directory/@path}"><xsl:value-of select="parent::log-directory/@name"/></td>
          <td data-sort="{@size}"><xsl:value-of select="f:to-size(@size)"/></td>
          <td data-sort="{@datetime}"><xsl:sequence select="f:last-modified(@datetime)"/></td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table>
  </sortable-table></div>
</section>
</xsl:if>
</xsl:if>
</xsl:template>

<!--
  The options to select a log file, grouped by the period of the date in their names (see f:log-period):
  undated files first under 'Current', so that the live log is selected by default, then the periods
  and the files in them from newest to oldest. Without dated files, the options are not grouped.
-->
<xsl:template match="log-files" mode="bzadmin-logs-options">
  <xsl:variable name="files" select="descendant::log-file"/>
  <!-- The directory is shown when the files come from more than one place -->
  <xsl:variable name="prefixed" select="count(log-directory) + (if (log-file) then 1 else 0) gt 1"/>
  <xsl:variable name="grouped" select="some $name in $files/@name satisfies f:log-period($name) != ''"/>
  <xsl:for-each-group select="$files" group-by="f:log-period(@name)">
    <xsl:sort select="if (current-grouping-key() = '') then 1 else 0" data-type="number" order="descending"/>
    <xsl:sort select="current-grouping-key()" order="descending"/>
    <xsl:variable name="options" as="element(option)*">
      <xsl:for-each select="current-group()">
        <xsl:sort select="f:log-date(@name)" order="descending"/>
        <xsl:sort select="@name"/>
        <xsl:sort select="string(parent::log-directory/@name)"/>
        <option value="{@id}"><xsl:value-of select="if ($prefixed and parent::log-directory) then concat(parent::log-directory/@name, '/', @name) else @name"/> (<xsl:value-of select="f:to-size(@size)"/>)</option>
      </xsl:for-each>
    </xsl:variable>
    <xsl:choose>
      <xsl:when test="$grouped"><optgroup label="{if (current-grouping-key() = '') then 'Current' else current-grouping-key()}"><xsl:sequence select="$options"/></optgroup></xsl:when>
      <xsl:otherwise><xsl:sequence select="$options"/></xsl:otherwise>
    </xsl:choose>
  </xsl:for-each-group>
</xsl:template>

<!--
  The date in the name of a log file: 'yyyy-mm-dd' for a daily file, 'yyyy-mm' for a monthly file, or
  '' when undated. The parts are separated by '-' or '_', the year is 19xx or 20xx, the month 01-12 and
  the day 01-31, with no digit adjoining the date, so that 'build-202611.log' is not dated. The name is
  used rather than the time last modified, which changes when files are copied or restored.
-->
<xsl:function name="f:log-date" as="xs:string">
  <xsl:param name="name" as="xs:string"/>
  <xsl:variable name="dates" as="xs:string*">
    <xsl:analyze-string select="$name" regex="(^|[^0-9])((19|20)[0-9]{{2}})[-_](0[1-9]|1[0-2])([-_](0[1-9]|[12][0-9]|3[01]))?([^0-9]|$)">
      <xsl:matching-substring>
        <xsl:sequence select="string-join((regex-group(2), regex-group(4), regex-group(6)[. != '']), '-')"/>
      </xsl:matching-substring>
    </xsl:analyze-string>
  </xsl:variable>
  <xsl:sequence select="string($dates[1])"/>
</xsl:function>

<!--
  The period that groups a log file in the selector: the month of a daily file ('yyyy-mm'), the year of
  a monthly file ('yyyy'), otherwise every group would hold one file, or '' when undated.
-->
<xsl:function name="f:log-period" as="xs:string">
  <xsl:param name="name" as="xs:string"/>
  <xsl:variable name="date" select="f:log-date($name)"/>
  <xsl:sequence select="if (string-length($date) = 10) then substring($date, 1, 7)
                        else if (string-length($date) = 7) then substring($date, 1, 4) else ''"/>
</xsl:function>

<!--
  The log files listed, their total size, the largest and the last modified
-->
<xsl:template match="log-files" mode="bzadmin-logs-key-metrics">
  <xsl:variable name="files" select="descendant::log-file"/>
  <xsl:variable name="largest" select="($files[number(@size) = max($files/@size/number())])[1]"/>
  <xsl:variable name="by-date" as="element(log-file)*">
    <xsl:perform-sort select="$files"><xsl:sort select="@datetime" order="descending"/></xsl:perform-sort>
  </xsl:variable>
  <xsl:variable name="latest" select="$by-date[1]"/>
  <xsl:variable name="directories" select="count(log-directory)"/>
  <dl class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Log files'"/>
      <xsl:with-param name="value" select="string(count($files))"/>
      <xsl:with-param name="detail" select="concat(if ($directories gt 0) then concat('In ', $directories, if ($directories = 1) then ' directory' else ' directories') else 'Registered files',
                                                   if (log-directory[@truncated = 'true']) then ', not all listed' else '')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Total size'"/>
      <xsl:with-param name="value" select="f:size(sum($files/@size))"/>
      <xsl:with-param name="detail" select="'Of the files listed'"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Largest'"/>
      <xsl:with-param name="value" select="f:size($largest/@size)"/>
      <xsl:with-param name="detail" select="string($largest/@name)"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Last modified'"/>
      <xsl:with-param name="value" select="normalize-space(string(f:last-modified(xs:dateTime($latest/@datetime))))"/>
      <xsl:with-param name="detail" select="string($latest/@name)"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  The errors and warnings among the events kept, the last error and the events captured; a level
  below the capture threshold is not available, since its events are not captured. Replaced with
  the events when they are refreshed (#recent-metrics).

  @param levels The levels, from the lowest
-->
<xsl:template match="recent-logs" mode="bzadmin-recent-key-metrics">
  <xsl:param name="levels" as="xs:string*"/>
  <xsl:variable name="threshold" select="index-of($levels, string(@threshold))"/>
  <xsl:variable name="errors-captured" select="not($threshold gt index-of($levels, 'ERROR'))"/>
  <xsl:variable name="warnings-captured" select="not($threshold gt index-of($levels, 'WARN'))"/>
  <xsl:variable name="errors" select="event[@level = 'ERROR']"/>
  <xsl:variable name="last-error" select="($errors[number(@timestamp) = max($errors/@timestamp/number())])[1]"/>
  <xsl:variable name="kept" select="concat('In the last ', count(event), ' events')"/>
  <xsl:variable name="not-captured" select="concat('Not captured: the threshold is ', @threshold)"/>
  <dl class="key-metrics" id="recent-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Errors'"/>
      <xsl:with-param name="value" select="if ($errors-captured) then string(count($errors)) else ()"/>
      <xsl:with-param name="detail" select="if ($errors-captured) then $kept else $not-captured"/>
      <xsl:with-param name="warning" select="exists($errors)"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Warnings'"/>
      <xsl:with-param name="value" select="if ($warnings-captured) then string(count(event[@level = 'WARN'])) else ()"/>
      <xsl:with-param name="detail" select="if ($warnings-captured) then $kept else $not-captured"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Last error'"/>
      <xsl:with-param name="value" select="if ($last-error) then normalize-space(string(f:datetime-diff(xs:dateTime($last-error/@datetime))))
                                           else if ($errors-captured) then 'None' else ()"/>
      <xsl:with-param name="detail" select="if ($last-error/@logger) then tokenize($last-error/@logger, '\.')[last()] else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Captured'"/>
      <xsl:with-param name="value" select="format-number(@captured, '#,##0')"/>
      <xsl:with-param name="detail" select="concat('Since the application started; the last ', @capacity, ' are kept')"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  Displays the recent events captured by the logging adapter (logback), or why there are none (Recent view)
-->
<xsl:template match="content[@name='logs-recent']" mode="bzadmin-content">
<xsl:if test="f:logs-view(.) = ''">
<section>
  <xsl:choose>
    <xsl:when test="no-recent-logs">
      <p class="callout">Recent events need logback: the logging framework of this application is <code><xsl:value-of select="no-recent-logs/@backend"/></code><xsl:if test="no-recent-logs/@version"> (version <xsl:value-of select="no-recent-logs/@version"/>)</xsl:if>.
      The log files listed in the global property <code>berlioz.admin.logs</code> can still be read.</p>
    </xsl:when>
    <xsl:when test="recent-logs">
      <xsl:apply-templates select="recent-logs" mode="bzadmin-recent"/>
    </xsl:when>
  </xsl:choose>
</section>
</xsl:if>
</xsl:template>

<!--
  The recent events, the most recent first, their capture threshold and the level of the application
-->
<xsl:template match="recent-logs" mode="bzadmin-recent">
  <xsl:variable name="levels" select="('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF')"/>
  <xsl:apply-templates select="." mode="bzadmin-recent-key-metrics">
    <xsl:with-param name="levels" select="$levels"/>
  </xsl:apply-templates>
  <p class="actions">
    <button type="button" data-href="{f:to-path('/bzadmin/logs/recent')}" id="refresh-events" class="button hollow"><svg-icon name="refresh"/> Refresh</button>
  </p>
  <div class="panel">
  <p class="recent-events-backend callout">
    <svg-icon name="info-circle"/>
    <span>Captured from <b><xsl:value-of select="@backend"/><xsl:if test="@version"><xsl:text> </xsl:text><xsl:value-of select="@version"/></xsl:if></b>
    by an appender that bzadmin attaches to the root logger, and kept in memory: they are lost when the application restarts,
    and events of loggers that are not additive do not reach it.</span>
  </p>
  <div class="row">
    <div class="medium-8 columns">
      <p>The last events that the application logged at or above the capture threshold: at most <xsl:value-of select="@capacity"/>, set by the global property <code>berlioz.admin.logs.recent-capacity</code>.</p>
      <p class="muted" id="recent-threshold-note">The capture threshold does not change what the application logs<xsl:if test="@root-level">: its level (root logger) is <b><xsl:value-of select="@root-level"/></b></xsl:if>.
        <xsl:if test="@root-level and index-of($levels, @threshold) lt index-of($levels, @root-level)">Events below <xsl:value-of select="@root-level"/> are not logged, so not captured.</xsl:if></p>
    </div>
    <div class="medium-4 columns text-right">
      <form id="set-threshold" action="{f:xml-url('/bzadmin/api/logs/recent/threshold')}" method="post">
        <div class="input-group">
          <span class="input-group-label">Capture threshold</span>
          <select class="input-group-field" name="threshold">
            <xsl:variable name="threshold" select="@threshold"/>
            <xsl:variable name="labels" select="('Trace', 'Debug', 'Info (default)', 'Warnings', 'Errors only', 'Off')"/>
            <xsl:for-each select="$levels">
              <xsl:variable name="i" select="position()"/>
              <option value="{.}"><xsl:if test=". = $threshold"><xsl:attribute name="selected">selected</xsl:attribute></xsl:if><xsl:value-of select="$labels[$i]"/></option>
            </xsl:for-each>
          </select>
          <div class="input-group-button">
            <input type="submit" value="Change" class="button alert"/>
          </div>
        </div>
      </form>
    </div>
  </div>
  </div>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Recent log events"><sortable-table>
  <table class="basic-table events is-panel" aria-label="Recent log events">
    <thead>
      <tr>
        <th class="time">Time</th>
        <th>Logger</th>
        <th class="level">Level</th>
        <th>Message</th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="reverse(event)">
        <tr class="{lower-case(@level)}">
          <td data-sort="{@timestamp}"><xsl:sequence select="f:last-modified(@datetime)"/></td>
          <td title="{@logger} ({@thread})"><xsl:sequence select="f:abbr-classname(@logger)"/></td>
          <td data-sort="{index-of($levels, @level)}">
            <svg-icon title="{@level}">
              <xsl:attribute name="name">
              <xsl:choose>
                <xsl:when test="@level = ('TRACE', 'DEBUG')">info</xsl:when>
                <xsl:when test="@level = 'INFO'" >info-circle</xsl:when>
                <xsl:when test="@level = 'WARN'" >warning</xsl:when>
                <xsl:when test="@level = 'ERROR'">error</xsl:when>
              </xsl:choose>
              </xsl:attribute>
            </svg-icon>
          </td>
          <td>
            <xsl:value-of select="@message"/>
            <xsl:if test="throwable">
            <details class="throwable">
              <summary><xsl:value-of select="throwable/@class"/><xsl:if test="throwable/@message">: <xsl:value-of select="throwable/@message"/></xsl:if></summary>
              <pre><xsl:apply-templates select="throwable" mode="bzadmin-recent"/></pre>
            </details>
            </xsl:if>
          </td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table>
  </sortable-table></div>
</xsl:template>

<!--
  A throwable and its causes, as Java prints them
-->
<xsl:template match="throwable|cause" mode="bzadmin-recent">
  <xsl:if test="self::cause">Caused by: <xsl:value-of select="@class"/><xsl:if test="@message">: <xsl:value-of select="@message"/></xsl:if><xsl:text>&#xA;</xsl:text></xsl:if>
  <xsl:for-each select="step">
    <xsl:text>    at </xsl:text><xsl:value-of select="."/><xsl:text>&#xA;</xsl:text>
  </xsl:for-each>
  <xsl:if test="@omitted"><xsl:text>    ... </xsl:text><xsl:value-of select="@omitted"/> more<xsl:text>&#xA;</xsl:text></xsl:if>
  <xsl:apply-templates select="cause" mode="bzadmin-recent"/>
</xsl:template>

<!--
  Displays the last lines of a log file
-->
<xsl:template match="content[@name='logs-raw']" mode="bzadmin-content">
<section id="log-content">
  <h2>Raw log: <xsl:choose>
    <!-- A link by name, to bookmark -->
    <xsl:when test="log"><a href="{f:to-path('/bzadmin/logs/raw')}?name={encode-for-uri(log/@name)}"><i><xsl:value-of select="log/@name"/></i></a></xsl:when>
    <xsl:otherwise><i><xsl:value-of select="no-log/@name"/></i></xsl:otherwise>
  </xsl:choose></h2>
  <xsl:if test="no-log[@reason = 'busy']">
  <div class="alert callout">
    <p><svg-icon name="warning"/> Too many log files are being read: try again in a moment.</p>
  </div>
  </xsl:if>
  <xsl:if test="no-log[not(@reason)]">
  <div class="alert callout">
    <p><svg-icon name="warning"/> No such log file: it may have been deleted or renamed.</p>
  </div>
  </xsl:if>
  <xsl:if test="log">
  <!-- Hidden when the first line is the first of the file: the page shows it when it drops lines -->
  <form id="earlier-log" action="{f:to-path('/bzadmin/logs/raw')}">
    <xsl:if test="not(log/@before)"><xsl:attribute name="hidden">hidden</xsl:attribute></xsl:if>
    <input type="hidden" name="id" value="{log/@id}"/>
    <input type="hidden" name="before" value="{log/@before}"/>
    <p>
      <span>Earlier lines of the log file are not shown<xsl:if test="log/@before and not(log/line[1]/@n)"> (line numbers are not available for large files)</xsl:if>.</span>
      <button type="submit" class="button hollow"><svg-icon name="arrow-up"/> Show earlier lines</button>
    </p>
  </form>
  </xsl:if>
  <xsl:if test="log/@truncated = 'true' and not(log/@before)">
  <div class="alert callout">
    <p><svg-icon name="warning"/> Only the last <xsl:value-of select="count(log/line)"/> lines of this log file are shown.</p>
  </div>
  </xsl:if>
  <!-- One span per line, followed by a line feed, so that a selection copies the text alone: the line numbers are generated content -->
  <pre class="rawlog" data-reset="{log/@reset = 'true'}"><code><xsl:for-each select="descendant::line">
    <span class="{if (@level) then lower-case(@level) else lower-case(preceding-sibling::line[@level][1]/@level)}" data-position="{@position}">
      <xsl:if test="@n"><xsl:attribute name="data-n" select="@n"/></xsl:if>
      <xsl:if test="@incomplete = 'true'"><xsl:attribute name="data-incomplete">true</xsl:attribute></xsl:if>
      <xsl:value-of select="text()"/>
    </span><xsl:text>&#xA;</xsl:text>
  </xsl:for-each></code></pre>
  <!-- Below the lines, where the new lines are added -->
  <xsl:if test="log">
  <form id="follow-log" action="{f:to-path('/bzadmin/logs/raw')}">
    <input type="hidden" name="id" value="{log/@id}"/>
    <input type="hidden" name="cursor" value="{log/@cursor}"/>
    <p><button type="submit" class="button hollow"><svg-icon name="refresh"/> Show new lines</button></p>
  </form>
  </xsl:if>
  </section>
</xsl:template>

</xsl:stylesheet>
