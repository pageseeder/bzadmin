<?xml version="1.0"?>
<!--
  Display the Berlioz services.

  The services are those Berlioz is running (Berlioz ListRuntimeServices), including services
  declared in JARs, with the source that declared each, the declarations it replaced and the
  diagnostics of the last load (sources, warnings, failure). See docs/design/metadata-contract.md. GetBerliozControl ('berlioz-control')
  tells whether the services can be reloaded. The Lookup view ('group-by=lookup') has the form to find
  the service matching a URL with Berlioz GetMatchingService ('service-match', /bzadmin/services/lookup).
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!-- The views of the services, selected by the 'group-by' parameter -->
<xsl:template match="content[@name='runtime-services']" mode="bzadmin-secondary-nav">
  <xsl:call-template name="bzadmin-services-views">
    <xsl:with-param name="active" select="string(/root/header/http-parameters/parameter[@name = 'group-by'][1])"/>
  </xsl:call-template>
</xsl:template>

<!-- The service matching a URL is shown in the Lookup view -->
<xsl:template match="content[@name='service-match']" mode="bzadmin-secondary-nav">
  <xsl:call-template name="bzadmin-services-views">
    <xsl:with-param name="active" select="'lookup'"/>
  </xsl:call-template>
</xsl:template>

<!--
  Links to the views of the Services page.

  @param active The 'group-by' value of the current view
-->
<xsl:template name="bzadmin-services-views">
  <xsl:param name="active" as="xs:string"/>
  <xsl:variable name="views" select="('', 'generator', 'tree', 'lookup')"/>
  <xsl:variable name="labels" select="('Services', 'Generators', 'Tree', 'Lookup')"/>
  <xsl:for-each select="$views">
    <xsl:variable name="i" select="position()"/>
    <xsl:call-template name="bzadmin-secondary-link">
      <xsl:with-param name="href"   select="concat(f:to-path('/bzadmin/services'), '?group-by=', .)"/>
      <xsl:with-param name="label"  select="$labels[$i]"/>
      <xsl:with-param name="active" select=". = $active"/>
    </xsl:call-template>
  </xsl:for-each>
</xsl:template>

<!--
  Display the services.
-->
<xsl:template match="content[@name='runtime-services']" mode="bzadmin-content">
  <xsl:variable name="group-by" select="string(/root/header/http-parameters/parameter[@name = 'group-by'][1])"/>
  <xsl:variable name="runtime" select="live-services"/>
  <section>
    <xsl:call-template name="bzadmin-control-form">
      <xsl:with-param name="id"       select="'reload-services'"/>
      <xsl:with-param name="path"     select="'/bzadmin/services/reload'"/>
      <xsl:with-param name="scope"    select="'services'"/>
      <xsl:with-param name="label"    select="'Reload services'"/>
      <xsl:with-param name="icon"     select="'refresh'"/>
      <xsl:with-param name="disabled" as="node()*">
        <xsl:apply-templates select="../content[@name='berlioz-control']/berlioz-control" mode="bzadmin-control-disabled"/>
      </xsl:with-param>
    </xsl:call-template>
  </section>
  <section id="services-details">
    <xsl:apply-templates select="$runtime/@load-status" mode="bzadmin-services"/>
    <xsl:if test="$group-by != 'lookup'">
      <xsl:apply-templates select="$runtime[warning]" mode="bzadmin-services-warnings"/>
      <xsl:apply-templates select="$runtime[service]" mode="bzadmin-services-key-metrics"/>
    </xsl:if>
    <xsl:choose>
      <xsl:when test="$group-by = 'lookup'">
        <xsl:call-template name="bzadmin-service-lookup"/>
      </xsl:when>
      <xsl:when test="not($runtime/service)"><p><i>No service is running.</i></p></xsl:when>
      <xsl:when test="$group-by = 'generator'">
        <xsl:apply-templates select="$runtime" mode="bzadmin-by-generator"/>
      </xsl:when>
      <xsl:when test="$group-by = 'tree'">
        <xsl:apply-templates select="$runtime" mode="bzadmin-as-tree"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="$runtime" mode="bzadmin-by-service"/>
        <xsl:apply-templates select="$runtime[source]" mode="bzadmin-services-sources"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="$group-by != 'lookup'">
      <xsl:apply-templates select="$runtime/service" mode="reveal-service"/>
    </xsl:if>
  </section>
