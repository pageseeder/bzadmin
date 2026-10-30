<?xml version="1.0"?>
<!--
  Display the application level information
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!-- The views of the application, selected by the 'view' parameter -->
<xsl:template match="content[@name='summary']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=')"/>
    <xsl:with-param name="label"  select="'Overview'"/>
    <xsl:with-param name="active" select="not($view = ('statistics', 'libraries'))"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=statistics')"/>
    <xsl:with-param name="label"  select="'Statistics'"/>
    <xsl:with-param name="active" select="$view = 'statistics'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=libraries')"/>
    <xsl:with-param name="label"  select="'Libraries'"/>
    <xsl:with-param name="active" select="$view = 'libraries'"/>
  </xsl:call-template>
</xsl:template>

<!--
  Display the view of the application selected by the 'view' parameter: the overview by default,
  the generator statistics or the libraries.
-->
<xsl:template match="content[@name='summary']" mode="bzadmin-content">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <div id="application-details">
    <xsl:choose>
      <xsl:when test="$view = ('statistics', 'libraries')">
        <xsl:apply-templates select="../content[@name = $view]" mode="bzadmin-application"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="bzadmin-application"/>
      </xsl:otherwise>
    </xsl:choose>
  </div>
</xsl:template>

<!-- The statistics and libraries are only shown on their own, or in their view of the application -->
<xsl:template match="content[@name='statistics' or @name='libraries']" mode="bzadmin-content">
  <xsl:if test="not(../content[@name='summary'])">
    <xsl:apply-templates select="." mode="bzadmin-application"/>
  </xsl:if>
</xsl:template>

<!--
  Display the versions of the components, the detected integrations and the reload status
