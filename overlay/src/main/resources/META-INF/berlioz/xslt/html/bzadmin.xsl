<?xml version="1.0" encoding="UTF-8"?>
<!--
  Templates for Berlioz Administration.

  This group is reserved and provide online access to the Berlioz configuration.
-->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                exclude-result-prefixes="#all">

<!-- Functions used in Berlioz Admin -->
<xsl:import href="bzadmin/_functions.xsl"/>

<!-- Standard Berlioz Admin tools -->
<xsl:include href="bzadmin/config.xsl"/>
<xsl:include href="bzadmin/services.xsl"/>
<xsl:include href="bzadmin/cache.xsl"/>
<xsl:include href="bzadmin/files.xsl"/>
<xsl:include href="bzadmin/xslt.xsl"/>
<xsl:include href="bzadmin/application.xsl"/>
<xsl:include href="bzadmin/system.xsl"/>
<xsl:include href="bzadmin/logs.xsl"/>

<!-- General Output properties. -->
<xsl:output method="html" encoding="utf-8" indent="no" media-type="text/html" version="5.0"/>

<!-- Berlioz administrator version -->
<xsl:variable name="bzadmin-version" select="'@VERSION@'"/>

<!-- Application version -->
<xsl:variable name="application-version" select="/root/header/app/@version"/>

<!--
  Main template called in all cases.
-->
<xsl:template match="/root">
<xsl:variable name="context" select="header/path/@context"/>
<xsl:variable name="nonce" select="header/security/@nonce"/>
<html lang="en">
<head>
  <title><xsl:value-of select="f:to-page-title(@service)"/> | Berlioz administration</title>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <meta name="author" content="Allette Systems"/>
  <meta name="copyright" content="Copyright 2013-{format-date(current-date(), '[Y]')} Allette Systems"/>
  <link rel="preload" href="{$context}/js/{@group}-{$bzadmin-version}.min.js" as="script">
    <xsl:if test="$nonce"><xsl:attribute name="nonce" select="$nonce"/></xsl:if>
  </link>
<!--  <xsl:if test="not(header/location/@port = '9000')">-->
    <link rel="stylesheet" href="{$context}/css/{@group}-{$bzadmin-version}.min.css" type="text/css">
      <xsl:if test="$nonce"><xsl:attribute name="nonce" select="$nonce"/></xsl:if>
    </link>
<!--  </xsl:if>-->
</head>
<body class="page-{@service}">
  <a class="skip-link" href="#main-content">Skip to content</a>
  <header class="header">
    <div class="header-inner">
      <div class="header-title">
        <p class="site-title">Berlioz administration</p>
        <p class="site-location"><xsl:apply-templates select="header/location" mode="header"/></p>
      </div>
      <div class="header-meta">
        <xsl:variable name="now" select="current-dateTime()"/>
        <div class="server-time">Server time: <time datetime="{$now}"><b><xsl:value-of select="format-dateTime($now, '[H01]:[m01]:[s01]')"/></b>
          <xsl:text> on </xsl:text>
          <xsl:value-of select="format-dateTime($now, '[D1] [MNn,*-3] [Y0001] [Z]')"/></time></div>
      </div>
      <div class="header-nav">
        <xsl:apply-templates select="." mode="navigation"/>
      </div>
    </div>
  </header>
  <main class="main" id="main-content" tabindex="-1">
    <article class="article">
      <xsl:call-template name="bzadmin-page-header"/>
      <xsl:apply-templates select="//content[@target='main']" mode="bzadmin-content"/>
    </article>
    <xsl:if test="starts-with(@service, 'bzadmin-system')"><xsl:call-template name="system-info-popovers"/></xsl:if>
  </main>
  <footer class="footer">
    <p>Berlioz <xsl:value-of select="header/berlioz/@version"/><xsl:if test="string($application-version)"> - App version <xsl:value-of select="$application-version"/></xsl:if></p>
  </footer>
  <script src="{$context}/js/bzadmin-{$bzadmin-version}.min.js">
    <xsl:if test="$nonce"><xsl:attribute name="nonce" select="$nonce"/></xsl:if>
  </script>
</body>
</html>
</xsl:template>

<!--
  Denied by the generators (see org.pageseeder.bzadmin.access.AdminGuard): a page with no content,
  the same whatever the reason, with no configuration detail. A denied generator writes nothing:
  forbidden content with an element is an expected failure of an allowed one (an unreadable file).
-->
<xsl:template match="/root[content[@status = 'forbidden'][not(*)]]" priority="1">
<html lang="en">
<head>
  <title>Berlioz administration</title>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
</head>
<body class="bzadmin-denied">
  <main><h1>Administration access unavailable</h1>
  <p>Access to Berlioz administration is not configured for this request.</p></main>
</body>
</html>
</xsl:template>

<!--
  Display the primary navigation (displayed just under the header)
