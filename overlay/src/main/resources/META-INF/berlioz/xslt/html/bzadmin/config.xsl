<?xml version="1.0"?>
<!--
  Display the Berlioz configuration.

  Contents (see docs/implementation/metadata-contract.md):
  - 'global-config'         GetGlobalConfig: configured global properties (sensitive values redacted) and load status
  - 'berlioz-options'       ListBerliozOptions: options of this Berlioz version with their defaults
  - 'berlioz-config'        GetBerliozConfig: Berlioz servlets (the XSLT processor and servlet container are on the Application page)
  - 'error-handler-config'  GetErrorHandlerConfig: Berlioz error handler servlets
  - 'berlioz-control'       GetBerliozControl: whether the "Reload configuration", "Reload rules" and "Reload all" buttons (berlioz-reload) work
  - 'redirect-filters'      GetRedirectFilters: Berlioz's redirect filters and the rules they loaded (docs/design/redirects.md)
  - 'admin-login'           GetAdminLogin: whether the password of the fallback login is a hash (docs/access.md)
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              exclude-result-prefixes="#all">

<!--
  What the Berlioz options do, from the documentation of `org.pageseeder.berlioz.BerliozOption`
  in Berlioz 0.14.6, as HTML phrasing content shown in a popover by the 'config-info' template.
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

<!--
  Whether a configured option value is the default of the option, so that the property can be
  removed without changing what Berlioz uses. Booleans compare as Berlioz parses them
  (`Boolean.parseBoolean`), when the value is `true` or `false` in any case; other values must be
  identical. Redacted values cannot be compared.

  @param option The Berlioz option
  @param value  The configured value
-->
<xsl:function name="f:config-is-default" as="xs:boolean">
  <xsl:param name="option" as="element(option)"/>
  <xsl:param name="value" as="xs:string"/>
  <xsl:sequence select="if ($value = '[REDACTED]') then false()
    else if ($option/@type = 'boolean') then lower-case($value) = ('true', 'false') and lower-case($value) = string($option/@default)
    else $value = string($option/@default)"/>
</xsl:function>

<!-- The views of the configuration, selected by the 'view' parameter -->
<xsl:template match="content[@name='global-config']" mode="bzadmin-secondary-nav">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=')"/>
    <xsl:with-param name="label"  select="'Global properties'"/>
    <xsl:with-param name="active" select="not($view = ('deployment', 'redirects', 'login'))"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=deployment')"/>
    <xsl:with-param name="label"  select="'Deployment'"/>
    <xsl:with-param name="active" select="$view = 'deployment'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=redirects')"/>
    <xsl:with-param name="label"  select="'Redirects'"/>
    <xsl:with-param name="active" select="$view = 'redirects'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href"   select="concat(/root/header/location/@path, '?view=login')"/>
    <xsl:with-param name="label"  select="'Login'"/>
    <xsl:with-param name="active" select="$view = 'login'"/>
  </xsl:call-template>
</xsl:template>

<!--
  Display the configuration, with a tab for the global properties and one for the deployment
  (servlets and error handler) selected by the 'view' parameter.
