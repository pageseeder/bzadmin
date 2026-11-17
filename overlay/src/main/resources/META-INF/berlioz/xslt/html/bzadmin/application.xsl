<?xml version="1.0"?>
<!--
  Display the application level information
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!--
  Returns the view of the application selected by the 'view' parameter: 'overview' (the default),
  'performance' and 'status-codes' (the generator statistics; 'statistics' for 'performance', the
  name of the view before it was split), or 'libraries'
-->
<xsl:function name="f:application-view" as="xs:string">
  <xsl:param name="root" as="node()"/>
  <xsl:variable name="view" select="string($root/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:sequence select="if ($view = ('performance', 'statistics')) then 'performance'
                        else if ($view = ('status-codes', 'libraries')) then $view
                        else 'overview'"/>
</xsl:function>

<!-- The views of the application, selected by the 'view' parameter -->
<xsl:template match="content[@name='summary']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="f:application-view(root(.))"/>
  <xsl:variable name="path" select="/root/header/location/@path"/>
  <xsl:for-each select="('overview', 'performance', 'status-codes', 'libraries')">
    <xsl:call-template name="bzadmin-secondary-link">
      <xsl:with-param name="href"   select="concat($path, '?view=', if (. = 'overview') then '' else .)"/>
      <xsl:with-param name="label"  select="if (. = 'status-codes') then 'Status codes' else concat(upper-case(substring(., 1, 1)), substring(., 2))"/>
      <xsl:with-param name="active" select=". = $view"/>
    </xsl:call-template>
  </xsl:for-each>
</xsl:template>

<!--
  Display the view of the application selected by the 'view' parameter: the overview by default,
  the generator statistics (performance or status codes) or the libraries.
