<?xml version="1.0"?>
<!--
  Display the Berlioz configuration.

  Contents (see docs/implementation/metadata-contract.md):
  - 'global-config'         GetGlobalConfig: configured global properties (sensitive values redacted)
  - 'berlioz-options'       ListBerliozOptions: options of this Berlioz version with their defaults
  - 'berlioz-config'        GetBerliozConfig: Berlioz servlets, the XSLT processor and the servlet container
  - 'error-handler-config'  GetErrorHandlerConfig: Berlioz error handler servlets
  - 'berlioz-control'       GetBerliozControl: whether the "Reload everything" button (berlioz-reload) works
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              exclude-result-prefixes="#all">

<!--
  What the Berlioz options do, from the documentation of `org.pageseeder.berlioz.BerliozOption`
  in Berlioz 0.14.5, as HTML phrasing content shown in a popover by the 'config-info' template.
  An option without an explanation here shows the plain text description that Berlioz gives
  ('berlioz-options', since 0.14.5).
-->
<xsl:variable name="config-explanations">
  <explanation name="berlioz.control.key">Shared secret authorising control parameters (e.g. <code>berlioz-reload</code>) with an <code>Authorization: Berlioz &lt;key&gt;</code> header</explanation>
  <explanation name="berlioz.control.network">Network position from which control parameters are authorised without a key: <code>off</code>, <code>loopback</code> or <code>lan</code> (development only)</explanation>
  <explanation name="berlioz.errors.detail">Detail in legacy framework error responses: <code>minimal</code>, <code>standard</code> (exception class and message) or <code>full</code> (stack trace, headers and parameters)</explanation>
  <explanation name="berlioz.errors.generator-catch"><code>true</code> to catch errors thrown by generators; <code>false</code> to throw them to the service</explanation>
  <explanation name="berlioz.errors.handle"><code>true</code> to display errors with Berlioz fail-safe templates; <code>false</code> to use the error handler of the web application (<code>sendError</code>)</explanation>
  <explanation name="berlioz.errors.problem"><code>true</code> for RFC 9457 Problem Details in framework error responses instead of the legacy <code>&lt;error&gt;</code> format</explanation>
  <explanation name="berlioz.errors.stylesheet">Stylesheet in <code>WEB-INF</code> for framework error responses, before the built-in fail-safe template</explanation>
  <explanation name="berlioz.http.cache-control">Default <code>Cache-Control</code> header of cacheable responses when the service does not define one</explanation>
  <explanation name="berlioz.http.compression"><code>true</code> to allow Berlioz to compress responses when possible</explanation>
  <explanation name="berlioz.http.get-via-post"><code>true</code> to handle a POST request with the GET service when there is no POST service</explanation>
  <explanation name="berlioz.http.server-timing"><code>true</code> to return timing information in the <code>Server-Timing</code> header</explanation>
  <explanation name="berlioz.http.service-header"><code>true</code> to return the Berlioz service in the <code>X-Berlioz-Service</code> header</explanation>
  <explanation name="berlioz.nonce.attribute">Request attribute holding the nonce; Berlioz generates one if it is not set</explanation>
  <explanation name="berlioz.nonce.enable"><code>true</code> to include a nonce in the XML header</explanation>
  <explanation name="berlioz.profile"><code>true</code> to record the time taken by each generator and by the transformer</explanation>
  <explanation name="berlioz.redirect.allowed-hosts">Comma-separated external hosts allowed as redirect targets</explanation>
  <explanation name="berlioz.redirect.trust-forwarded-headers"><code>true</code> to trust <code>X-Forwarded-Host</code> and <code>X-Forwarded-Proto</code> for the same-host redirect check</explanation>
  <explanation name="berlioz.xml.header.version">Version of the XML header format</explanation>
  <explanation name="berlioz.xml.parse-strict"><code>true</code> to throw an error for any warning when parsing Berlioz XML files</explanation>
  <explanation name="berlioz.xslt.cache">XSLT template caching: <code>no</code>, <code>auto</code> (reload changed files) or <code>manual</code> (until cleared on the XSLT page)</explanation>
  <explanation name="berlioz.xslt.sensitivity">XSLT diagnostics that make compilation or execution fail: <code>fatal</code>, <code>error</code> or <code>warning</code></explanation>
</xsl:variable>