-->
<xsl:template match="content[@name='summary']" mode="bzadmin-application">
<xsl:for-each select="application-summary">
<section id="summary" class="application-overview">
  <h2>Overview</h2>
  <xsl:variable name="summary" select="."/>
  <dl class="key-metrics" id="application-metrics">
    <xsl:for-each select="('mode', 'services', 'configuration', 'berlioz')">
      <xsl:variable name="name" select="."/>
      <xsl:variable name="label" select="if (. = 'mode') then 'Mode' else if (. = 'services') then 'Services' else if (. = 'configuration') then 'Configuration' else 'Berlioz'"/>
      <xsl:variable name="state" select="string($summary/status/@*[name() = $name])"/>
      <xsl:call-template name="bzadmin-key-metric">
        <xsl:with-param name="label" select="$label"/>
        <xsl:with-param name="value" select="if (. = 'mode') then string($summary/@mode)
          else if (. = 'berlioz') then string($summary/component[@name = 'berlioz']/@version)
          else if (. = 'configuration') then (if ($state = 'loaded') then concat($summary/status/@properties, ' properties') else if ($state = 'empty') then 'Empty or failed' else 'No file')
          else if ($state = 'ready') then 'Ready' else if ($state = 'pending') then 'Pending' else if ($state = 'reload-failed') then 'Reload failed' else if ($state = 'startup-failed') then 'Startup failed' else 'Unknown'"/>
        <xsl:with-param name="status-icon" select="if ($state = ('ready', 'loaded')) then 'ok' else if ($state = ('reload-failed', 'startup-failed', 'empty')) then 'warning' else ()"/>
        <xsl:with-param name="zero-muted" select="false()"/>
        <xsl:with-param name="warning" select="$state = ('reload-failed', 'startup-failed', 'empty')"/>
        <xsl:with-param name="info" as="node()*">
          <button type="button" class="info-button link-button" popovertarget="application-info-{$name}" aria-label="About {$label}"><svg-icon name="info-circle"/></button>
        </xsl:with-param>
      </xsl:call-template>
    </xsl:for-each>
  </dl>
  <p id="application-info-mode" class="info-popover" popover="">The Berlioz mode used to select mode-specific configuration. <a href="{f:to-path('/bzadmin/config')}">View configuration</a>.</p>
  <p id="application-info-services" class="info-popover" popover="">
    <xsl:choose>
      <xsl:when test="status/@services = 'ready'">The service registry is loaded.</xsl:when>
      <xsl:when test="status/@services = 'pending'">The service registry is waiting to load.</xsl:when>
      <xsl:when test="status/@services = 'reload-failed'">Berlioz keeps running the previous valid registry. <a href="{f:to-path('/bzadmin/logs')}">Check the logs</a> for the failure.</xsl:when>
      <xsl:when test="status/@services = 'startup-failed'">The service registry could not be loaded. <a href="{f:to-path('/bzadmin/logs')}">Check the logs</a> for the failure.</xsl:when>
      <xsl:otherwise>The service load status is not recognised.</xsl:otherwise>
    </xsl:choose>
    <xsl:text> Reload this page to update. </xsl:text><a href="{f:to-path('/bzadmin/services')}">View services</a>.
  </p>
  <p id="application-info-configuration" class="info-popover" popover="">
    <xsl:choose>
      <xsl:when test="status/@configuration = 'loaded'">The tick means global properties were loaded, not that their values were validated. Berlioz option defaults are listed on the Configuration page.</xsl:when>
      <xsl:when test="status/@configuration = 'empty'">No global properties were loaded. The summary cannot distinguish an empty file from a load failure. <a href="{f:to-path('/bzadmin/logs')}">Check the logs</a>.</xsl:when>
      <xsl:otherwise>No global configuration file was found.</xsl:otherwise>
    </xsl:choose>
    <xsl:text> Reload this page to update. </xsl:text><a href="{f:to-path('/bzadmin/config')}">View configuration</a>.
  </p>
  <p id="application-info-berlioz" class="info-popover" popover="">The version of the Berlioz framework reported by the running application. An unavailable version is not a health warning. <a href="{f:to-path('/bzadmin/application')}?view=libraries">Browse libraries</a>.</p>
  <div class="application-overview-grid">
    <section class="panel" aria-labelledby="application-runtime-title">
      <h3 id="application-runtime-title">Runtime components</h3>
      <p class="muted">Versions reported by the running application. <a href="{f:to-path('/bzadmin/application')}?view=libraries">Browse libraries</a> for the full inventory.</p>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Application versions"><table class="summary-table" aria-label="Application versions">
        <tbody>
          <xsl:for-each select="component">
            <tr data-component="{@name}">
              <th><xsl:value-of select="f:component-label(@name)"/></th>
              <td>
                <xsl:choose>
                  <!-- As the processor identifies itself, e.g. SAXON HE 12.10 -->
                  <xsl:when test="@name = 'xslt'">
                    <xsl:value-of select="if (@product or @version) then string-join((@product, @version), ' ') else 'Unknown'"/>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="if (@version) then @version else if (@name = 'container') then '' else 'Unknown'"/>
                    <xsl:if test="@product"><xsl:if test="@version"><xsl:text> </xsl:text></xsl:if><span class="muted"><xsl:value-of select="@product"/></span></xsl:if>
                  </xsl:otherwise>
                </xsl:choose>
                <xsl:if test="@vendor"> (<xsl:value-of select="@vendor"/>)</xsl:if>
                <xsl:if test="@xslt-version"><xsl:text> </xsl:text><span class="tag">XSLT <xsl:value-of select="@xslt-version"/></span></xsl:if>
                <xsl:if test="@servlet-api"><xsl:text> </xsl:text><span class="tag">Servlet <xsl:value-of select="@servlet-api"/></span></xsl:if>
                <xsl:if test="@factory"><details class="component-details"><summary>Technical details</summary><code><xsl:value-of select="@factory"/></code></details></xsl:if>
              </td>
            </tr>
          </xsl:for-each>
          <xsl:if test="not(component[@name = 'container'])">
            <tr data-component="container"><th>Servlet container</th><td>Unknown: the container does not identify itself to Berlioz</td></tr>
          </xsl:if>
          <xsl:if test="not(component[@name = 'xslt'])">
            <tr data-component="xslt"><th>XSLT processor</th><td>None: Berlioz cannot create its transformer factory</td></tr>
          </xsl:if>
        </tbody>
      </table></div>

    </section>
    <section class="panel" aria-labelledby="application-integrations-title">
      <h3 id="application-integrations-title">Integrations</h3>
      <p class="muted">Capabilities detected in this host application.</p>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Application integrations"><table class="summary-table application-integrations-table" aria-label="Application integrations"><tbody>
        <xsl:for-each select="logging">
          <tr><th scope="row"><a href="{f:to-path('/bzadmin/logs')}">Logs</a></th><td>
            <strong><xsl:value-of select="@backend"/></strong>
            <p><xsl:value-of select="if (@recent-events = 'true') then 'Recent event capture is available.' else 'This backend does not provide recent event capture. Log files may still be available.'"/></p>
          </td></tr>
        </xsl:for-each>
        <tr><th scope="row"><a href="{f:to-path('/bzadmin/cache')}">Caches</a></th><td>
          <xsl:choose>
            <xsl:when test="cache-provider"><ul class="application-providers"><xsl:for-each select="cache-provider"><li>
              <strong><xsl:value-of select="@label"/></strong>
              <xsl:choose>
                <xsl:when test="@failed"><p><span class="tag warning">Manager discovery failed</span> Open Caches for details.</p></xsl:when>
                <xsl:otherwise><p><xsl:value-of select="@managers"/> manager<xsl:if test="@managers != 1">s</xsl:if> detected.</p></xsl:otherwise>
              </xsl:choose>
            </li></xsl:for-each></ul></xsl:when>
            <xsl:otherwise>No cache provider detected.</xsl:otherwise>
          </xsl:choose>
        </td></tr>
      <tr><th scope="row">XSLT</th><td><a href="{f:to-path('/bzadmin/xslt')}">Cache settings and controls</a></td></tr>
      <xsl:for-each select="access">
        <tr><th scope="row">Access</th><td><details class="application-access"><summary>Access configuration</summary>
          <p>Host access attribute<xsl:if test="@roles = 'true'">, container roles</xsl:if><xsl:if test="@fallback-login = 'true'">, fallback login</xsl:if>.</p>
          <p>Configured mechanisms do not identify which one authorised this request. The host can supply an access attribute.</p>
          <a href="{f:to-path('/bzadmin/config')}">View configuration</a>
        </details></td></tr>
      </xsl:for-each>
      </tbody></table></div>
    </section>
  </div>