</xsl:template>

<!--
  The services by method, the generator classes they use, the cacheable services (all their
  generators have an ETag) and the groups
-->
<xsl:template match="live-services" mode="bzadmin-services-key-metrics">
  <xsl:variable name="methods" select="distinct-values(service/upper-case(@method))"/>
  <xsl:variable name="groups" select="distinct-values(service/@group)"/>
  <xsl:variable name="cacheable" select="service[@cacheable = 'true']"/>
  <dl class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Services'"/>
      <xsl:with-param name="value" select="string(count(service))"/>
      <xsl:with-param name="detail" select="string-join(for $m in $methods return concat(count(service[upper-case(@method) = $m]), ' ', $m), ', ')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Generators'"/>
      <xsl:with-param name="value" select="string(count(distinct-values(service/generator/@class)))"/>
      <xsl:with-param name="detail" select="concat('classes, used ', count(service/generator), ' times')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Cacheable'"/>
      <xsl:with-param name="value" select="string(count($cacheable))"/>
      <xsl:with-param name="detail" select="concat(f:to-percent(count($cacheable) div count(service)), ' of the services have an ETag')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Groups'"/>
      <xsl:with-param name="value" select="string(count($groups))"/>
      <xsl:with-param name="detail" select="if (count($groups) le 4) then string-join($groups, ', ') else concat(string-join(subsequence($groups, 1, 3), ', '), '…')"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  The service matching a URL: the Lookup view with the result.
-->
<xsl:template match="content[@name='service-match']" mode="bzadmin-content">
  <section id="services-details">
    <xsl:call-template name="bzadmin-service-lookup">
      <xsl:with-param name="result" select="matching-service"/>
    </xsl:call-template>
  </section>
</xsl:template>