-->
<xsl:template match="content[@name='global-config']" mode="bzadmin-content">
  <xsl:variable name="view" select="string(/root/header/http-parameters/parameter[@name = 'view'][1])"/>
  <xsl:variable name="control" select="../content[@name='berlioz-control']/berlioz-control"/>
  <xsl:variable name="disabled" as="node()*">
    <xsl:apply-templates select="$control" mode="bzadmin-control-disabled"/>
  </xsl:variable>
  <!-- Swapped with the view: the Redirects view reloads the rules instead of the configuration -->
  <section id="config-controls" class="control-forms">
    <xsl:choose>
      <xsl:when test="$view = 'redirects'">
        <xsl:call-template name="bzadmin-control-form">
          <xsl:with-param name="id"       select="'reload-redirects'"/>
          <xsl:with-param name="path"     select="'/bzadmin/api/redirects/reload'"/>
          <xsl:with-param name="scope"    select="'redirects'"/>
          <xsl:with-param name="label"    select="'Reload rules'"/>
          <xsl:with-param name="icon"     select="'refresh'"/>
          <xsl:with-param name="disabled" as="node()*">
            <xsl:sequence select="$disabled"/>
            <xsl:if test="empty($disabled) and not(../content[@name='redirect-filters']/redirect-filters/filter[@status = ('not-loaded', 'failed', 'loaded')])">No filter with a <code>config</code> file to reload</xsl:if>
          </xsl:with-param>
          <xsl:with-param name="description" as="node()*">Reloads the rules of every redirect and relocation filter, on the next request through each, without reloading the configuration or the services.</xsl:with-param>
        </xsl:call-template>
      </xsl:when>
      <xsl:otherwise>
        <xsl:call-template name="bzadmin-control-form">
          <xsl:with-param name="id"       select="'reload-configuration'"/>
          <xsl:with-param name="path"     select="'/bzadmin/api/config/reload'"/>
          <xsl:with-param name="scope"    select="'config'"/>
          <xsl:with-param name="label"    select="'Reload configuration'"/>
          <xsl:with-param name="icon"     select="'refresh'"/>
          <xsl:with-param name="disabled" select="$disabled"/>
          <xsl:with-param name="description" as="node()*">Reloads the global configuration only. Berlioz keeps the last valid configuration if the file fails to load.</xsl:with-param>
        </xsl:call-template>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:call-template name="bzadmin-control-form">
      <xsl:with-param name="id"       select="'reload-all'"/>
      <xsl:with-param name="path"     select="'/bzadmin/api/reload'"/>
      <xsl:with-param name="scope"    select="'config,services,xslt,redirects'"/>
      <xsl:with-param name="label"    select="'Reload all'"/>
      <xsl:with-param name="icon"     select="'refresh'"/>
      <xsl:with-param name="class"    select="'button alert'"/>
      <xsl:with-param name="disabled" select="$disabled"/>
      <xsl:with-param name="description" as="node()*">Reloads the configuration and the services, clears the XSLT cache and reloads the redirect rules, so that they stay consistent.
        The ETags are kept: a response whose content changed with the configuration or the services, but not the ETags of its generators, can still be revalidated by clients until the ETags are reset from the Services page.</xsl:with-param>
    </xsl:call-template>
  </section>
  <section id="config-details">
    <xsl:if test="$view != 'login'">
      <xsl:apply-templates select="../content[@name='admin-login']/admin-login" mode="config-login-notice"/>
    </xsl:if>
    <xsl:choose>
      <xsl:when test="$view = 'deployment'">
        <xsl:apply-templates select="//content[@name='berlioz-config']" mode="bzadmin-config"/>
        <xsl:apply-templates select="//content[@name='error-handler-config']" mode="bzadmin-config"/>
      </xsl:when>
      <xsl:when test="$view = 'redirects'">
        <xsl:apply-templates select="//content[@name='redirect-filters']" mode="bzadmin-config"/>
      </xsl:when>
      <xsl:when test="$view = 'login'">
        <xsl:apply-templates select="//content[@name='admin-login']" mode="bzadmin-config"/>
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
  <xsl:apply-templates select="properties/@load-status" mode="bzadmin-config"/>
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
    <xsl:if test="properties/@loaded">Loaded <xsl:sequence select="f:datetime-diff(xs:dateTime(properties/@loaded))"/>. </xsl:if>
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
              <xsl:when test="$configured">
                <code><xsl:sequence select="f:redacted($configured/@value)"/></code>
                <xsl:if test="f:config-is-default(., $configured/@value)"><xsl:text> </xsl:text><span class="tag" title="Configured with the default value: the property can be removed">same as default</span></xsl:if>
              </xsl:when>
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
  The mode, the Berlioz options configured (deprecated ones call for attention), those configured
  with their default value and the other properties

  @param properties The configured global properties
  @param options    The options of this Berlioz version
-->
<!--
  Status of the global configuration, when the last load failed, with the file that failed (Berlioz
  0.14.6). Berlioz only reports the line and the parser's message when 'berlioz.errors.detail' is not
  'minimal'.
