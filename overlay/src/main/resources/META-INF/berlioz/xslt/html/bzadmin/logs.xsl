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
</xsl:template>

<!-- The view of the logs page: 'files' (the log files, and a log file), or '' for the recent events -->
<xsl:function name="f:logs-view" as="xs:string">
  <xsl:param name="content" as="element(content)"/>
  <xsl:variable name="root" select="$content/root()/root"/>
  <xsl:sequence select="if ($root/@service = 'bzadmin-logs-raw'
                            or $root/header/http-parameters/parameter[@name = 'view'][1] = 'files') then 'files' else ''"/>
</xsl:function>

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
        <xsl:for-each select="descendant::log-file">
          <xsl:sort select="@name"/>
          <option value="{@id}"><xsl:value-of select="@name"/> (<xsl:value-of select="f:to-size(@size)"/>)</option>
        </xsl:for-each>
      </select>
      <div class="input-group-button">
        <input type="submit" class="button" value="show"/>
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
        <th class="size">Size</th>
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
  below the capture threshold is not available, since its events are not captured.

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
  <dl class="key-metrics">
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
  The recent events, their capture threshold and the level of the application
-->
<xsl:template match="recent-logs" mode="bzadmin-recent">
  <xsl:variable name="levels" select="('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF')"/>
  <xsl:apply-templates select="." mode="bzadmin-recent-key-metrics">
    <xsl:with-param name="levels" select="$levels"/>
  </xsl:apply-templates>
  <div class="panel">
  <p class="recent-events-backend callout">
    <svg-icon name="info-circle"/>
    <span>Captured from <b><xsl:value-of select="@backend"/><xsl:if test="@version"><xsl:text> </xsl:text><xsl:value-of select="@version"/></xsl:if></b>
    by an appender that bzadmin attaches to the root logger, and kept in memory: they are lost when the application restarts,
    and events of loggers that are not additive do not reach it.</span>
  </p>
  <div class="row">
    <div class="medium-8 columns">
      <p>The last events that the application logged at or above the capture threshold.</p>
      <p class="muted">The capture threshold does not change what the application logs<xsl:if test="@root-level">: its level (root logger) is <b><xsl:value-of select="@root-level"/></b></xsl:if>.
        <xsl:if test="@root-level and index-of($levels, @threshold) lt index-of($levels, @root-level)">Events below <xsl:value-of select="@root-level"/> are not logged, so not captured.</xsl:if></p>
    </div>
    <div class="medium-4 columns text-right">
      <form id="set-threshold" action="{concat($bzheader/path/@context, $bzheader/path/@prefix, '/bzadmin/logs/recent/threshold.xml')}" method="post">
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
            <input type="submit" value="change" class="button alert"/>
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
      <xsl:for-each select="event">
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