<!--
  The Lookup view: the form to find the service matching a URL, and the result (#service-lookup-result).

  The frontend reduces the URL to the path Berlioz matches (no origin, context path, prefix,
  extension, query or fragment) and replaces the result; without script, the form opens the
  lookup page with the path as entered.

  @param result The 'matching-service' element; none before a lookup
-->
<xsl:template name="bzadmin-service-lookup">
  <xsl:param name="result" as="element(matching-service)?"/>
  <xsl:variable name="url"    select="string($bzheader/http-parameters/parameter[@name = 'url'][1])"/>
  <xsl:variable name="method" select="upper-case(($bzheader/http-parameters/parameter[@name = 'method'][1], 'GET')[1])"/>
  <div class="service-lookup panel">
    <h2>Find a service</h2>
    <form id="service-lookup" action="{f:to-path('/bzadmin/services/lookup')}"
          data-context="{$bzheader/path/@context}" data-prefix="{$bzheader/path/@prefix}">
      <label for="service-lookup-url">Request URL or path</label>
      <div class="input-group">
        <select class="input-group-field service-lookup-method" name="method" aria-label="HTTP method">
          <xsl:for-each select="('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'QUERY')">
            <option value="{.}"><xsl:if test=". = $method"><xsl:attribute name="selected">selected</xsl:attribute></xsl:if><xsl:value-of select="."/></option>
          </xsl:for-each>
        </select>
        <input id="service-lookup-url" class="input-group-field" type="text" name="url" value="{$url}" required="required"
               placeholder="/path/to/page" autocomplete="off" spellcheck="false" aria-describedby="service-lookup-help"/>
        <div class="input-group-button">
          <button type="submit" class="button">Find</button>
        </div>
      </div>
      <p id="service-lookup-help" class="help-text muted">Choose an HTTP method and enter an application path, e.g. <code>/bzadmin/config</code>, or a full URL.</p>
      <details class="service-lookup-help">
        <summary>How URL matching works</summary>
        <p>Find which registered service Berlioz selects for a request. This lookup does not execute the service.</p>
        <p>The HTTP method and application path are matched against registered service URL patterns.
          Full URLs are reduced to the application path, excluding the origin, query string, fragment,
          application context and servlet prefix. For extension-mapped servlets, the final extension is removed too.</p>
        <p>The extension selects the servlet (HTML, XML, JSON, source), which decides the response format.
          The result shows the selected service and URI parameters extracted from the path.</p>
      </details>
    </form>
  </div>
  <div id="service-lookup-result" aria-live="polite">
    <xsl:apply-templates select="$result" mode="bzadmin-service-lookup">
      <xsl:with-param name="url"    select="$url"/>
      <xsl:with-param name="method" select="$method"/>
    </xsl:apply-templates>
  </div>
</xsl:template>

<!--
  The service matching the URL.
-->
<xsl:template match="matching-service[@matched = 'true']" mode="bzadmin-service-lookup">
  <xsl:param name="method" as="xs:string"/>
  <div class="service-match panel">
    <h2>Matching result</h2>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Service match summary"><table class="summary-table" aria-label="Service match summary">
      <tbody>
        <tr><th scope="row">HTTP method</th><td><code><xsl:value-of select="$method"/></code></td></tr>
        <tr><th scope="row">Path checked</th><td><code><xsl:value-of select="url/@path"/></code></td></tr>
        <tr><th scope="row">Matched URL pattern</th><td><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(url/@pattern)"/></code></td></tr>
        <tr><th scope="row">Selected service</th><td><code><xsl:value-of select="service/@id"/></code><xsl:apply-templates select="service" mode="bzadmin-service-tags"/></td></tr>
      </tbody>
    </table></div>
    <xsl:choose>
      <xsl:when test="url/parameter">
        <h3>URI parameters</h3>
        <p class="help-text muted">Values extracted from the matched path.</p>
        <div class="table-scroll" tabindex="0" role="group" aria-label="Matched URI parameters"><table class="basic-table info" aria-label="Matched URI parameters">
          <thead><tr><th>Name</th><th>Value</th></tr></thead>
          <tbody>
            <xsl:for-each select="url/parameter">
              <tr><td><code><xsl:value-of select="@name"/></code></td><td><code><xsl:value-of select="@value"/></code></td></tr>
            </xsl:for-each>
          </tbody>
        </table></div>
      </xsl:when>
      <xsl:otherwise><p class="muted">No URI parameters.</p></xsl:otherwise>
    </xsl:choose>
    <h3>Service details</h3>
    <xsl:apply-templates select="service" mode="bzadmin-service-details"/>
  </div>
</xsl:template>

<!--
  No service matches the URL.
-->
<xsl:template match="matching-service" mode="bzadmin-service-lookup">
  <xsl:param name="url"    as="xs:string"/>
  <xsl:param name="method" as="xs:string"/>
  <div class="service-match panel">
    <h2>No matching service</h2>
    <p>No <b><xsl:value-of select="$method"/></b> service matches <code><xsl:value-of select="$url"/></code>.</p>
  </div>
</xsl:template>

<!--
  Status of the service configuration, when it is not ready, with the cause of the last failed
  load and when Berlioz tries again. Berlioz only reports the line and the parser's message
  when 'berlioz.errors.detail' is not 'minimal'.
-->
<xsl:template match="@load-status" mode="bzadmin-services">
  <xsl:choose>
    <xsl:when test=". = 'ready'"/>
    <xsl:when test=". = 'reload-failed'">
      <div class="alert callout" role="alert">
        <p><svg-icon name="warning"/> <b>The last reload of the service configuration failed<xsl:apply-templates select="../@failed" mode="bzadmin-services"/>.</b>
        Berlioz keeps running the last valid services, listed below.</p>
        <xsl:apply-templates select="../load-failure" mode="bzadmin-services"/>
        <xsl:if test="not(../load-failure)"><p>See the logs for the cause.</p></xsl:if>
      </div>
    </xsl:when>
    <xsl:when test=". = 'startup-failed'">
      <div class="alert callout" role="alert">
        <p><svg-icon name="warning"/> <b>The service configuration could not be loaded on startup<xsl:apply-templates select="../@failed" mode="bzadmin-services"/>.</b>
        No service is running.</p>
        <xsl:apply-templates select="../load-failure" mode="bzadmin-services"/>
        <xsl:if test="not(../load-failure)"><p>See the logs for the cause.</p></xsl:if>
      </div>
    </xsl:when>
    <xsl:otherwise>
      <div class="callout"><p>Service configuration: <b><xsl:value-of select="."/></b></p></div>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!-- When the last load failed -->
<xsl:template match="@failed" mode="bzadmin-services">
  <xsl:text> </xsl:text><xsl:sequence select="f:datetime-diff(xs:dateTime(.))"/>
</xsl:template>

<!--
  The cause of the last failed load, and when Berlioz tries again
-->
<xsl:template match="load-failure" mode="bzadmin-services">
  <p class="service-load-failure">
    <xsl:if test="@source">In <code><xsl:value-of select="@source"/></code><xsl:if test="@line">, line <xsl:value-of select="@line"/></xsl:if>: </xsl:if>
    <xsl:value-of select="."/>
  </p>
  <xsl:if test="../@retry-after">
    <p>Berlioz tries again <xsl:sequence select="f:datetime-diff(xs:dateTime(../@retry-after))"/>, or reload the services.</p>
  </xsl:if>
</xsl:template>

<!--
  Warnings of the last successful load: Berlioz loaded the services despite them.
-->
<xsl:template match="live-services" mode="bzadmin-services-warnings">
  <div class="callout warning">
    <p><svg-icon name="warning"/> <b><xsl:value-of select="count(warning)"/> <xsl:value-of select="if (count(warning) = 1) then ' warning' else ' warnings'"/></b>
    when the service configuration was loaded:</p>
    <ul>
      <xsl:for-each select="warning">
        <li>
          <xsl:if test="@source"><code><xsl:value-of select="@source"/></code><xsl:if test="@line">, line <xsl:value-of select="@line"/></xsl:if>: </xsl:if>
          <xsl:value-of select="."/>
        </li>
      </xsl:for-each>
    </ul>
  </div>
</xsl:template>

<!--
  The service configuration files of the last successful load, in load order: a later one
  replaces the services of an earlier one for the same method and URL pattern.
-->
<xsl:template match="live-services" mode="bzadmin-services-sources">
  <div class="panel">
    <h2>Service configuration</h2>
    <p>
      <xsl:text>Loaded from these files, in this order</xsl:text>
      <xsl:if test="@loaded"><xsl:text>, </xsl:text><xsl:sequence select="f:datetime-diff(xs:dateTime(@loaded))"/></xsl:if>
      <xsl:text>. A later file replaces the services of an earlier one for the same method and URL pattern.</xsl:text>
    </p>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Service configuration files"><table class="basic-table" aria-label="Service configuration files">
      <thead>
        <tr><th scope="col">#</th><th scope="col">File</th><th scope="col">From</th><th scope="col">Services</th><th scope="col">Replaced</th></tr>
      </thead>
      <tbody>
        <xsl:for-each select="source">
          <xsl:variable name="name" select="@name"/>
          <tr>
            <td><xsl:value-of select="position()"/></td>
            <td><code><xsl:value-of select="@name"/></code></td>
            <td><xsl:value-of select="if (@kind = 'classpath') then 'JAR overlay' else 'Application'"/></td>
            <td><xsl:value-of select="count(../service[@source = $name])"/></td>
            <td><xsl:value-of select="count(../service/overridden[@source = $name])"/></td>
          </tr>
        </xsl:for-each>
      </tbody>
    </table></div>
  </div>
</xsl:template>

<!--
  Display the services organised by group
-->
<xsl:template match="live-services" mode="bzadmin-by-service">
<xsl:for-each-group select="service" group-by="@group">
  <xsl:sort select="current-grouping-key()"/>
  <div class="panel">
  <h2><xsl:value-of select="current-grouping-key()"/> services
    <span class="count muted" title="{count(current-group())} services">(<xsl:value-of select="count(current-group())"/>)</span></h2>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Services in {current-grouping-key()}"><sortable-table>
  <table class="basic-table" aria-label="Services in {current-grouping-key()}">
    <thead>
      <tr>
        <th width="25%">Name</th>
        <th width="35%">URLs</th>
        <th width="8%">Method</th>
        <th width="8%">Cache</th>
        <th width="14%" title="Formats the service can produce; the servlet of the URL's extension decides the response">Formats</th>
        <th data-sort="none">Info</th>
      </tr>
    </thead>
    <tbody>
    <xsl:for-each select="current-group()">
      <xsl:sort select="@id"/>
    <tr>
      <td><xsl:value-of select="@id"/><xsl:apply-templates select="." mode="bzadmin-service-tags"/></td>
      <td>
      <xsl:for-each select="url">
        <div class="url-pattern-row"><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(@pattern)"/></code></div>
      </xsl:for-each>
      </td>
      <td><xsl:value-of select="@method"/></td>
      <td>
        <xsl:choose>
          <xsl:when test="@cacheable = 'true'"><abbr title="{if (@cache-control) then @cache-control else 'No Cache-Control'}">Yes</abbr></xsl:when>
          <xsl:otherwise>No</xsl:otherwise>
        </xsl:choose>
      </td>
      <td><xsl:value-of select="f:outputs(@supported)"/></td>
      <td><button type="button" class="link-button" commandfor="{f:dialog-id(.)}" command="show-modal" aria-label="Show service {@id} [{@method}]">show</button></td>
    </tr>
    </xsl:for-each>
  </tbody>
  </table>
  </sortable-table></div>
  </div>
</xsl:for-each-group>
</xsl:template>

<!--
  Display the services organised by generator
-->
<xsl:template match="live-services" mode="bzadmin-by-generator">
  <div class="panel">
  <xsl:for-each-group select="service/generator" group-by="f:generator-class(@class)">
    <xsl:sort select="current-grouping-key()"/>
    <xsl:variable name="services" select="current-group()/parent::service"/>
    <h2 class="generator-title">
      <span><xsl:value-of select="tokenize(current-grouping-key(), '\.')[last()]"/><xsl:text> </xsl:text>
        <span class="count muted" title="{count($services)} services">(<xsl:value-of select="count($services)"/>)</span>
      </span>
      <span class="generator-package">
        <xsl:if test="f:generator-origin(current-grouping-key()) != ''"><span class="generator-origin">[<xsl:value-of select="f:generator-origin(current-grouping-key())"/>]</span><xsl:text> · </xsl:text></xsl:if>
        <xsl:value-of select="replace(current-grouping-key(), '\.[^.]+$', '')"/>
      </span>
    </h2>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Services using {current-grouping-key()}"><sortable-table>
    <table class="basic-table" aria-label="Services using {current-grouping-key()}">
      <thead>
        <tr>
          <th width="10%">Group</th>
          <th width="25%">Service</th>
          <th width="40%">URLs</th>
          <th width="10%">Method</th>
          <th width="10%">Type</th>
          <th data-sort="none">Info</th>
        </tr>
      </thead>
      <tbody>
      <xsl:for-each select="$services">
      <tr>
        <td><xsl:value-of select="@group"/></td>
        <td><xsl:value-of select="@id"/></td>
        <td>
        <xsl:for-each select="url">
          <div class="url-pattern-row"><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(@pattern)"/></code></div>
        </xsl:for-each>
        </td>
        <td><xsl:value-of select="@method"/></td>
        <td><xsl:value-of select="distinct-values(generator[f:generator-class(@class) = current-grouping-key()]/@type)"/></td>
        <td><button type="button" class="link-button" commandfor="{f:dialog-id(.)}" command="show-modal" aria-label="Show service {@id} [{@method}]">show</button></td>
      </tr>
      </xsl:for-each>
    </tbody>
    </table>
    </sortable-table></div>
  </xsl:for-each-group>
  </div>
</xsl:template>

<!--
  Display the services as a tree
-->
<xsl:template match="live-services" mode="bzadmin-as-tree">
<div class="panel">
<ul class="no-bullet tree">
<xsl:for-each-group select="service" group-by="@group">
  <xsl:sort select="current-grouping-key()"/>
  <li>
    <details>
      <summary>
        <h2>
          <svg-icon name="caret-right" class="caret"/><xsl:text> </xsl:text>
          <xsl:value-of select="current-grouping-key()"/><xsl:text> </xsl:text>
          <span class="count muted" title="{count(current-group())} services">(<xsl:value-of select="count(current-group())"/>)</span>
        </h2>
      </summary>
      <ul class="no-bullet">
        <xsl:for-each select="current-group()">
          <xsl:sort select="@id"/>
          <li>
            <details>
              <summary>
                <h3>
                  <svg-icon name="caret-right" class="caret"/><xsl:text> </xsl:text>
                  <xsl:value-of select="@id"/><xsl:text> </xsl:text>
                  <i class="method">[<xsl:value-of select="@method"/>]</i>
                  <xsl:apply-templates select="." mode="bzadmin-service-tags"/>
                </h3>
              </summary>
              <div class="tree_details">
                <xsl:for-each select="url">
                  <div class="url-pattern-row"><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(@pattern)"/></code></div>
                </xsl:for-each>
                <xsl:apply-templates select="." mode="bzadmin-generators"><xsl:with-param name="scope" select="'tree'"/></xsl:apply-templates>
              </div>
            </details>
          </li>
        </xsl:for-each>
      </ul>
    </details>
  </li>
</xsl:for-each-group>
</ul>
</div>
</xsl:template>

<!--
  Flags and direct mode of a service
-->
<xsl:template match="service" mode="bzadmin-service-tags">
  <xsl:if test="overridden"><xsl:text> </xsl:text><span class="tag" title="Replaces {string-join(for $o in overridden return concat($o/@service, ' in ', $o/@source), ', ')}">overrides</span></xsl:if>
  <xsl:if test="@direct = 'true'"><xsl:text> </xsl:text><span class="tag" title="Returns the output of its generator directly, without a Berlioz envelope">direct</span></xsl:if>
  <xsl:for-each select="tokenize(@flags, '[\s,]+')[. != '']">
    <xsl:text> </xsl:text><span class="tag" title="Flag"><xsl:value-of select="."/></span>
  </xsl:for-each>
</xsl:template>

<!--
  Generators of a service
-->
<xsl:template match="service" mode="bzadmin-generators">
  <xsl:param name="scope" as="xs:string" select="'detail'"/>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Service generators"><sortable-table>
  <table class="basic-table info" aria-label="Service generators">
    <thead>
      <tr><th>Class</th><th>Name</th><th>Target</th><th>Type</th><th>Cacheable</th><th>Affects status</th><th title="Formats the generator can produce">Formats</th><th data-sort="none">Parameters</th></tr>
    </thead>
    <tbody>
      <xsl:for-each select="generator">
      <tr>
        <td data-sort="{f:generator-class(@class)}"><xsl:sequence select="f:generator-name(@class, concat('generator-', $scope, '-', generate-id(.)))"/></td>
        <td><xsl:value-of select="@name"/></td>
        <td><xsl:value-of select="@target"/></td>
        <td><xsl:value-of select="@type"/></td>
        <td><xsl:value-of select="@cacheable"/></td>
        <td><xsl:value-of select="@affect-status"/></td>
        <td><xsl:value-of select="f:outputs(@supported)"/></td>
        <td>
          <xsl:for-each select="parameter">
            <div><code><xsl:value-of select="@name"/></code> = <code><xsl:value-of select="@value"/></code></div>
          </xsl:for-each>
        </td>
      </tr>
      </xsl:for-each>
    </tbody>
  </table>
  </sortable-table></div>
</xsl:template>

<!--
  Details of a service displayed in a dialog
-->
<xsl:template match="service" mode="reveal-service">
<dialog id="{f:dialog-id(.)}" class="dialog" aria-labelledby="{f:dialog-id(.)}-title">
  <h2 class="dialog-title" id="{f:dialog-id(.)}-title"><xsl:value-of select="@id"/> [<xsl:value-of select="@method"/>]<xsl:apply-templates select="." mode="bzadmin-service-tags"/></h2>
  <xsl:apply-templates select="." mode="bzadmin-service-details"/>
  <form method="dialog">
    <button class="button">OK</button>
  </form>
</dialog>
</xsl:template>

<!--
  Details of a service: in its dialog, and for the service matching a URL
-->
<xsl:template match="service" mode="bzadmin-service-details">
  <p>Group: <b><xsl:value-of select="@group"/></b> · Formats the service can produce: <b><xsl:value-of select="f:outputs(@supported)"/></b></p>
  <xsl:if test="@source"><p>Declared in <code><xsl:value-of select="@source"/></code></p></xsl:if>
  <p class="help-text muted">The servlet that the extension of the URL maps to decides the response, from these formats:
    see the <a href="{f:to-path('/bzadmin/config')}?view=deployment">servlets on the Config page</a>.</p>
  <h3>URL Patterns</h3>
  <ul class="no-bullet">
    <xsl:for-each select="url">
      <li><div class="url-pattern-row"><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(@pattern)"/></code></div></li>
    </xsl:for-each>
  </ul>
  <h3>Response code</h3>
  <div class="row">
    <div class="medium-6 columns">
      <p>Generator(s): <b><xsl:value-of select="if (response-code/@use) then response-code/@use else '*'"/></b></p>
    </div>
    <div class="medium-6 columns">
      <p>Rule: <b><xsl:value-of select="response-code/@rule"/></b></p>
    </div>
  </div>
  <xsl:apply-templates select="response-code/@origin" mode="bzadmin-service-details"/>
  <h3>Generators</h3>
  <xsl:apply-templates select="." mode="bzadmin-generators"/>
  <xsl:if test="overridden">
    <h3>Replaced declarations</h3>
    <p class="help-text muted">Earlier declarations for the same method and URL pattern, which this service replaces.</p>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Replaced declarations"><table class="basic-table info" aria-label="Replaced declarations">
      <thead><tr><th>Service</th><th>Declared in</th><th>URL pattern</th></tr></thead>
      <tbody>
        <xsl:for-each select="overridden">
          <tr><td><code><xsl:value-of select="@service"/></code></td><td><code><xsl:value-of select="@source"/></code></td><td><code class="url-pattern"><xsl:sequence select="f:pretty-pattern(@pattern)"/></code></td></tr>
        </xsl:for-each>
      </tbody>
    </table></div>
  </xsl:if>
  <h3>Cache control</h3>
  <p>
    <xsl:choose>
      <xsl:when test="@cacheable = 'true' and @cache-control"><code><xsl:value-of select="@cache-control"/></code></xsl:when>
      <xsl:when test="@cacheable = 'true'"><i>Cacheable, without Cache-Control</i></xsl:when>
      <xsl:otherwise><i>Not cacheable: at least one generator is not cacheable</i></xsl:otherwise>
    </xsl:choose>
  </p>
  <p class="help-text muted">Berlioz reports the effective values; whether they are declared by the service, its group or a default is not available.</p>
</xsl:template>

<!--
  Where the response code rule of a service is declared
-->
<xsl:template match="@origin" mode="bzadmin-service-details">
  <p class="help-text muted">
    <xsl:choose>
      <xsl:when test=". = 'service'">Declared by the service.</xsl:when>
      <xsl:when test=". = 'group'">Declared by its group (<code>&lt;services&gt;</code>).</xsl:when>
      <xsl:when test=". = 'service-config'">Declared for all services (<code>&lt;service-config&gt;</code>).</xsl:when>
      <xsl:when test=". = 'default'">Not declared: the default rule applies.</xsl:when>
      <xsl:otherwise>Declared by: <xsl:value-of select="."/></xsl:otherwise>
    </xsl:choose>
  </p>
</xsl:template>

<!--
  @param service A service element
  @return a dialog id unique in the page, as service ids are not unique across methods
-->
<xsl:function name="f:dialog-id" as="xs:string">
  <xsl:param name="service" as="element(service)"/>
  <xsl:sequence select="concat('service-', generate-id($service))"/>
</xsl:function>

<!--
  @param supported The output types supported, e.g. 'xml,json'
  @return the output types for display, e.g. 'XML, JSON'
-->
<xsl:function name="f:outputs" as="xs:string">
  <xsl:param name="supported" as="attribute()?"/>
  <xsl:sequence select="string-join(for $t in tokenize($supported, ',') return upper-case($t), ', ')"/>
</xsl:function>

</xsl:stylesheet>