-->
<xsl:template match="@load-status" mode="bzadmin-config">
  <xsl:choose>
    <xsl:when test=". = 'reload-failed'">
      <div class="alert callout" role="alert">
        <p><svg-icon name="warning"/> <b>The last reload of the global configuration failed<xsl:apply-templates select="../@failed" mode="bzadmin-config"/>.</b>
        Berlioz keeps the last valid configuration, listed below.</p>
        <xsl:apply-templates select="../load-failure" mode="bzadmin-config"/>
      </div>
    </xsl:when>
    <xsl:when test=". = 'startup-failed'">
      <div class="alert callout" role="alert">
        <p><svg-icon name="warning"/> <b>The global configuration could not be loaded<xsl:apply-templates select="../@failed" mode="bzadmin-config"/>.</b>
        Berlioz uses the default value of every option.</p>
        <xsl:apply-templates select="../load-failure" mode="bzadmin-config"/>
      </div>
    </xsl:when>
  </xsl:choose>
</xsl:template>

<!-- When the last load failed -->
<xsl:template match="@failed" mode="bzadmin-config">
  <xsl:text> </xsl:text><xsl:sequence select="f:datetime-diff(xs:dateTime(.))"/>
</xsl:template>

<!-- The cause of the last failed load: the file, and the line and message when Berlioz reports them -->
<xsl:template match="load-failure" mode="bzadmin-config">
  <p class="config-load-failure">
    <xsl:if test="@source">In <code><xsl:value-of select="@source"/></code><xsl:if test="@line">, line <xsl:value-of select="@line"/></xsl:if>: </xsl:if>
    <xsl:value-of select="."/>
    <xsl:if test="not(@line)">. See the logs for the cause.</xsl:if>
  </p>
</xsl:template>

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
          <tr><th scope="row" data-sort="{@name}"><xsl:value-of select="@name"/><xsl:call-template name="config-info"><xsl:with-param name="name" select="@name"/></xsl:call-template></th><td><code><xsl:sequence select="f:redacted(@value)"/></code></td></tr>
        </xsl:for-each>
        </tbody>
      </table>
      </sortable-table></div>
    </xsl:for-each-group>
  </xsl:if>
</xsl:template>

<!-- Displayed by the global properties and the deployment tabs -->
<xsl:template match="content[@name=('berlioz-options', 'berlioz-config', 'error-handler-config', 'redirect-filters', 'admin-login')]" mode="bzadmin-content"/>

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
            <xsl:variable name="servlet" select="@name"/>
            <tr><th>Mappings<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'mappings'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th><td><code><xsl:value-of select="@mappings"/></code></td></tr>
            <tr><th>Problem Details (XML)<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'problem-xml'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th><td><xsl:value-of select="@problem-xml"/></td></tr>
            <tr><th>Detail<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'detail'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th><td><xsl:value-of select="@detail"/></td></tr>
            <tr>
              <th>Forwarded extensions<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'forward'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th>
              <td><code><xsl:value-of select="forward/@extensions"/></code>
                  (<xsl:value-of select="forward/@source"/><xsl:if test="forward/@default-extension">, default <code><xsl:value-of select="forward/@default-extension"/></code></xsl:if>)</td>
            </tr>
            <tr><th>Ignored extensions<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'ignore'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th><td><code><xsl:value-of select="ignore/@extensions"/></code></td></tr>
            <tr><th>Stylesheet<xsl:call-template name="error-handler-info"><xsl:with-param name="field" select="'stylesheet'"/><xsl:with-param name="servlet" select="$servlet"/></xsl:call-template></th><td>configured: <xsl:value-of select="stylesheet/@configured"/>, selected: <xsl:value-of select="stylesheet/@selected"/></td></tr>
          </tbody>
        </table></div>
      </xsl:for-each>
    </xsl:when>
    <xsl:otherwise><p><i>No Berlioz error handler servlet reported.</i></p></xsl:otherwise>
  </xsl:choose>
  </div>
</xsl:template>

<!--
  What a field of the error handler means, from `org.pageseeder.berlioz.servlet.ErrorHandlerServlet`
  in Berlioz 0.14.6, as HTML phrasing content shown in a popover by the 'config-info' template.