-->
<xsl:template match="root[@group='bzadmin']" mode="navigation">
<xsl:variable name="service"  select="@service"/>
<nav class="primary-nav" aria-label="Administration">
  <ul>
    <li><xsl:if test="starts-with($service, 'bzadmin-config')"  ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/config')}"><xsl:if test="starts-with($service, 'bzadmin-config')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>Config</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-services')"><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/services')}"><xsl:if test="starts-with($service, 'bzadmin-services')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>Services</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-cache')"   ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/cache')}"><xsl:if test="starts-with($service, 'bzadmin-cache')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>Cache</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-files')"   ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/files')}"><xsl:if test="starts-with($service, 'bzadmin-files')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>PSML</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-xslt')"    ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/xslt')}"><xsl:if test="starts-with($service, 'bzadmin-xslt')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>XSLT</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-application')"><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/application')}"><xsl:if test="starts-with($service, 'bzadmin-application')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>Application</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-logs')"    ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/logs')}"><xsl:if test="starts-with($service, 'bzadmin-logs')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>Logs</a></li>
    <li><xsl:if test="starts-with($service, 'bzadmin-system')"  ><xsl:attribute name="class">active</xsl:attribute></xsl:if><a href="{f:to-path('/bzadmin/system')}"><xsl:if test="starts-with($service, 'bzadmin-system')"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>System</a></li>
  </ul>
</nav>
</xsl:template>

<!--
  The title of the page, with the secondary navigation of the page, if any, on the same line.
-->
<xsl:template name="bzadmin-page-header">
  <xsl:variable name="links" as="element()*">
    <xsl:apply-templates select="//content[@target='main']" mode="bzadmin-secondary-nav"/>
  </xsl:variable>
  <header class="page-header">
    <h1 class="page-title"><xsl:value-of select="f:to-page-title(@service)"/></h1>
    <xsl:if test="exists($links)">
      <nav class="secondary-nav" aria-label="Views">
        <ul><xsl:for-each select="$links"><li><xsl:sequence select="."/></li></xsl:for-each></ul>
      </nav>
    </xsl:if>
  </header>
</xsl:template>

<!--
  The links of the secondary navigation: a page with views overrides this mode for its content
  and returns one link per view (see bzadmin-secondary-link).
-->
<xsl:template match="content" mode="bzadmin-secondary-nav"/>

<!--
  A link of the secondary navigation.

  @param href   The URL of the view
  @param label  The label of the link
  @param active Whether it is the current view
-->
<xsl:template name="bzadmin-secondary-link">
  <xsl:param name="href"   as="xs:string"/>
  <xsl:param name="label"  as="xs:string"/>
  <xsl:param name="active" as="xs:boolean"/>
  <a href="{$href}" class="choice{if ($active) then ' active' else ''}">
    <xsl:if test="$active"><xsl:attribute name="aria-current">page</xsl:attribute></xsl:if>
    <xsl:value-of select="$label"/>
  </a>
</xsl:template>

<!--
  A key metric of a row of key metrics (`<dl class="key-metrics">`): a large value (muted when
  unavailable or zero), an optional meter and a detail.

  @param label      The label of the metric
  @param value      The value, empty when it is not available
  @param zero-muted Whether a zero value is muted
  @param meter      A <meter> for the value; optional
  @param detail     What the value is made of, or compared with; optional
  @param info       Nodes after the label, such as an info button; optional
  @param id         The id of the metric, for the page to update it; optional
  @param warning    Whether the value calls for attention
-->
<xsl:template name="bzadmin-key-metric">
  <xsl:param name="label" as="xs:string"/>
  <xsl:param name="value" as="xs:string?"/>
  <xsl:param name="status-icon" as="xs:string?"/>
  <xsl:param name="zero-muted" as="xs:boolean" select="true()"/>
  <xsl:param name="meter" as="element()?"/>
  <xsl:param name="detail" as="xs:string?"/>
  <xsl:param name="info" as="node()*"/>
  <xsl:param name="id" as="xs:string?"/>
  <xsl:param name="warning" as="xs:boolean" select="false()"/>
  <xsl:variable name="label-id" select="concat('metric-label-', if ($id) then $id else generate-id($meter))"/>
  <div class="key-metric{if ($warning) then ' warning' else ''}">
    <xsl:if test="$id"><xsl:attribute name="id" select="$id"/></xsl:if>
    <dt>
      <xsl:choose>
        <xsl:when test="$meter"><span id="{$label-id}"><xsl:value-of select="$label"/></span></xsl:when>
        <xsl:otherwise><xsl:value-of select="$label"/></xsl:otherwise>
      </xsl:choose>
      <xsl:sequence select="$info"/>
    </dt>
    <xsl:choose>
      <xsl:when test="not($value)"><dd class="key-metric-value muted" title="Not available"><span role="img" aria-label="Not available">—</span></dd></xsl:when>
      <xsl:otherwise>
        <dd class="key-metric-value{if ($zero-muted and matches($value, '^0([^.\d]|$)')) then ' muted' else ''}"><xsl:if test="$status-icon"><svg-icon name="{$status-icon}" class="metric-status-icon" aria-hidden="true"/><xsl:text> </xsl:text></xsl:if><xsl:value-of select="$value"/></dd>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="$meter">
      <dd class="key-metric-meter">
        <xsl:for-each select="$meter">
          <xsl:copy>
            <xsl:copy-of select="@* except @aria-labelledby"/>
            <xsl:attribute name="aria-labelledby" select="$label-id"/>
            <xsl:copy-of select="node()"/>
          </xsl:copy>
        </xsl:for-each>
      </dd>
    </xsl:if>
    <xsl:if test="$detail"><dd class="key-metric-detail"><xsl:value-of select="$detail"/></dd></xsl:if>
  </div>