-->
<xsl:template match="content[@name='summary']" mode="bzadmin-content">
  <xsl:variable name="view" select="f:application-view(root(.))"/>
  <div id="application-details">
    <xsl:choose>
      <xsl:when test="$view = ('performance', 'status-codes')">
        <xsl:apply-templates select="../content[@name = 'statistics']" mode="bzadmin-application"/>
      </xsl:when>
      <xsl:when test="$view = 'libraries'">
        <xsl:apply-templates select="../content[@name = 'libraries']" mode="bzadmin-application"/>
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
          else if (. = 'configuration' and $state = 'ready') then concat($summary/status/@properties, ' properties')
          else if ($state = 'none') then 'No file'
          else if ($state = 'ready') then 'Ready' else if ($state = 'pending') then 'Pending' else if ($state = 'reload-failed') then 'Reload failed' else if ($state = 'startup-failed') then 'Startup failed' else 'Unknown'"/>
        <xsl:with-param name="status-icon" select="if ($state = 'ready') then 'ok' else if ($state = ('reload-failed', 'startup-failed')) then 'warning' else ()"/>
        <xsl:with-param name="zero-muted" select="false()"/>
        <xsl:with-param name="warning" select="$state = ('reload-failed', 'startup-failed')"/>
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
      <xsl:when test="status/@configuration = 'ready'">The tick means global properties were loaded, not that their values were validated. Berlioz option defaults are listed on the Configuration page.</xsl:when>
      <xsl:when test="status/@configuration = 'pending'">The global configuration is waiting to load.</xsl:when>
      <xsl:when test="status/@configuration = 'reload-failed'">The last reload failed: Berlioz keeps the last valid configuration. The Configuration page shows the file that failed.</xsl:when>
      <xsl:when test="status/@configuration = 'startup-failed'">The global configuration could not be loaded: Berlioz uses the default value of every option. The Configuration page shows the file that failed.</xsl:when>
      <xsl:when test="status/@configuration = 'none'">No global configuration file was found.</xsl:when>
      <xsl:otherwise>The configuration load status is not recognised.</xsl:otherwise>
    </xsl:choose>
    <xsl:text> Reload this page to update. </xsl:text><a href="{f:to-path('/bzadmin/config')}">View configuration</a>.
  </p>
  <p id="application-info-berlioz" class="info-popover" popover="">The version of the Berlioz framework reported by the running application. An unavailable version is not a health warning. <a href="{f:to-path('/bzadmin/application')}?view=libraries">Browse libraries</a>.</p>
  <div class="application-overview-grid">
    <section class="panel" aria-labelledby="application-runtime-title">
      <h3 id="application-runtime-title">Runtime components</h3>
      <p class="muted">Versions reported by the running application.</p>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Application versions"><table class="summary-table" aria-label="Application versions">
        <tbody>
          <xsl:for-each select="component">
            <tr data-component="{@name}">
              <th><xsl:value-of select="f:component-label(@name)"/></th>
              <td>
                <xsl:choose>
                  <!-- As the processor identifies itself, e.g. SAXON HE 12.10 -->
                  <xsl:when test="@name = 'xslt'">
                    <xsl:value-of select="if (@product or @version) then string-join((@product, @version), ' ') else ()"/>
                    <xsl:if test="not(@product or @version)"><xsl:sequence select="f:not-available()"/></xsl:if>
                  </xsl:when>
                  <xsl:otherwise>
                    <xsl:value-of select="@version"/>
                    <xsl:if test="not(@product or @version)"><xsl:sequence select="f:not-available()"/></xsl:if>
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
          <tr><th scope="row">Logs</th><td>
            <strong><xsl:value-of select="@backend"/></strong>
            <p><xsl:value-of select="if (@recent-events = 'true') then 'Recent event capture is available.' else 'This backend does not provide recent event capture. Log files may still be available.'"/></p>
            <xsl:choose>
              <xsl:when test="@configuration = 'writing'"><p class="logging-configuration"><svg-icon name="ok"/> Logging writes events.</p></xsl:when>
              <xsl:when test="@configuration = ('problems', 'not-writing')">
                <p class="logging-configuration"><span class="tag warning"><xsl:value-of select="if (@configuration = 'problems') then 'Logging problems' else 'Logging writes nowhere'"/></span>
                <xsl:text> </xsl:text><a href="{f:to-path('/bzadmin/logs')}?view=config">See the logging configuration</a>.</p>
              </xsl:when>
            </xsl:choose>
          </td></tr>
        </xsl:for-each>
        <tr><th scope="row">Caches</th><td>
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
  The statuses of the generator statistics, in the order of their code: Berlioz writes the count of
  each status a generator returned as an attribute of `status`, named after its `ContentStatus`
  in lower case (`not_found`), without its code. Mirrors `ContentStatus` (DiagnosticsXsltTest checks it).
  The status codes view always shows the likely codes (`@always`), even when no generator returned them.