-->
<xsl:variable name="error-handler-explanations">
  <explanation name="mappings">URL patterns of this servlet in <code>web.xml</code>. The container sends errors to it through the <code>&lt;error-page&gt;</code> locations; these patterns are not error-page registrations. An error page ending in <code>.auto</code> is forwarded to the extension of the failed request.</explanation>
  <explanation name="problem-xml"><code>true</code> for RFC 9457 <code>&lt;problem&gt;</code> XML (and HTML made from it); <code>false</code> for the legacy <code>&lt;error&gt;</code> format. JSON errors are always <code>application/problem+json</code>. Set by <code>berlioz.errors.problem</code>.</explanation>
  <explanation name="detail">Diagnostic detail in error responses: <code>minimal</code> (for production), <code>standard</code> (exception class and message) or <code>full</code> (stack trace, headers and parameters, for development only). Set by <code>berlioz.errors.detail</code>.</explanation>
  <explanation name="forward">Extensions that an <code>.auto</code> error page keeps from the failed request; any other extension becomes the default. The source is <code>discovered</code> (extension mappings of the Berlioz servlets), <code>explicit</code> (<code>forward-extensions</code> init parameter, default from <code>forward-default</code>) or <code>legacy-default</code> (<code>html</code> and <code>xml</code>, when discovery is unavailable).</explanation>
  <explanation name="ignore">Requests with these extensions (images, styles, scripts, fonts, media) get the error status with an empty body instead of an error page. Set by the <code>ignore-extensions</code> init parameter.</explanation>
  <explanation name="stylesheet">Stylesheet that renders HTML error pages: <code>custom</code> when <code>berlioz.errors.stylesheet</code> names a file in <code>WEB-INF</code>, otherwise <code>built-in</code> (the fail-safe template). The selected one differs from the configured one when the custom stylesheet cannot be found; <code>none</code> returns XML.</explanation>
</xsl:variable>

<!--
  The info button of a field of an error handler servlet.

  @param field   The name of the field in 'error-handler-explanations'
  @param servlet The name of the error handler servlet, for a unique ID
-->
<xsl:template name="error-handler-info">
  <xsl:param name="field" as="xs:string"/>
  <xsl:param name="servlet" as="xs:string"/>
  <xsl:call-template name="config-info">
    <xsl:with-param name="name" select="$field"/>
    <xsl:with-param name="explanations" select="$error-handler-explanations"/>
    <xsl:with-param name="id" select="concat('error-handler-info-', replace($servlet, '[^A-Za-z0-9_-]', '-'), '-', $field)"/>
  </xsl:call-template>
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
  A notice above the other views when the password of the fallback login is not a valid hash
-->
<xsl:template match="admin-login[@password = 'plain']" mode="config-login-notice">
  <div class="warning callout config-login-notice">
    <p><svg-icon name="warning"/> The password of the fallback login (<code>berlioz.admin.password</code>) is not hashed: use a hash outside local development.
      <a href="{/root/header/location/@path}?view=login">Make a password hash</a></p>
  </div>
</xsl:template>
<xsl:template match="admin-login[@password = 'invalid']" mode="config-login-notice">
  <div class="alert callout config-login-notice" role="alert">
    <p><svg-icon name="warning"/> The password of the fallback login (<code>berlioz.admin.password</code>) is not a valid PBKDF2 hash: the fallback login denies access.
      <a href="{/root/header/location/@path}?view=login">Make a password hash</a></p>
  </div>
</xsl:template>
<xsl:template match="admin-login" mode="config-login-notice"/>

<!--
  The Login view: how the fallback login is configured, and a form to make a password hash for it
  (bzadmin-config.js posts it to the internal API). The page never shows the password or its hash.