<!-- The views of the configuration, selected by the 'view' parameter -->
<xsl:template match="content[@name='global-config']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=')"/>
    <xsl:with-param name="label"  select="'Global properties'"/>
    <xsl:with-param name="active" select="$view != 'deployment'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=deployment')"/>
    <xsl:with-param name="label"  select="'Deployment'"/>
    <xsl:with-param name="active" select="$view = 'deployment'"/>
  </xsl:call-template>
</xsl:template>

<!--
  Display the configuration, with a tab for the global properties and one for the deployment
  (servlets and error handler) selected by the 'view' parameter.
-->
<xsl:template match="content[@name='global-config']" mode="bzadmin-content">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <section>
    <xsl:call-template name="bzadmin-control-form">
      <xsl:with-param name="id"       select="'berlioz-reload'"/>
      <xsl:with-param name="path"     select="'/bzadmin/config/reload'"/>
      <xsl:with-param name="scope"    select="'true'"/>
      <xsl:with-param name="label"    select="'Reload everything'"/>
      <xsl:with-param name="icon"     select="'refresh'"/>
      <xsl:with-param name="disabled" as="node()*">
        <xsl:apply-templates select="../content[@name='berlioz-control']/berlioz-control" mode="bzadmin-control-disabled"/>
      </xsl:with-param>
      <xsl:with-param name="description" as="node()*">Reloads the configuration and the services, clears the XSLT cache, resets the ETags and reloads the redirect rules, so that they stay consistent.</xsl:with-param>
    </xsl:call-template>
  </section>
  <section id="config-details">
    <xsl:choose>
      <xsl:when test="$view = 'deployment'">
        <xsl:apply-templates select="//content[@name='berlioz-config']" mode="bzadmin-config"/>
        <xsl:apply-templates select="//content[@name='error-handler-config']" mode="bzadmin-config"/>
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates select="." mode="bzadmin-config"/>
      </xsl:otherwise>
    </xsl:choose>
  </section>
</xsl:template>

<!--
  Display the global properties: Berlioz options, then other properties
-->
<xsl:template match="content[@name='global-config']" mode="bzadmin-config">
  <xsl:variable name="properties" select="properties/property"/>
  <xsl:variable name="options" select="//content[@name='berlioz-options']//option"/>
  <xsl:call-template name="config-key-metrics">
    <xsl:with-param name="properties" select="$properties"/>
    <xsl:with-param name="options" select="$options"/>
  </xsl:call-template>
  <p>
    <xsl:choose>
      <xsl:when test="starts-with(properties/@source, 'config-')">Merged from the default configuration file, if any, and <code><xsl:value-of select="properties/@source"/></code>. </xsl:when>
      <xsl:when test="properties/@source">Configured in <code><xsl:value-of select="properties/@source"/></code>. </xsl:when>
      <xsl:otherwise>No global configuration file. </xsl:otherwise>
    </xsl:choose>
    <xsl:text>Sensitive values are redacted.</xsl:text>
  </p>

  <div class="panel">
    <h2>Berlioz options</h2>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Berlioz options"><sortable-table>
    <table class="basic-table page-bzadmin-config_properties-table" aria-label="Berlioz options">
      <thead>
        <tr><th scope="col">Option</th><th scope="col">Value</th></tr>
      </thead>
      <tbody>
      <xsl:for-each select="$options">
        <xsl:sort select="@property"/>
        <xsl:variable name="configured" select="$properties[@name = current()/@property]"/>
        <tr>
          <th scope="row" data-sort="{@property}">
            <xsl:value-of select="@property"/>
            <xsl:call-template name="config-info"><xsl:with-param name="name" select="@property"/><xsl:with-param name="description" select="@description"/></xsl:call-template>
            <xsl:if test="@deprecated"><xsl:text> </xsl:text><span class="tag warning" title="Deprecated since Berlioz {@deprecated}{if (@removal) then concat(': removed in Berlioz ', @removal) else ''}">deprecated</span></xsl:if>
          </th>
          <td data-sort="{if ($configured) then $configured/@value else ''}">
            <xsl:choose>
              <xsl:when test="$configured"><code><xsl:value-of select="$configured/@value"/></code></xsl:when>
              <xsl:otherwise>
                <span class="tag" title="Not configured: Berlioz uses the default">default</span>
                <xsl:text> </xsl:text>
                <code class="muted"><xsl:value-of select="if (@default = '') then '(empty)' else @default"/></code>
              </xsl:otherwise>
            </xsl:choose>
          </td>
        </tr>
      </xsl:for-each>
      </tbody>
    </table>
    </sortable-table></div>
  </div>
  <div class="panel">
    <xsl:call-template name="other-properties">
      <xsl:with-param name="properties" select="$properties[not(@name = $options/@property)]"/>
      <xsl:with-param name="title" select="'Other properties'"/>
    </xsl:call-template>
  </div>