-->
<xsl:variable name="bzadmin-statuses" as="element(status)+">
  <status name="ok"                              code="200" reason="OK" always="true"/>
  <status name="created"                         code="201" reason="Created"/>
  <status name="accepted"                        code="202" reason="Accepted"/>
  <status name="non_authoritative_information"   code="203" reason="Non-Authoritative Information"/>
  <status name="no_content"                      code="204" reason="No Content"/>
  <status name="reset_content"                   code="205" reason="Reset Content"/>
  <status name="multiple_choice"                 code="300" reason="Multiple Choices"/>
  <status name="moved_permanently"               code="301" reason="Moved Permanently" always="true"/>
  <status name="found"                           code="302" reason="Found" always="true"/>
  <status name="see_other"                       code="303" reason="See Other"/>
  <status name="temporary_redirect"              code="307" reason="Temporary Redirect"/>
  <status name="permanent_redirect"              code="308" reason="Permanent Redirect"/>
  <status name="bad_request"                     code="400" reason="Bad Request" always="true"/>
  <status name="unauthorized"                    code="401" reason="Unauthorized"/>
  <status name="forbidden"                       code="403" reason="Forbidden"/>
  <status name="not_found"                       code="404" reason="Not Found" always="true"/>
  <status name="method_not_allowed"              code="405" reason="Method Not Allowed"/>
  <status name="not_acceptable"                  code="406" reason="Not Acceptable"/>
  <status name="request_timeout"                 code="408" reason="Request Timeout"/>
  <status name="conflict"                        code="409" reason="Conflict"/>
  <status name="gone"                            code="410" reason="Gone"/>
  <status name="length_required"                 code="411" reason="Length Required"/>
  <status name="payload_too_large"               code="413" reason="Payload Too Large"/>
  <status name="uri_too_long"                    code="414" reason="URI Too Long"/>
  <status name="unsupported_media_type"          code="415" reason="Unsupported Media Type"/>
  <status name="unprocessable_entity"            code="422" reason="Unprocessable Entity"/>
  <status name="locked"                          code="423" reason="Locked"/>
  <status name="failed_dependency"               code="424" reason="Failed Dependency"/>
  <status name="precondition_required"           code="428" reason="Precondition Required"/>
  <status name="too_many_requests"               code="429" reason="Too Many Requests"/>
  <status name="request_header_fields_too_large" code="431" reason="Request Header Fields Too Large"/>
  <status name="unavailable_for_legal_reasons"   code="451" reason="Unavailable For Legal Reasons"/>
  <status name="internal_server_error"           code="500" reason="Internal Server Error" always="true"/>
  <status name="not_implemented"                 code="501" reason="Not Implemented"/>
  <status name="bad_gateway"                     code="502" reason="Bad Gateway"/>
  <status name="service_unavailable"             code="503" reason="Service Unavailable"/>
  <status name="gateway_timeout"                 code="504" reason="Gateway Timeout"/>
  <status name="insufficient_storage"            code="507" reason="Insufficient Storage"/>
</xsl:variable>

<!-- The classes of statuses: 2 for 2xx, etc. -->
<xsl:variable name="bzadmin-status-classes" as="element(class)+">
  <class code="2" label="Success"/>
  <class code="3" label="Redirection"/>
  <class code="4" label="Client error"/>
  <class code="5" label="Server error"/>
</xsl:variable>

<!--
  Returns the class of a status count attribute of the generator statistics (2 for `ok`), empty if
  the status is unknown
-->
<xsl:function name="f:status-class" as="xs:integer?">
  <xsl:param name="count" as="attribute()"/>
  <xsl:sequence select="$bzadmin-statuses[@name = local-name($count)]/(xs:integer(@code) idiv 100)"/>
</xsl:function>

<!--
  Returns the sum of the status counts of a class (2 for 2xx)
-->
<xsl:function name="f:status-class-count" as="xs:double">
  <xsl:param name="counts" as="attribute()*"/>
  <xsl:param name="class"  as="xs:integer"/>
  <xsl:sequence select="sum($counts[f:status-class(.) = $class])"/>
</xsl:function>

<!--
  Returns a status count of the statistics table: empty when there is none
-->
<xsl:function name="f:status-count" as="xs:string">
  <xsl:param name="count" as="xs:double"/>
  <xsl:sequence select="if ($count = 0) then '' else format-number($count, '#,##0')"/>
</xsl:function>

<!--
  Display the generator statistics in the selected view: performance or status codes