-->
<xsl:template match="content[@name='admin-login']" mode="bzadmin-config">
  <xsl:variable name="login" select="admin-login"/>
  <xsl:variable name="mode" select="string(/root/header/berlioz/@mode)"/>
  <xsl:variable name="file" select="if ($mode != '') then concat('config-', $mode, '.xml') else 'config-&lt;mode&gt;.xml'"/>
  <xsl:variable name="authorized" select="../content[@name='berlioz-control']/berlioz-control/@authorized = 'true'"/>
  <div class="panel">
    <h2>Fallback login</h2>
    <xsl:choose>
      <xsl:when test="$login/@password = 'hash'">
        <p><svg-icon name="ok"/> The fallback login of <b><xsl:value-of select="$login/@username"/></b> has a password hash.</p>
      </xsl:when>
      <xsl:when test="$login/@password = 'plain'">
        <div class="warning callout">
          <p><svg-icon name="warning"/> The fallback login of <b><xsl:value-of select="$login/@username"/></b> has the password itself, not a hash. Use it for local development only: anywhere else, replace it with a hash.</p>
        </div>
      </xsl:when>
      <xsl:when test="$login/@password = 'invalid'">
        <div class="alert callout" role="alert">
          <p><svg-icon name="warning"/> The password of the fallback login looks like a hash, but is not a valid PBKDF2 hash: the fallback login denies access. Replace it with a new hash.</p>
        </div>
      </xsl:when>
      <xsl:when test="$login/@password = 'incomplete'">
        <div class="alert callout" role="alert">
          <p><svg-icon name="warning"/> The fallback login is not used: it needs a user name without <code>:</code> (<code>berlioz.admin.username</code>) and a password (<code>berlioz.admin.password</code>).</p>
        </div>
      </xsl:when>
      <xsl:otherwise>
        <p>No fallback login is configured: the application grants access to Berlioz administration itself.
          A fallback login, with a BASIC challenge, needs a user name and a password in the global configuration.</p>
      </xsl:otherwise>
    </xsl:choose>
    <p class="muted">The fallback login is accepted over HTTPS, or over plain HTTP from this machine only. The page never shows the password or its hash.</p>
  </div>
  <div class="panel">
    <h2>Make a password hash</h2>
    <p>Type the password twice to get its hash (PBKDF2-HMAC-SHA256, 600,000 iterations), and put the hash in place of the password in <code><xsl:value-of select="$file"/></code>.
      The server computes the hash and keeps neither the password nor the hash.</p>
    <form id="password-hash" class="password-hash-form" method="post" action="{f:xml-url('/bzadmin/api/config/password/hash')}"
          data-username="{($login/@username, 'admin')[1]}" autocomplete="off">
      <label for="password-hash-password">Password</label>
      <input id="password-hash-password" class="password-hash-field" type="password" name="password" required="required"
             maxlength="1024" autocomplete="new-password" spellcheck="false"/>
      <label for="password-hash-confirm">Password again</label>
      <input id="password-hash-confirm" class="password-hash-field" type="password" name="confirm" required="required"
             maxlength="1024" autocomplete="new-password" spellcheck="false"/>
      <p>
        <xsl:choose>
          <xsl:when test="$authorized">
            <button type="submit" class="button">Make the hash</button>
          </xsl:when>
          <xsl:otherwise>
            <button type="submit" class="button hollow" disabled="disabled" aria-describedby="password-hash-disabled">Make the hash</button>
            <br/><small id="password-hash-disabled">Requires the permission to operate Berlioz administration</small>
          </xsl:otherwise>
        </xsl:choose>
      </p>
    </form>
    <div id="password-hash-result" class="password-hash-result" hidden="hidden">
      <p>In <code><xsl:value-of select="$file"/></code>:</p>
      <pre><code id="password-hash-config"></code></pre>
      <p><button type="button" id="password-hash-copy" class="button hollow">Copy</button>
        <xsl:text> </xsl:text><span class="muted">Each hash has a random salt, so the same password never gives the same hash.</span></p>
    </div>
    <p class="muted">Or, without a running application, in a terminal:
      <code>java -cp pso-bzadmin-&lt;version&gt;.jar org.pageseeder.bzadmin.access.AdminPassword</code></p>
  </div>
</xsl:template>

<!--
  What a configuration property does, in a popover opened by an info button; nothing when it has
  no explanation. The row header is the sort key, so that the explanation is not part of it.

  @param name         The name of the configuration property, or of the explained field
  @param explanations The explanations to look the name up in (the Berlioz options by default)
  @param id           The ID of the popover, unique in the page
-->
<xsl:template name="config-info">
  <xsl:param name="name" as="xs:string"/>
  <xsl:param name="description" as="attribute()?"/>
  <xsl:param name="explanations" select="$config-explanations"/>
  <xsl:param name="id" select="concat('config-info-', translate($name, '.', '-'))" as="xs:string"/>
  <xsl:variable name="explanation" select="$explanations/explanation[@name = $name]"/>
  <xsl:if test="$explanation or normalize-space($description)">
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

<!--
  Display Berlioz's redirect filters (RedirectFilter, RelocationFilter): a panel per registration
  with its status and the rules it loaded, as text, never links (docs/design/redirects.md).