</xsl:template>

<!--
  The mode, the Berlioz options configured (deprecated ones call for attention) and the other properties

  @param properties The configured global properties
  @param options    The options of this Berlioz version
-->
<xsl:template name="config-key-metrics">
  <xsl:param name="properties" as="element(property)*"/>
  <xsl:param name="options" as="element(option)*"/>
  <xsl:variable name="configured" select="$options[@property = $properties/@name]"/>
  <xsl:variable name="deprecated" select="$configured[@deprecated]"/>
  <xsl:variable name="others" select="$properties[not(@name = $options/@property)]"/>
  <dl class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Mode'"/>
      <xsl:with-param name="value" select="string($bzheader/berlioz/@mode)"/>
      <xsl:with-param name="detail" select="if ($bzheader/berlioz/@version) then concat('Berlioz ', $bzheader/berlioz/@version) else ()"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Options configured'"/>
      <xsl:with-param name="value" select="string(count($configured))"/>
      <xsl:with-param name="detail" select="concat('of ', count($options), ' Berlioz options; the others use their default')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Deprecated options'"/>
      <xsl:with-param name="value" select="string(count($deprecated))"/>
      <xsl:with-param name="detail" select="if ($deprecated) then string-join($deprecated/@property, ', ') else 'None configured'"/>
      <xsl:with-param name="warning" select="exists($deprecated)"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Other properties'"/>
      <xsl:with-param name="value" select="string(count($others))"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  Display configured properties grouped by prefix

  @param properties The properties to display
  @param title      The title of the section
-->
<xsl:template name="other-properties">
  <xsl:param name="properties" as="element(property)*"/>
  <xsl:param name="title" as="xs:string"/>
  <xsl:if test="$properties">
    <h2><xsl:value-of select="$title"/></h2>
    <xsl:for-each-group select="$properties" group-by="substring-before(@name, '.')">
      <xsl:sort select="current-grouping-key()"/>
      <h3><xsl:value-of select="current-grouping-key()"/></h3>
      <div class="table-scroll" tabindex="0" role="group" aria-label="Properties: {current-grouping-key()}"><sortable-table>
      <table class="basic-table page-bzadmin-config_properties-table" aria-label="Properties: {current-grouping-key()}">
        <thead>
          <tr><th scope="col">Name</th><th scope="col">Value</th></tr>
        </thead>
        <tbody>
        <xsl:for-each select="current-group()">
          <xsl:sort select="@name"/>
          <tr><th scope="row" data-sort="{@name}"><xsl:value-of select="@name"/><xsl:call-template name="config-info"><xsl:with-param name="name" select="@name"/></xsl:call-template></th><td><code><xsl:value-of select="@value"/></code></td></tr>
        </xsl:for-each>
        </tbody>
      </table>
      </sortable-table></div>
    </xsl:for-each-group>
  </xsl:if>
</xsl:template>

<!-- Displayed by the global properties and the deployment tabs -->
<xsl:template match="content[@name=('berlioz-options', 'berlioz-config', 'error-handler-config')]" mode="bzadmin-content"/>

<!--
  Display the Berlioz servlets