-->
<xsl:template match="content[@name='statistics']" mode="bzadmin-application">
<xsl:variable name="view" select="f:application-view(root(.))"/>
<section>
  <h2>Generator <xsl:value-of select="if ($view = 'status-codes') then 'status codes' else 'performance'"/></h2>
  <xsl:apply-templates select="statistics" mode="bzadmin-application-key-metrics"/>
  <p class="actions">
    <button type="button" data-href="{f:to-path('/bzadmin/application/statistics')}{if ($view = 'status-codes') then '?view=status-codes' else ''}" id="refresh-statistics" class="button hollow"><svg-icon name="refresh"/> Refresh</button>
    <xsl:apply-templates select="statistics/@since" mode="bzadmin-application-since"/>
    <button type="button" data-href="{f:xml-url('/bzadmin/api/application/statistics/reset')}" id="reset-statistics" class="button alert"><svg-icon name="trash"/> Reset statistics</button>
  </p>
  <xsl:for-each select="statistics">
    <div class="table-scroll" tabindex="0" role="group" aria-label="Generator statistics"><sortable-table>
    <xsl:choose>
      <xsl:when test="$view = 'status-codes'"><xsl:apply-templates select="." mode="bzadmin-application-status-table"/></xsl:when>
      <xsl:otherwise><xsl:apply-templates select="." mode="bzadmin-application-performance-table"/></xsl:otherwise>
    </xsl:choose>
    </sortable-table></div>
  </xsl:for-each>
</section>
</xsl:template>

<!--
  The first cell of a row of the statistics table: the generator, its row highlighted when it is
  slow or returned server errors
-->
<xsl:template match="statistic" mode="bzadmin-application-statistic">
  <xsl:if test="@avg-etag > 10000 or @avg-process > 100000 or f:status-class-count(status/@*, 5) > 0">
    <xsl:attribute name="class">warn</xsl:attribute>
  </xsl:if>
  <td data-sort="{f:generator-class(@generator)}"><xsl:sequence select="f:generator-name(@generator, concat('generator-statistics-', generate-id(.)))"/></td>
</xsl:template>

<!--
  The statistics table in the performance view: the times in microseconds, the counts of each
  class of statuses and the calls