-->
<xsl:template match="content[@name='redirect-filters']" mode="bzadmin-config">
  <xsl:variable name="filters" select="redirect-filters"/>
  <p>The rules shown are those each filter loaded, in the order of its file. A filter loads its rules on its first request, and again on its next request after <b>Reload rules</b> or <b>Reload all</b>. Patterns match the request path, including the context path. Passwords and query parameter values in targets are redacted.</p>
  <xsl:choose>
    <xsl:when test="$filters/@context = 'unavailable'">
      <p><i>The filters of the application are not available for this request.</i></p>
    </xsl:when>
    <xsl:when test="not($filters/filter[@status != 'not-mapped'])">
      <h2>No redirect filter</h2>
      <div class="panel">
        <p>The application maps neither Berlioz filter. <code>RedirectFilter</code> redirects URLs (301, 302, 307 or 308) and <code>RelocationFilter</code> forwards them internally, each with the rules of the file named by its <code>config</code> parameter, relative to <code>WEB-INF</code>.</p>
      </div>
    </xsl:when>
    <xsl:otherwise>
      <xsl:apply-templates select="$filters/filter" mode="bzadmin-config"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!-- The label of a filter type -->
<xsl:function name="f:redirect-type" as="xs:string">
  <xsl:param name="type" as="xs:string"/>
  <xsl:sequence select="if ($type = 'relocation') then 'Forwards (RelocationFilter)' else 'Redirects (RedirectFilter)'"/>
</xsl:function>

<!-- A type of filter that the application does not map -->
<xsl:template match="filter[@status = 'not-mapped']" mode="bzadmin-config">
  <section>
    <h2><xsl:value-of select="f:redirect-type(@type)"/></h2>
    <div class="panel">
      <p class="muted">Not mapped.</p>
    </div>
  </section>
</xsl:template>

<!-- A registration of a filter -->
<xsl:template match="filter" mode="bzadmin-config">
  <xsl:variable name="label" select="concat(f:redirect-type(@type), ': ', @name)"/>
  <section>
  <h2><xsl:value-of select="$label"/></h2>
  <div class="panel">
    <div class="table-scroll" tabindex="0" role="group" aria-label="{$label}"><table class="summary-table" aria-label="{$label}">
      <tbody>
        <tr>
          <th scope="row">Status</th>
          <td>
            <span class="{if (@status = ('loaded', 'not-loaded')) then 'tag' else 'tag warning'}"><xsl:value-of select="@status"/></span>
            <xsl:text> </xsl:text>
            <xsl:apply-templates select="." mode="redirect-status"/>
          </td>
        </tr>
        <tr>
          <th scope="row">Configuration</th>
          <td><xsl:choose><xsl:when test="@config"><code><xsl:value-of select="@config"/></code> in <code>WEB-INF</code></xsl:when><xsl:otherwise><span class="muted">No <code>config</code> parameter</span></xsl:otherwise></xsl:choose></td>
        </tr>
        <tr>
          <th scope="row">Mapped to</th>
          <td><xsl:value-of select="string-join((mapping/@url-pattern, mapping/@servlet-name/concat('servlet ', .)), ', ')"/></td>
        </tr>
        <xsl:if test="@loaded">
          <tr>
            <th scope="row">Loaded</th>
            <td>
              <xsl:sequence select="f:datetime-diff(xs:dateTime(@loaded))"/>
              <xsl:if test="@reload-pending = 'true'"><br/><small>A reload was requested: the filter loads its rules again on its next request.</small></xsl:if>
            </td>
          </tr>
        </xsl:if>
        <xsl:if test="@rules">
          <tr>
            <th scope="row">Rules</th>
            <td>
              <xsl:value-of select="@rules"/>
              <xsl:if test="@ignored">, and <xsl:value-of select="@ignored"/> ignored</xsl:if>
              <xsl:if test="@limit"> (the first <xsl:value-of select="@limit"/> of each listed)</xsl:if>
            </td>
          </tr>
        </xsl:if>
      </tbody>
    </table></div>
  </div>
    <xsl:if test="rule">
      <div class="table-scroll" tabindex="0" role="group" aria-label="Rules: {@name}"><table class="basic-table redirect-rules is-panel" aria-label="Rules: {@name}">
        <thead>
          <tr>
            <th scope="col">#</th>
            <th scope="col">From</th>
            <th scope="col">To</th>
            <th scope="col">Effect</th>
          </tr>
        </thead>
        <tbody>
          <xsl:for-each select="rule">
            <tr>
              <td><xsl:value-of select="@position"/></td>
              <td><code><xsl:sequence select="f:redirect-uri(@from)"/></code></td>
              <td><code><xsl:sequence select="f:redirect-uri(@to)"/></code></td>
              <td>
                <xsl:value-of select="if (not(@effect)) then '' else if (@effect = 'forward') then 'Forward' else concat(@effect, if (@effect = ('301', '308')) then ' permanent' else ' temporary')"/>
                <xsl:apply-templates select="warning" mode="redirect-warning"/>
              </td>
            </tr>
          </xsl:for-each>
        </tbody>
      </table></div>
    </xsl:if>
  </section>