-->
<xsl:template match="content[@name='berlioz-config']" mode="bzadmin-config">
<div class="panel">
  <h2>Servlets</h2>
  <p>The Berlioz servlets of the application. A servlet that is not loaded on startup is initialised by its first request: until then, only its mappings are known. Stylesheets are selected on first use, so a group or service is only listed once requested.</p>
  <xsl:choose>
    <xsl:when test="berlioz-config/servlet">
      <div class="table-scroll" tabindex="0" role="group" aria-label="Servlets"><sortable-table>
      <table class="basic-table" aria-label="Servlets">
        <thead>
          <tr>
            <th scope="col">Servlet</th>
            <th scope="col">Mappings</th>
            <th scope="col">Media type</th>
            <th scope="col">Cache control</th>
            <th scope="col">Compression</th>
            <th scope="col" data-sort="none">Stylesheet</th>
          </tr>
        </thead>
        <tbody>
        <xsl:for-each select="berlioz-config/servlet[not(@initialised = 'false')]">
          <xsl:sort select="@name"/>
          <tr>
            <th scope="row" data-sort="{@name}"><xsl:value-of select="@name"/><xsl:if test="@requires-control = 'true'"><xsl:text> </xsl:text><span class="tag" title="Only serves requests authorised for Berlioz control; others receive a 404">requires control</span></xsl:if></th>
            <td><code><xsl:value-of select="@mappings"/></code></td>
            <td><xsl:value-of select="@media-type"/><xsl:if test="@charset">; charset=<xsl:value-of select="@charset"/></xsl:if></td>
            <td><code><xsl:value-of select="@cache-control"/></code></td>
            <td><xsl:value-of select="@compression"/></td>
            <td>
              <xsl:apply-templates select="stylesheet" mode="bzadmin-config"/>
              <xsl:for-each select="fallback">
                <br/><xsl:text>Fallback: </xsl:text><xsl:apply-templates select="." mode="bzadmin-config"/>
              </xsl:for-each>
              <xsl:if test="stylesheet/selection">
                <ul class="no-bullet stylesheet-selections" aria-label="Selected stylesheets">
                  <xsl:for-each select="stylesheet/selection">
                    <li><xsl:value-of select="@name"/>: <xsl:value-of select="@source"/><xsl:if test="@path"><xsl:text> </xsl:text><code><xsl:value-of select="@path"/></code></xsl:if></li>
                  </xsl:for-each>
                </ul>
              </xsl:if>
            </td>
          </tr>
        </xsl:for-each>
        <xsl:for-each select="berlioz-config/servlet[@initialised = 'false']">
          <xsl:sort select="@name"/>
          <tr>
            <th scope="row" data-sort="{@name}"><xsl:value-of select="@name"/><xsl:text> </xsl:text><span class="tag" title="Registered, but not requested yet">not initialised</span></th>
            <td><code><xsl:value-of select="@mappings"/></code></td>
            <td colspan="4" class="muted">Known after its first request</td>
          </tr>
        </xsl:for-each>
        </tbody>
      </table>
      </sortable-table></div>
    </xsl:when>
    <xsl:otherwise><p><i>No Berlioz servlet reported.</i></p></xsl:otherwise>
  </xsl:choose>
</div>
<xsl:if test="berlioz-config/xslt-processor/@factory or berlioz-config/container">
  <div class="panel">
    <h2>Runtime</h2>
    <div class="table-scroll" tabindex="0" role="group" aria-label="Runtime"><table class="summary-table" aria-label="Runtime">
      <tbody>
        <xsl:for-each select="berlioz-config/xslt-processor[@factory]">
          <tr>
            <th scope="row">XSLT processor</th>
            <td>
              <xsl:value-of select="if (@product-name or @product-version) then string-join((@product-name, @product-version), ' ') else 'Unknown'"/>
              <xsl:if test="@vendor"> (<xsl:value-of select="@vendor"/>)</xsl:if>
              <xsl:if test="@xslt-version"><xsl:text> </xsl:text><span class="tag">XSLT <xsl:value-of select="@xslt-version"/></span></xsl:if>
              <br/><code><xsl:value-of select="@factory"/></code>
            </td>
          </tr>
        </xsl:for-each>
        <xsl:for-each select="berlioz-config/container">
          <tr>
            <th scope="row">Servlet container</th>
            <td><xsl:value-of select="@server-info"/><xsl:if test="@servlet-api"><xsl:text> </xsl:text><span class="tag">Servlet <xsl:value-of select="@servlet-api"/></span></xsl:if></td>
          </tr>
        </xsl:for-each>
      </tbody>
    </table></div>
  </div>
</xsl:if>
</xsl:template>

<!--
  Stylesheet of a servlet: how it is allocated and where it comes from; the path is only reported
  by Berlioz when it is a safe relative or classpath path.