-->
<xsl:template match="statistics" mode="bzadmin-application-performance-table">
  <table class="basic-table statistics-table is-panel" aria-label="Generator performance" data-columns="performance">
    <thead>
      <tr>
        <th></th>
        <th colspan="3">ETag (&#xb5;s)</th>
        <th colspan="3">Process (&#xb5;s)</th>
        <th colspan="4">Status</th>
        <th></th>
      </tr>
      <tr>
        <th>Generator</th>
        <th class="number">Min</th>
        <th class="number">Max</th>
        <th class="number">Avg</th>
        <th class="number">Min</th>
        <th class="number">Max</th>
        <th class="number">Avg</th>
        <xsl:for-each select="$bzadmin-status-classes">
          <th class="status number"><abbr title="{@label}"><xsl:value-of select="@code"/>xx</abbr></th>
        </xsl:for-each>
        <th class="calls number">Calls</th>
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
        <xsl:variable name="counts" select="statistic/status/@*"/>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="total" select="f:status-class-count($counts, xs:integer(@code))"/>
          <td class="status{if ($total = 0) then ' muted' else if (@code = 5) then ' server_error' else ''}"><xsl:value-of select="format-number($total, '#,##0')"/></td>
        </xsl:for-each>
        <td class="calls"><xsl:value-of select="format-number(sum(statistic/@count), '#,##0')"/></td>
      </tr>
    </tfoot>
    <tbody>
      <xsl:for-each select="statistic">
        <xsl:sort select="f:generator-class(@generator)"/>
      <tr>
        <xsl:apply-templates select="." mode="bzadmin-application-statistic"/>
        <xsl:variable name="etag-trend"    select="if (@avg-last-etag    &gt; @avg-etag*1.2)    then 'up' else if (@avg-last-etag    &lt; @avg-etag*0.8)    then 'down' else ''"/>
        <xsl:variable name="process-trend" select="if (@avg-last-process &gt; @avg-process*1.2) then 'up' else if (@avg-last-process &lt; @avg-process*0.8) then 'down' else ''" />
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
        <xsl:variable name="counts" select="status/@*"/>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="count" select="f:status-class-count($counts, xs:integer(@code))"/>
          <td class="status{if (@code = 5 and $count > 0) then ' mark' else ''}"><xsl:value-of select="f:status-count($count)"/></td>
        </xsl:for-each>
        <td class="calls{f:muted-zero(@count)}"><xsl:value-of select="format-number(@count, '#,##0')"/></td>
      </tr>
      </xsl:for-each>
    </tbody>
  </table>
</xsl:template>

<!--
  The statistics table in the status codes view: for each class, its common codes (`@always`), the
  other codes that a generator returned, and the total of the class, even when zero; then the calls. The codes are
  listed in `data-columns`, so that a refresh can tell whether the columns changed.
-->
<xsl:template match="statistics" mode="bzadmin-application-status-table">
  <xsl:variable name="names" select="distinct-values(statistic/status/@*/local-name())"/>
  <xsl:variable name="codes" select="$bzadmin-statuses[@always = 'true' or @name = $names]"/>
  <xsl:variable name="statistics" select="statistic"/>
  <table class="basic-table statistics-table is-panel" aria-label="Generator status codes" data-columns="{string-join(('status-codes', $codes/@code), ' ')}">
    <thead>
      <tr>
        <th></th>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="class" select="xs:integer(@code)"/>
          <th colspan="{count($codes[xs:integer(@code) idiv 100 = $class]) + 1}" class="status-class"><xsl:value-of select="@label"/></th>
        </xsl:for-each>
        <th></th>
      </tr>
      <tr>
        <th>Generator</th>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="class" select="xs:integer(@code)"/>
          <xsl:for-each select="$codes[xs:integer(@code) idiv 100 = $class]">
            <th class="status number"><abbr title="{@reason}"><xsl:value-of select="@code"/></abbr></th>
          </xsl:for-each>
          <th class="status total number"><abbr title="All {lower-case(@label)} codes"><xsl:value-of select="@code"/>xx</abbr></th>
        </xsl:for-each>
        <th class="calls number">Calls</th>
      </tr>
    </thead>
    <tfoot>
      <tr>
        <td></td>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="class" select="xs:integer(@code)"/>
          <xsl:for-each select="$codes[xs:integer(@code) idiv 100 = $class]">
            <xsl:variable name="name" select="string(@name)"/>
            <xsl:variable name="total" select="sum($statistics/status/@*[local-name() = $name])"/>
            <td class="status{if ($total = 0) then ' muted' else if ($class = 5) then ' server_error' else ''}"><xsl:value-of select="format-number($total, '#,##0')"/></td>
          </xsl:for-each>
          <xsl:variable name="total" select="f:status-class-count($statistics/status/@*, $class)"/>
          <td class="status total{if ($total = 0) then ' muted' else if ($class = 5) then ' server_error' else ''}"><xsl:value-of select="format-number($total, '#,##0')"/></td>
        </xsl:for-each>
        <td class="calls"><xsl:value-of select="format-number(sum($statistics/@count), '#,##0')"/></td>
      </tr>
    </tfoot>
    <tbody>
      <xsl:for-each select="statistic">
        <xsl:sort select="f:generator-class(@generator)"/>
      <tr>
        <xsl:apply-templates select="." mode="bzadmin-application-statistic"/>
        <xsl:variable name="counts" select="status/@*"/>
        <xsl:for-each select="$bzadmin-status-classes">
          <xsl:variable name="class" select="xs:integer(@code)"/>
          <xsl:for-each select="$codes[xs:integer(@code) idiv 100 = $class]">
            <xsl:variable name="name" select="string(@name)"/>
            <xsl:variable name="count" select="sum($counts[local-name() = $name])"/>
            <td class="status{if ($class = 5 and $count > 0) then ' mark' else ''}"><xsl:value-of select="f:status-count($count)"/></td>
          </xsl:for-each>
          <xsl:variable name="total" select="f:status-class-count($counts, $class)"/>
          <td class="status total{if ($total = 0) then ' muted' else if ($class = 5) then ' mark' else ''}"><xsl:value-of select="format-number($total, '#,##0')"/></td>
        </xsl:for-each>
        <td class="calls{f:muted-zero(@count)}"><xsl:value-of select="format-number(@count, '#,##0')"/></td>
      </tr>
      </xsl:for-each>
    </tbody>
  </table>
</xsl:template>

<!--
  When the statistics started: at startup or when they were last reset; the date only if it is not
  today. Replaced when the statistics are refreshed (#statistics-since).
-->
<xsl:template match="@since" mode="bzadmin-application-since">
  <xsl:variable name="since" select="xs:dateTime(.)"/>
  <xsl:variable name="today" select="xs:date(adjust-dateTime-to-timezone(current-dateTime(), timezone-from-dateTime($since))) = xs:date($since)"/>
  <span id="statistics-since">Since <time datetime="{.}" title="{format-dateTime($since, '[D1] [MNn,*-3] [Y0001] at [H01]:[m01]:[s01] [Z]')}">
    <xsl:value-of select="format-dateTime($since, if ($today) then '[H01]:[m01]' else '[D1] [MNn,*-3] [H01]:[m01]')"/>
  </time></span>
</xsl:template>

<!--
  Returns the total time of the calls of a generator statistic in microseconds, for 'etag' or
  'process': the average times the calls when Berlioz does not report the total
-->
<xsl:function name="f:total-time" as="xs:double">
  <xsl:param name="statistic" as="element(statistic)"/>
  <xsl:param name="kind"      as="xs:string"/>
  <xsl:variable name="total" select="$statistic/@*[local-name() = concat('total-', $kind)]"/>
  <xsl:sequence select="if ($total) then number($total)
                        else number(($statistic/@*[local-name() = concat('avg-', $kind)], 0)[1]) * number($statistic/@count)"/>
</xsl:function>

<!--
  Returns the share of the calls as a percentage with one decimal, empty without any call
-->
<xsl:function name="f:share-of-calls" as="xs:string?">
  <xsl:param name="count" as="xs:double"/>
  <xsl:param name="calls" as="xs:double"/>
  <xsl:sequence select="if ($calls = 0) then () else format-number($count div $calls, '0.0%')"/>
</xsl:function>

<!--
  Returns the status of a class (4 for 4xx) with the highest count, empty if there is none
-->
<xsl:function name="f:most-frequent-status" as="element(status)?">
  <xsl:param name="counts" as="attribute()*"/>
  <xsl:param name="class"  as="xs:integer"/>
  <xsl:variable name="statuses" select="$bzadmin-statuses[xs:integer(@code) idiv 100 = $class][@name = $counts/local-name()]"/>
  <xsl:variable name="totals" select="for $s in $statuses return sum($counts[local-name() = $s/@name])"/>
  <xsl:sequence select="if (empty($statuses)) then () else $statuses[index-of($totals, max($totals))[1]]"/>
</xsl:function>

<!--
  Returns the detail of the errors of a class: their share of the calls and their most frequent code
-->
<xsl:function name="f:errors-detail" as="xs:string?">
  <xsl:param name="counts" as="attribute()*"/>
  <xsl:param name="class"  as="xs:integer"/>
  <xsl:param name="calls"  as="xs:double"/>
  <xsl:variable name="share" select="f:share-of-calls(f:status-class-count($counts, $class), $calls)"/>
  <xsl:variable name="most" select="f:most-frequent-status($counts, $class)"/>
  <xsl:sequence select="if ($share) then concat($share, ' of calls', if ($most) then concat(', mostly ', $most/@code, ' ', $most/@reason) else '') else ()"/>
</xsl:function>

<!--
  The key metrics of the view, replaced with the table when the statistics are refreshed
  (#statistics-metrics); both start with the generator calls.

  Performance: the average time of all the calls (from the totals of the generators), the slowest
  generator and the generators slower than 100 ms on average (as marked in the table).

  Status codes: the client errors (4xx) and the server errors (5xx), with their share of the calls
  and their most frequent code, and the generators that returned errors (the highest error rate).
-->
<xsl:template match="statistics" mode="bzadmin-application-key-metrics">
  <xsl:variable name="view" select="f:application-view(root(.))"/>
  <xsl:variable name="counts" select="statistic/status/@*"/>
  <xsl:variable name="calls" select="sum(statistic/@count)"/>
  <xsl:variable name="errors" select="f:status-class-count($counts, 5)"/>
  <dl class="key-metrics" id="statistics-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Generator calls'"/>
      <xsl:with-param name="value" select="format-number($calls, '#,##0')"/>
      <xsl:with-param name="detail" select="concat('by ', count(statistic), ' generators')"/>
    </xsl:call-template>
    <xsl:choose>
      <xsl:when test="$view = 'status-codes'">
        <xsl:variable name="failing" select="statistic[f:status-class-count(status/@*, 4) + f:status-class-count(status/@*, 5) gt 0]"/>
        <xsl:variable name="rates" select="for $s in $failing return (f:status-class-count($s/status/@*, 4) + f:status-class-count($s/status/@*, 5)) div number($s/@count)"/>
        <xsl:variable name="worst" select="if (empty($failing)) then () else $failing[index-of($rates, max($rates))[1]]"/>
        <xsl:call-template name="bzadmin-key-metric">
          <xsl:with-param name="label" select="'Client errors'"/>
          <xsl:with-param name="value" select="format-number(f:status-class-count($counts, 4), '#,##0')"/>
          <xsl:with-param name="detail" select="f:errors-detail($counts, 4, $calls)"/>
        </xsl:call-template>
        <xsl:call-template name="bzadmin-key-metric">
          <xsl:with-param name="label" select="'Server errors'"/>
          <xsl:with-param name="value" select="format-number($errors, '#,##0')"/>
          <xsl:with-param name="detail" select="f:errors-detail($counts, 5, $calls)"/>
          <xsl:with-param name="warning" select="$errors gt 0"/>
        </xsl:call-template>
        <xsl:call-template name="bzadmin-key-metric">
          <xsl:with-param name="label" select="'Generators with errors'"/>
          <xsl:with-param name="value" select="string(count($failing))"/>
          <xsl:with-param name="detail" select="if ($worst) then concat('Highest rate: ', f:generator-label($worst/@generator), ' (', format-number(max($rates), '0.0%'), ')') else 'Client or server errors'"/>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:variable name="slowest" select="(statistic[number(@avg-process) = max(../statistic/@avg-process/number())])[1]"/>
        <xsl:variable name="slow" select="statistic[number(@avg-process) gt 100000]"/>
        <xsl:variable name="process" select="sum(for $s in statistic return f:total-time($s, 'process'))"/>
        <xsl:variable name="etag" select="sum(for $s in statistic return f:total-time($s, 'etag'))"/>
        <xsl:call-template name="bzadmin-key-metric">
          <xsl:with-param name="label" select="'Average time'"/>
          <xsl:with-param name="value" select="if ($calls gt 0) then concat(format-number($process div $calls div 1000, '#,##0.0'), ' ms') else ()"/>
          <xsl:with-param name="zero-muted" select="false()"/>
          <xsl:with-param name="detail" select="if ($calls gt 0) then concat('Process per call; ETag ', format-number($etag div $calls div 1000, '#,##0.0'), ' ms') else ()"/>
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
      </xsl:otherwise>
    </xsl:choose>
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
<xsl:call-template name="bzadmin-dialog">
  <xsl:with-param name="id" select="f:library-dialog-id(.)"/>
  <xsl:with-param name="class" select="'library-dialog'"/>
  <xsl:with-param name="title"><xsl:value-of select="f:get-library-name(.)"/><xsl:text> </xsl:text><xsl:sequence select="f:get-library-version(.)"/></xsl:with-param>
  <xsl:with-param name="body">
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
  </xsl:with-param>
</xsl:call-template>
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