</xsl:template>

<!--
  The location of the website (displayed in the header)
-->
<xsl:template match="header/location" mode="header">
  <a href="{if (/root/header/path/@context) then /root/header/path/@context else '/'}" title="Return to website">
    <xsl:value-of select="@scheme"/>
    <xsl:text>://</xsl:text>
    <var><xsl:value-of select="@host"/></var>
    <xsl:if test="(@scheme = 'http' and @port != '80') or (@scheme = 'https' and @port != '443')">
      <xsl:text>:</xsl:text><xsl:value-of select="@port"/>
    </xsl:if>
     <xsl:if test="/root/header/path/@context">
      <xsl:value-of select="/root/header/path/@context"/>
    </xsl:if>
  </a>
</xsl:template>

<!-- Used by the pages with a Berlioz control button -->
<xsl:template match="content[@name='berlioz-control']" mode="bzadmin-content"/>

<!--
  A button that posts the Berlioz 'berlioz-reload' control parameter to an overlay service that runs GetBerliozControl,
  whose response tells the frontend whether Berlioz applied it. The button is always shown, and
  disabled with the reason when it would have no effect.

  @param id       The id of the form
  @param path     The path of the service, e.g. '/bzadmin/xslt/cache/clear'
  @param scope    The scopes of 'berlioz-reload' (see AdminFilter.CONTROL_SERVICES), e.g. 'xslt', or 'true' for all
  @param label    The label of the button
  @param icon     The name of the icon
  @param class    The classes of an enabled button
  @param disabled Why the button is disabled; empty when it is enabled
  @param disabled-description-id Existing explanation to reference instead of repeating the disabled reason; optional
  @param description What the button does, in a popover opened by an info button; optional
-->
<xsl:template name="bzadmin-control-form">
  <xsl:param name="id"       as="xs:string"/>
  <xsl:param name="path"     as="xs:string"/>
  <xsl:param name="scope"    as="xs:string"/>
  <xsl:param name="label"    as="xs:string"/>
  <xsl:param name="icon"     as="xs:string"/>
  <xsl:param name="class"    as="xs:string" select="'button success'"/>
  <xsl:param name="disabled" as="node()*"/>
  <xsl:param name="disabled-description-id" as="xs:string" select="''"/>
  <xsl:param name="description" as="node()*"/>
  <xsl:variable name="described-by" select="string-join((if (exists($disabled)) then (if ($disabled-description-id != '') then $disabled-description-id else concat($id, '-disabled')) else (), if (exists($description)) then concat($id, '-description') else ()), ' ')"/>
  <form id="{$id}" class="control-form" method="post" action="{concat($bzheader/path/@context, $bzheader/path/@prefix, $path, '.xml')}">
    <input type="hidden" name="berlioz-reload" value="{$scope}"/>
    <p>
      <xsl:choose>
        <xsl:when test="empty($disabled)">
          <button type="submit" class="{$class}">
            <xsl:if test="$described-by != ''"><xsl:attribute name="aria-describedby" select="$described-by"/></xsl:if>
            <svg-icon name="{$icon}"/><xsl:text> </xsl:text><xsl:value-of select="$label"/></button>
        </xsl:when>
        <xsl:otherwise>
          <button type="submit" class="button hollow" disabled="disabled" aria-describedby="{$described-by}"><svg-icon name="{$icon}"/><xsl:text> </xsl:text><xsl:value-of select="$label"/></button>
          <xsl:if test="$disabled-description-id = ''">
            <br/><small id="{$id}-disabled"><xsl:sequence select="$disabled"/></small>
          </xsl:if>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:if test="exists($description)">
        <xsl:text> </xsl:text>
        <button type="button" class="info-button link-button" popovertarget="{$id}-description" aria-label="About {$label}"><svg-icon name="info-circle"/></button>
      </xsl:if>
    </p>
    <xsl:if test="exists($description)">
      <!-- Shown inline by browsers without the popover API; still the button's description when hidden -->
      <p id="{$id}-description" class="info-popover" popover=""><xsl:sequence select="$description"/></p>
    </xsl:if>
  </form>
</xsl:template>

<!-- Why a Berlioz control button is disabled -->
<xsl:template match="berlioz-control[@authorized != 'true']" mode="bzadmin-control-disabled">
  Requires the permission to operate Berlioz administration
</xsl:template>
<xsl:template match="berlioz-control" mode="bzadmin-control-disabled"/>

</xsl:stylesheet>