</xsl:template>

<!--
  A pattern or target of a rule, with its URI template tokens ({path}, {+path}) and the parts
  redacted by the generator ([REDACTED]) highlighted.
-->
<xsl:function name="f:redirect-uri" as="node()*">
  <xsl:param name="uri" as="xs:string?"/>
  <xsl:analyze-string select="string($uri)" regex="\{{[^{{}}]*\}}|\[REDACTED\]">
    <xsl:matching-substring>
      <span class="{if (starts-with(., '[')) then 'redacted' else 'redirect-token'}"><xsl:value-of select="."/></span>
    </xsl:matching-substring>
    <xsl:non-matching-substring><xsl:value-of select="."/></xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:function>

<!-- What a status means for the filter -->
<xsl:template match="filter" mode="redirect-status">
  <xsl:choose>
    <xsl:when test="@status = 'loaded' and @rules = '0'">The file has no rule: the filter has no effect.</xsl:when>
    <xsl:when test="@status = 'loaded'">Berlioz applies these rules.</xsl:when>
    <xsl:when test="@status = 'not-loaded'">The filter loads its rules on its first request<xsl:if test="@exists = 'false'">, but the file does not exist</xsl:if>.</xsl:when>
    <xsl:when test="@status = 'no-config'">Without a <code>config</code> parameter, the filter has no effect.</xsl:when>
    <xsl:when test="@status = 'failed'">
      <xsl:value-of select="if (@exists = 'false') then 'The file does not exist or is not a file' else 'Berlioz could not load the file'"/>
      <xsl:text>: the filter has no effect until it is fixed and reloaded.</xsl:text>
      <xsl:if test="@failure"><br/><small><xsl:value-of select="@failure"/></small></xsl:if>
    </xsl:when>
    <xsl:when test="@status = 'not-initialized'">The container did not initialize this filter: see the log for its startup error.</xsl:when>
  </xsl:choose>
</xsl:template>

<!-- A warning of a rule -->
<xsl:template match="warning" mode="redirect-warning">
  <br/>
  <span class="tag warning">
    <xsl:choose>
      <xsl:when test="@code = 'ignored-rule'"><xsl:attribute name="title" select="concat(if (starts-with(@reason, 'missing')) then 'A pattern is missing' else 'A pattern does not parse', ': Berlioz ignores this rule')"/>ignored</xsl:when>
      <xsl:when test="@code = 'duplicate-from'"><xsl:attribute name="title">Rule <xsl:value-of select="@position"/> has the same pattern and applies first</xsl:attribute>same as #<xsl:value-of select="@position"/></xsl:when>
      <xsl:when test="@code = 'redirect-chain'"><xsl:attribute name="title" select="concat('The filter redirects the target again', if (@position) then concat(', with rule ', @position) else '', ': the browser makes one more request')"/><xsl:value-of select="if (@position) then concat('redirected again by #', @position) else 'redirected again'"/></xsl:when>
      <xsl:when test="@code = 'redirect-loop'"><xsl:attribute name="title" select="concat('Following the redirects', if (@position) then concat(' from rule ', @position) else '', ' comes back to a rule already followed: the browser stops with a too many redirects error, and keeps following them for a day after a fix')"/><xsl:value-of select="if (@position) then concat('redirect loop via #', @position) else 'redirect loop'"/></xsl:when>
      <xsl:when test="@code = 'forwards-to-admin'"><xsl:attribute name="title">Container security constraints do not apply to forwards, and AdminFilter checks them only when mapped with FORWARD: the admin page is denied</xsl:attribute>forwards to admin</xsl:when>
      <xsl:otherwise><xsl:value-of select="@code"/></xsl:otherwise>
    </xsl:choose>
  </span>
</xsl:template>

</xsl:stylesheet>