-->
<xsl:template match="stylesheet|fallback" mode="bzadmin-config">
  <xsl:choose>
    <xsl:when test="@allocation = 'identity'">None (raw XML)</xsl:when>
    <xsl:otherwise>
      <xsl:if test="@allocation"><xsl:value-of select="@allocation"/>, </xsl:if>
      <xsl:value-of select="@source"/>
      <xsl:if test="@path"><xsl:text> </xsl:text><code><xsl:value-of select="@path"/></code></xsl:if>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!--
  Display the Berlioz error handler
-->
<xsl:template match="content[@name='error-handler-config']" mode="bzadmin-config">
  <div class="panel">
  <h2>Error handler</h2>
  <xsl:choose>
    <xsl:when test="error-handler-config/servlet">
      <xsl:for-each select="error-handler-config/servlet">
        <h3><xsl:value-of select="@name"/></h3>
        <div class="table-scroll" tabindex="0" role="group" aria-label="Error handler: {@name}"><table class="summary-table" aria-label="Error handler: {@name}">
          <tbody>
            <tr><th>Mappings</th><td><code><xsl:value-of select="@mappings"/></code></td></tr>
            <tr><th>Problem Details (XML)</th><td><xsl:value-of select="@problem-xml"/></td></tr>
            <tr><th>Detail</th><td><xsl:value-of select="@detail"/></td></tr>
            <tr>
              <th>Forwarded extensions</th>
              <td><code><xsl:value-of select="forward/@extensions"/></code>
                  (<xsl:value-of select="forward/@source"/><xsl:if test="forward/@default-extension">, default <code><xsl:value-of select="forward/@default-extension"/></code></xsl:if>)</td>
            </tr>
            <tr><th>Ignored extensions</th><td><code><xsl:value-of select="ignore/@extensions"/></code></td></tr>
            <tr><th>Stylesheet</th><td>configured: <xsl:value-of select="stylesheet/@configured"/>, selected: <xsl:value-of select="stylesheet/@selected"/></td></tr>
          </tbody>
        </table></div>
      </xsl:for-each>
    </xsl:when>
    <xsl:otherwise><p><i>No Berlioz error handler servlet reported.</i></p></xsl:otherwise>
  </xsl:choose>
  </div>
</xsl:template>

<!-- Configuration check -->
<xsl:template match="content[@name='check-config']" mode="bzadmin-content">
  <section>
  <h2>Configuration check</h2>
  <div class="table-scroll" tabindex="0" role="group" aria-label="Configuration check"><sortable-table>
  <table class="basic-table" aria-label="Configuration check">
    <thead>
      <tr><th>Check</th><th>Status</th><th>Message</th></tr>
    </thead>
    <tbody>
      <tr><td>PageSeeder properties</td><td class="{pageseeder/@status}"><xsl:value-of select="pageseeder/@status"/></td><td><xsl:value-of select="pageseeder/@message"/></td></tr>
      <tr><td>Admin credentials</td><td class="{pageseeder-admin/@status}"><xsl:value-of select="pageseeder-admin/@status"/></td><td><xsl:value-of select="pageseeder-admin/@message"/></td></tr>
    </tbody>
  </table>
  </sortable-table></div>
  </section>
</xsl:template>

<!--
  What a configuration property does, in a popover opened by an info button; nothing when it has
  no explanation. The row header is the sort key, so that the explanation is not part of it.

  @param name The name of the configuration property
-->
<xsl:template name="config-info">
  <xsl:param name="name" as="xs:string"/>
  <xsl:param name="description" as="attribute()?"/>
  <xsl:variable name="explanation" select="$config-explanations/explanation[@name = $name]"/>
  <xsl:if test="$explanation or normalize-space($description)">
    <xsl:variable name="id" select="concat('config-info-', translate($name, '.', '-'))"/>
    <xsl:text> </xsl:text>
    <button type="button" class="info-button link-button" popovertarget="{$id}" aria-label="About {$name}"><svg-icon name="info-circle"/></button>
    <p id="{$id}" class="info-popover" popover="">
      <xsl:choose>
        <xsl:when test="$explanation"><xsl:copy-of select="$explanation/node()"/></xsl:when>
        <xsl:otherwise><xsl:value-of select="$description"/></xsl:otherwise>
      </xsl:choose>
    </p>
  </xsl:if>
</xsl:template>

</xsl:stylesheet>