</section>
</xsl:for-each>
</xsl:template>

<!--
  Returns the label of a component of the application summary
-->
<xsl:function name="f:component-label" as="xs:string">
  <xsl:param name="name" as="xs:string"/>
  <xsl:sequence select="if ($name = 'bzadmin') then 'Berlioz administration'
                   else if ($name = 'berlioz') then 'Berlioz'
                   else if ($name = 'java') then 'Java'
                   else if ($name = 'xslt') then 'XSLT processor'
                   else if ($name = 'container') then 'Servlet container'
                   else $name"/>
</xsl:function>

<!--
  Returns the class that mutes a zero value of the statistics table, preceded by a space
-->
<xsl:function name="f:muted-zero" as="xs:string">
  <xsl:param name="value" as="attribute()?"/>
  <xsl:sequence select="if (number($value) = 0) then ' muted' else ''"/>
</xsl:function>

<!--
  Display the generator statistics
-->
<xsl:template match="content[@name='statistics']" mode="bzadmin-application">
<section>
  <h2>Generator statistics</h2>
  <xsl:apply-templates select="statistics" mode="bzadmin-application-key-metrics"/>
  <p class="actions">
    <button type="button" data-href="{f:to-path('/bzadmin/application/statistics')}" id="refresh-statistics" class="button hollow"><svg-icon name="refresh"/> Refresh</button>
    <button type="button" data-href="{f:to-path('/bzadmin/application/statistics/reset')}" id="reset-statistics" class="button alert"><svg-icon name="trash"/> Reset statistics</button>
  </p>
  <xsl:for-each select="statistics">
    <div class="table-scroll" tabindex="0" role="group" aria-label="Generator statistics"><sortable-table>
    <table class="basic-table statistics-table is-panel" aria-label="Generator statistics">
      <thead>
        <tr>
          <th></th>
          <th colspan="3">Etag (&#xb5;s)</th>
          <th colspan="3">Process (&#xb5;s)</th>
          <th colspan="7">Status</th>
        </tr>
        <tr>
          <th>Generator</th>
          <th>Min</th>
          <th>Max</th>
          <th>Avg</th>
          <th>Min</th>
          <th>Max</th>
          <th>Avg</th>
          <th class="status">OK</th>
          <th class="status"><abbr title="Temporary Redirect">TR</abbr></th>
          <th class="status"><abbr title="Not Found">NF</abbr></th>
          <th class="status"><abbr title="Bad Request">BR</abbr></th>
          <th class="status"><abbr title="Internal Server Error">ISE</abbr></th>
          <th class="status"><abbr title="Bad Gateway">BG</abbr></th>
          <th class="status"><abbr title="Service Unavailable">SU</abbr></th>
        </tr>
      </thead>
      <tfoot>
        <tr>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td></td>
          <td class="status{if (descendant::status/@ok) then '' else ' muted'}"><xsl:value-of select="sum(descendant::status/@ok)"/></td>
          <td class="status{if (descendant::status/@temporary_redirect) then '' else ' muted'}"><xsl:value-of select="sum(descendant::status/@temporary_redirect)"/></td>
          <td class="status{if (descendant::status/@not_found) then '' else ' muted'}"><xsl:value-of select="sum(descendant::status/@not_found)"/></td>
          <td class="status{if (descendant::status/@bad_request) then '' else ' muted'}"><xsl:value-of select="sum(descendant::status/@bad_request)"/></td>
          <td class="status{if (descendant::status/@internal_server_error) then ' server_error' else ' muted'}"><xsl:value-of select="sum(descendant::status/@internal_server_error)"/></td>
          <td class="status{if (descendant::status/@bad_gateway) then ' server_error' else ' muted'}"><xsl:value-of select="sum(descendant::status/@bad_gateway)"/></td>
          <td class="status{if (descendant::status/@service_unavailable) then ' server_error' else ' muted'}"><xsl:value-of select="sum(descendant::status/@service_unavailable)"/></td>
        </tr>
      </tfoot>
      <tbody>
        <xsl:for-each select="*">
          <xsl:sort select="f:generator-class(@generator)"/>
        <tr>
          <xsl:if test="@avg-etag > 10000 or @avg-process > 100000 or status/@internal_server_error">
            <xsl:attribute name="class">warn</xsl:attribute>
          </xsl:if>
          <xsl:variable name="etag-trend"    select="if (@avg-last-etag    &gt; @avg-etag*1.2)    then 'up' else if (@avg-last-etag    &lt; @avg-etag*0.8)    then 'down' else ''"/>
          <xsl:variable name="process-trend" select="if (@avg-last-process &gt; @avg-process*1.2) then 'up' else if (@avg-last-process &lt; @avg-process*0.8) then 'down' else ''" />
          <td data-sort="{f:generator-class(@generator)}"><xsl:sequence select="f:generator-name(@generator, concat('generator-statistics-', generate-id(.)))"/></td>
          <td><xsl:if test="number(@min-etag) = 0"><xsl:attribute name="class">muted</xsl:attribute></xsl:if><xsl:value-of select="format-number(@min-etag, '#,###,###')"/></td>
          <td><xsl:if test="number(@max-etag) = 0"><xsl:attribute name="class">muted</xsl:attribute></xsl:if><xsl:value-of select="format-number(@max-etag, '#,###,###')"/></td>
          <td class="avg{if (@avg-etag > 10000) then ' mark' else ''}{f:muted-zero(@avg-etag)} {$etag-trend}">
            <span title="Average over the last 10: {if (@avg-last-etag > 0) then format-number(@avg-last-etag, '#,###,###') else '&#x2013;'}"><xsl:value-of select="format-number(@avg-etag, '#,###,###')"/><svg-icon name="arrow-{$etag-trend}"/></span>
          </td>
          <td><xsl:if test="number(@min-process) = 0"><xsl:attribute name="class">muted</xsl:attribute></xsl:if><xsl:value-of select="format-number(@min-process, '#,###,##0')"/></td>
          <td><xsl:if test="number(@max-process) = 0"><xsl:attribute name="class">muted</xsl:attribute></xsl:if><xsl:value-of select="format-number(@max-process, '#,###,##0')"/></td>
          <td class="avg{if (@avg-process > 100000) then ' mark' else ''}{f:muted-zero(@avg-process)} {$process-trend}">
            <span title="Average over the last 10: {if (@avg-last-process > 0) then format-number(@avg-last-process, '#,###,###') else '&#x2013;'}"><xsl:value-of select="format-number(@avg-process, '#,###,##0')"/><svg-icon name="arrow-{$process-trend}"/></span>
          </td>
          <td class="status"><xsl:value-of select="status/@ok"/></td>
          <td class="status"><xsl:value-of select="status/@temporary_redirect"/></td>
          <td class="status"><xsl:value-of select="status/@not_found"/></td>
          <td class="status"><xsl:value-of select="status/@bad_request"/></td>
          <td class="status{if (status/@internal_server_error) then ' mark' else''}"><xsl:value-of select="status/@internal_server_error"/></td>
          <td class="status{if (status/@bad_gateway) then ' mark' else''}"><xsl:value-of select="status/@bad_gateway"/></td>
          <td class="status{if (status/@service_unavailable) then ' mark' else''}"><xsl:value-of select="status/@service_unavailable"/></td>
        </tr>
        </xsl:for-each>
      </tbody>
    </table>
    </sortable-table></div>
  </xsl:for-each>
</section>
</xsl:template>

<!--
  The generator calls, the server errors, the slowest generator and the generators slower than
  100 ms on average (as marked in the table); times are in microseconds. Replaced with the table
  when the statistics are refreshed (#statistics-metrics).
-->
<xsl:template match="statistics" mode="bzadmin-application-key-metrics">
  <xsl:variable name="status" select="statistic/status"/>
  <xsl:variable name="errors" select="sum($status/(@internal_server_error|@bad_gateway|@service_unavailable))"/>
  <xsl:variable name="slowest" select="(statistic[number(@avg-process) = max(../statistic/@avg-process/number())])[1]"/>
  <xsl:variable name="slow" select="statistic[number(@avg-process) gt 100000]"/>
  <dl class="key-metrics" id="statistics-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Generator calls'"/>
      <xsl:with-param name="value" select="format-number(sum(statistic/@count), '#,##0')"/>
      <xsl:with-param name="detail" select="concat('by ', count(statistic), ' generators')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Server errors'"/>
      <xsl:with-param name="value" select="format-number($errors, '#,##0')"/>
      <xsl:with-param name="detail" select="concat(format-number(sum($status/@bad_request), '#,##0'), ' bad requests, ', format-number(sum($status/@not_found), '#,##0'), ' not found')"/>
      <xsl:with-param name="warning" select="$errors gt 0"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Slowest generator'"/>
      <xsl:with-param name="value" select="if ($slowest) then concat(format-number(number($slowest/@avg-process) div 1000, '#,##0.0'), ' ms') else ()"/>
      <xsl:with-param name="zero-muted" select="false()"/>
      <xsl:with-param name="detail" select="if ($slowest) then concat('On average: ', f:generator-label($slowest/@generator)) else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Slow generators'"/>
      <xsl:with-param name="value" select="string(count($slow))"/>
      <xsl:with-param name="detail" select="'Over 100 ms on average'"/>
      <xsl:with-param name="warning" select="exists($slow)"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  Display the libraries in a table with their key properties, and the whole manifest of each one
  in a dialog
-->
<xsl:template match="content[@name='libraries']" mode="bzadmin-application">
<section>
  <h2>Libraries <span class="count muted" title="{count(libraries/library)} libraries">(<xsl:value-of select="count(libraries/library)"/>)</span></h2>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Libraries"><sortable-table>
  <table class="basic-table libraries-table is-panel" aria-label="Libraries">
    <thead>
      <tr>
        <th>Library</th>
        <th>Version</th>
        <th>Vendor</th>
        <th title="From the Bundle-License header of the manifest">Licence</th>
        <th data-sort="none">Manifest</th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="libraries/library">
        <xsl:sort select="lower-case(@file)"/>
        <tr>
          <td data-sort="{f:get-library-name(.)}">
            <xsl:value-of select="f:get-library-name(.)"/>
            <br/><small class="library-file"><xsl:value-of select="@file"/></small>
          </td>
          <td><xsl:sequence select="f:get-library-version(.)"/></td>
          <td><xsl:sequence select="f:get-library-vendor(.)"/></td>
          <td><xsl:sequence select="f:get-library-licence(.)"/></td>
          <td><button type="button" class="button hollow" commandfor="{f:library-dialog-id(.)}" command="show-modal" aria-label="Show manifest of {f:get-library-name(.)}"><svg-icon name="eye"/> Show</button></td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table>
  </sortable-table></div>
  <xsl:apply-templates select="libraries/library" mode="bzadmin-library-dialog"/>
</section>
</xsl:template>

<!--
  The manifest of a library displayed in a dialog
-->
<xsl:template match="library" mode="bzadmin-library-dialog">
<dialog id="{f:library-dialog-id(.)}" class="dialog library-dialog" aria-labelledby="{f:library-dialog-id(.)}-title">
  <h2 id="{f:library-dialog-id(.)}-title" class="dialog-title"><xsl:value-of select="f:get-library-name(.)"/><xsl:text> </xsl:text><xsl:sequence select="f:get-library-version(.)"/></h2>
  <p><code><xsl:value-of select="@file"/></code></p>
  <xsl:choose>
    <xsl:when test="attribute">
      <div class="table-scroll" tabindex="0" role="group" aria-label="Manifest of {f:get-library-name(.)}"><table class="basic-table properties-table" aria-label="Manifest of {f:get-library-name(.)}">
        <thead>
          <tr>
            <th>Property</th>
            <th>Value</th>
          </tr>
        </thead>
        <tbody>
          <xsl:for-each select="attribute">
            <xsl:sort select="@name"/>
            <tr>
              <td><xsl:value-of select="@name"/></td>
              <td><xsl:sequence select="f:display-manifest-value(@value)"/></td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table></div>
    </xsl:when>
    <xsl:otherwise><p>No manifest, or a manifest without main attributes.</p></xsl:otherwise>
  </xsl:choose>
  <form method="dialog">
    <button class="button">OK</button>
  </form>
</dialog>
</xsl:template>

<!--
  @param lib A library element
  @return a dialog id unique in the page
-->
<xsl:function name="f:library-dialog-id" as="xs:string">
  <xsl:param name="lib" as="element(library)"/>
  <xsl:sequence select="concat('library-', generate-id($lib))"/>
</xsl:function>

<!--
  Returns the first value of the specified manifest headers of a library (names in lower case,
  as ListLibraries writes them)
  @param lib   the library object
  @param names the names of the manifest headers, in order of preference
  @return the value, or the empty sequence
-->
<xsl:function name="f:manifest-value" as="xs:string?">
  <xsl:param name="lib"   as="element(library)"/>
  <xsl:param name="names" as="xs:string*"/>
  <xsl:sequence select="(for $name in $names return $lib/attribute[@name = $name][string(@value)]/string(@value))[1]"/>
</xsl:function>

<!--
  Returns the name of the library from the filename or information in the manifest
  @param the library object
  @return the name or a double dash
-->
<xsl:function name="f:get-library-name">
  <xsl:param name="lib" as="element(library)"/>
  <xsl:value-of select="(f:manifest-value($lib, ('bundle-name', 'implementation-title', 'specification-title')), $lib/@name)[1]"/>
</xsl:function>

<!--
  Returns the version of the library from the filename or information in the manifest
  @param the library object
  @return the version or an en dash
-->
<xsl:function name="f:get-library-version">
  <xsl:param name="lib" as="element(library)"/>
  <xsl:variable name="version" select="(f:manifest-value($lib, ('bundle-version', 'implementation-version', 'specification-version')), $lib/@version, '&#x2013;')[1]"/>
  <xsl:choose>
    <xsl:when test="contains($version, ' ')"><abbr title="{$version}"><xsl:value-of select="substring-before($version, ' ')"/></abbr></xsl:when>
    <xsl:otherwise><xsl:value-of select="$version"/></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Returns the vendor of the library from its manifest; when the manifest has no vendor, the
  namespace of its module name (muted, with an explanation in its title).
  @param the library object
  @return the vendor, the namespace or an en dash
-->
<xsl:function name="f:get-library-vendor" as="item()*">
  <xsl:param name="lib" as="element(library)"/>
  <xsl:variable name="vendor" select="f:manifest-value($lib, ('bundle-vendor', 'implementation-vendor', 'specification-vendor'))"/>
  <xsl:variable name="namespace" select="f:module-namespace(f:manifest-value($lib, ('automatic-module-name', 'bundle-symbolicname')))"/>
  <xsl:choose>
    <xsl:when test="$vendor"><xsl:value-of select="$vendor"/></xsl:when>
    <xsl:when test="$namespace"><span class="muted" title="No vendor in the manifest: namespace of the module name"><xsl:value-of select="$namespace"/></span></xsl:when>
    <xsl:otherwise>&#x2013;</xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Returns the namespace of a module or bundle name as a domain, e.g. 'checkerframework.org' for
  'org.checkerframework.checker.qual', or 'github.com/benmanes' for 'com.github.benmanes.caffeine'
  @param name the module name, in reverse domain notation
  @return the namespace, or the empty sequence for a name that is not in reverse domain notation
-->
<xsl:function name="f:module-namespace" as="xs:string?">
  <xsl:param name="name" as="xs:string?"/>
  <xsl:variable name="parts" select="tokenize($name, '\.')"/>
  <xsl:sequence select="
         if (count($parts) lt 3 or not($parts[1] = ('com', 'org', 'net', 'io', 'dev', 'edu', 'gov'))) then ()
    else if ($parts[2] = ('github', 'gitlab', 'bitbucket')) then concat($parts[2], '.', $parts[1], '/', $parts[3])
    else concat($parts[2], '.', $parts[1])"/>
</xsl:function>

<!--
  Returns the licence of the library from the Bundle-License header of its manifest: a short name
  for the common licences, the host of a link otherwise, with the whole header in its title.
  @param the library object
  @return the licence or an en dash
-->
<xsl:function name="f:get-library-licence" as="item()*">
  <xsl:param name="lib" as="element(library)"/>
  <xsl:variable name="value" select="f:manifest-value($lib, 'bundle-license')"/>
  <xsl:variable name="v" select="lower-case($value)"/>
  <xsl:variable name="label" select="
         if (not($value)) then ()
    else if (matches($v, 'cddl') and matches($v, 'gpl')) then 'CDDL + GPL'
    else if (matches($v, 'cddl')) then 'CDDL'
    else if (matches($v, 'apache')) then 'Apache-2.0'
    else if (matches($v, '(^|[^a-z])mit([^a-z]|$)')) then 'MIT'
    else if (matches($v, 'epl-?2|eclipse public license,? (v|version )?2')) then 'EPL-2.0'
    else if (matches($v, 'epl|eclipse public license')) then 'EPL-1.0'
    else if (matches($v, 'lgpl')) then 'LGPL'
    else if (matches($v, 'mpl|mozilla')) then 'MPL-2.0'
    else if (matches($v, 'bsd')) then 'BSD'
    else ()"/>
  <xsl:variable name="link" select="(tokenize($value, '[\s,;=&quot;]+')[matches(., '^https?://')])[1]"/>
  <xsl:choose>
    <xsl:when test="not($value)">&#x2013;</xsl:when>
    <xsl:when test="$label"><abbr title="{$value}"><xsl:value-of select="$label"/></abbr></xsl:when>
    <xsl:when test="$link"><a href="{$link}" target="_new" title="{$value}"><xsl:value-of select="replace($link, '^https?://([^/]+).*$', '$1')"/></a></xsl:when>
    <xsl:when test="string-length($value) gt 30"><abbr title="{$value}"><xsl:value-of select="substring($value, 1, 30)"/>&#x2026;</abbr></xsl:when>
    <xsl:otherwise><xsl:value-of select="$value"/></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Display the value of a manifest property handling long values, splitting at commas and semi-colons and
  recognising links
  @param value
  @return processed value
-->
<xsl:function name="f:display-manifest-value">
  <xsl:param name="value"/>
  <xsl:analyze-string select="$value" regex=",|;|\s">
    <xsl:matching-substring>
      <xsl:value-of select="."/><xsl:text> </xsl:text>
    </xsl:matching-substring>
    <xsl:non-matching-substring>
      <xsl:choose>
        <xsl:when test="starts-with(., 'http://') or starts-with(., 'https://')">
          <a href="{.}" target="_new"><xsl:value-of select="."/></a>
        </xsl:when>
        <xsl:when test="string-length(.) gt 80"><xsl:value-of select="substring(.,1,80)"/>&#x2026;</xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="."/>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:function>

</xsl:stylesheet>
