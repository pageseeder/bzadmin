<?xml version="1.0"?>
<!--
  Display XSLT documentation in the Berlioz application
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xf="https://pageseeder.org/berlioz/xslt/documentation"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!--
  Display the Berlioz configuration
-->
<xsl:template match="content[@name='xslt']" mode="bzadmin-content">
<section>
  <xsl:call-template name="xslt-key-metrics"/>
  <xsl:apply-templates select="../content[@name='berlioz-control']/berlioz-control" mode="bzadmin-xslt-cache"/>
  <div class="panel">
    <div id="xslttree" class="tree file-tree">
      <ul class="no-bullet">
        <xsl:apply-templates select="file/file[@type = 'folder']" mode="bzadmin-content-xslttree"/>
      </ul>
    </div>
  </div>
</section>
</xsl:template>

<!--
  The stylesheets by source, those of the folder replacing one of a JAR overlay, those found more
  than once on the classpath, and the XSLT cache mode; the context is the 'xslt' content
-->
<xsl:template name="xslt-key-metrics">
  <xsl:variable name="files" select="file//file[@type = 'file']"/>
  <xsl:variable name="overrides" select="$files[@source = 'override']"/>
  <xsl:variable name="duplicates" select="$files[@copies]"/>
  <xsl:variable name="cache" select="string(../content[@name='berlioz-control']/berlioz-control/@xslt-cache)"/>
  <dl id="xslt-key-metrics" class="key-metrics">
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Stylesheets'"/>
      <xsl:with-param name="value" select="string(count($files))"/>
      <xsl:with-param name="detail" select="concat(count($files[@source = ('file', 'override')]), ' in the XSLT folder, ', count($files[@source = 'classpath']), ' from JAR overlays')"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Overrides'"/>
      <xsl:with-param name="value" select="string(count($overrides))"/>
      <xsl:with-param name="detail" select="'Replace a stylesheet of a JAR overlay'"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'Duplicates'"/>
      <xsl:with-param name="value" select="string(count($duplicates))"/>
      <xsl:with-param name="detail" select="'On the classpath more than once: Berlioz uses the first'"/>
      <xsl:with-param name="warning" select="exists($duplicates)"/>
    </xsl:call-template>
    <xsl:call-template name="bzadmin-key-metric">
      <xsl:with-param name="label" select="'XSLT cache'"/>
      <xsl:with-param name="value" select="$cache"/>
      <xsl:with-param name="detail" select="if ($cache = 'no') then 'Compiled for every request: nothing to clear'
                                            else if ($cache = 'auto') then 'Compiled again when a file changes'
                                            else if ($cache = 'manual') then 'Compiled again when cleared' else ()"/>
    </xsl:call-template>
  </dl>
</xsl:template>

<!--
  What the XSLT cache does not pick up (the mode is a key metric), and a button to clear the cache, disabled when there is no cache or the
  user cannot operate (the admin filter only authorises control parameters for them)
-->
<xsl:template match="berlioz-control" mode="bzadmin-xslt-cache">
<div class="row">
  <div class="medium-8 columns">
    <p class="muted">Browse stylesheet documentation from the application’s XSLT folder and JAR overlays.
      Files in the XSLT folder take precedence over JAR overlays.

      <xsl:if test="@xslt-cache = 'auto'">Only changes in the folder of the group stylesheet are detected: clear the cache
        if a change was not picked up, for example in a module imported from outside that folder. </xsl:if>
      <xsl:text>Stylesheets from JAR overlays only change when the application restarts.</xsl:text></p>
  </div>
  <div class="medium-4 columns text-right">
    <xsl:call-template name="bzadmin-control-form">
      <xsl:with-param name="id"       select="'clear-xslt-cache'"/>
      <xsl:with-param name="path"     select="'/bzadmin/xslt/cache/clear'"/>
      <xsl:with-param name="scope"    select="'xslt'"/>
      <xsl:with-param name="label"    select="'Clear XSLT cache'"/>
      <xsl:with-param name="icon"     select="'recycle'"/>
      <xsl:with-param name="class"    select="if (@xslt-cache = 'manual') then 'button success' else 'button hollow'"/>
      <xsl:with-param name="disabled-description-id" select="if (@xslt-cache = 'no') then 'xslt-key-metrics' else ''"/>
      <xsl:with-param name="disabled" as="node()*">
        <xsl:choose>
          <xsl:when test="@xslt-cache = 'no'">Caching is off (<code>berlioz.xslt.cache</code>)</xsl:when>
          <xsl:otherwise><xsl:apply-templates select="." mode="bzadmin-control-disabled"/></xsl:otherwise>
        </xsl:choose>
      </xsl:with-param>
    </xsl:call-template>
  </div>
</div>
</xsl:template>

<!-- Display the content of the folder in a table -->
<xsl:template match="file[@type = 'folder']" mode="bzadmin-content-xslttree">
<li>
  <details>
    <summary>
      <svg-icon name="caret-right" class="caret"/><svg-icon name="folder-o"/><xsl:text> </xsl:text><xsl:value-of select="@name"/>
    </summary>
    <ul class="no-bullet">
      <xsl:apply-templates select="file" mode="bzadmin-content-xslttree">
        <xsl:sort select="@type" order="descending"/>
        <xsl:sort select="lower-case(@name)"/>
      </xsl:apply-templates>
    </ul>
  </details>
</li>
</xsl:template>

<xsl:template match="file[@type = 'file']" mode="bzadmin-content-xslttree">
<li class="leaf">
  <a href="{f:to-path('/bzadmin/xslt/doc')}?path={encode-for-uri(@path)}" class="goto"><xsl:value-of select="@name"/></a>
  <xsl:choose>
    <xsl:when test="@source = 'classpath'"><xsl:text> </xsl:text><span class="tag" title="From a JAR overlay (META-INF/berlioz/{@path})">classpath</span></xsl:when>
    <xsl:when test="@source = 'override'"><xsl:text> </xsl:text><span class="tag" title="Replaces META-INF/berlioz/{@path} of a JAR overlay">override</span></xsl:when>
  </xsl:choose>
  <xsl:if test="@copies"><xsl:text> </xsl:text><span class="tag" title="META-INF/berlioz/{@path} is in {@copies} places on the classpath: Berlioz and the documentation use the first">×<xsl:value-of select="@copies"/></span></xsl:if>
</li>
</xsl:template>


<!-- File views share their navigation and identity above the content panel. -->
<xsl:template match="content[@name = ('xslt-doc', 'xslt-source')]" mode="bzadmin-secondary-nav">
  <xsl:variable name="path" select="string((xslt-source/@path, xsltdoc-error/@path, /root/header/http-parameters/parameter[@name = 'path'][1], xsltdoc/stylesheet[1]/@path)[1])"/>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href" select="concat(f:to-path('/bzadmin/xslt/doc'), '?path=', encode-for-uri($path))"/>
    <xsl:with-param name="label" select="'Documentation'"/>
    <xsl:with-param name="active" select="@name = 'xslt-doc'"/>
  </xsl:call-template>
  <xsl:call-template name="bzadmin-secondary-link">
    <xsl:with-param name="href" select="concat(f:to-path('/bzadmin/xslt/source'), '?path=', encode-for-uri($path))"/>
    <xsl:with-param name="label" select="'Source'"/>
    <xsl:with-param name="active" select="@name = 'xslt-source'"/>
  </xsl:call-template>
</xsl:template>

<xsl:template name="xslt-file-heading">
  <xsl:variable name="file" select="(xslt-source, xsltdoc-error, xsltdoc/stylesheet[1])[1]"/>
  <h2><xsl:value-of select="if ($file/@package) then 'Package' else 'Stylesheet'"/>: <xsl:value-of select="($file/@name, tokenize($file/@path, '/')[last()])[1]"/></h2>
  <p class="path"><code><xsl:value-of select="$file/@path"/></code></p>
</xsl:template>

<xsl:template match="content[@name='xslt-doc']" mode="bzadmin-content">
  <xsl:call-template name="xslt-file-heading"/>
  <xsl:apply-templates mode="xsltdoc-nav"/>
  <div class="panel">
    <xsl:apply-templates mode="xsltdoc-main"/>
  </div>
</xsl:template>

<!--
  Why the stylesheet could not be documented (e.g. a missing import)
-->
<xsl:template match="xsltdoc-error" mode="xsltdoc-nav"/>

<xsl:template match="xsltdoc-error" mode="xsltdoc-main">
<section>
  <div class="alert callout">
    <p><svg-icon name="warning"/> Unable to document <code><xsl:value-of select="@path"/></code>: <xsl:value-of select="."/></p>
  </div>
</section>
</xsl:template>

<!--
  The source of a stylesheet, highlighted by <xml-source>
-->
<xsl:template match="content[@name='xslt-source']" mode="bzadmin-content">
<xsl:call-template name="xslt-file-heading"/>
<section class="panel">
  <xsl:for-each select="xslt-source">
    <p>
      <xsl:if test="@location = 'classpath'"><xsl:text> </xsl:text><span class="tag" title="From a JAR overlay (META-INF/berlioz/{@path})">classpath</span></xsl:if>
      <xsl:if test="@size"><xsl:if test="@location = 'classpath'"><xsl:text> – </xsl:text></xsl:if><xsl:value-of select="f:to-size(@size)"/></xsl:if>
    </p>
    <xsl:if test="@truncated = 'true'">
      <div class="alert callout">
        <p><svg-icon name="warning"/> Only the start of this stylesheet is shown.</p>
      </div>
    </xsl:if>
    <xml-source><pre><code><xsl:value-of select="."/></code></pre></xml-source>
  </xsl:for-each>
</section>
</xsl:template>

<!--
  Root template to generate the HTML from generated XSLT documentation.
-->
<xsl:template match="xsltdoc" mode="xsltdoc-nav">
<xsl:if test="count(stylesheet) > 1">
<div id="xsltdoc-nav">
  <nav aria-label="XSLT documentation">
    <ul>
      <li class="active"><a href="#xslt-summary" aria-current="true">SUMMARY</a></li>
      <li><a href="#xslt-templates">Templates</a></li>
      <li><a href="#xslt-functions">Functions</a></li>
      <xsl:if test="stylesheet/parameter"><li><a href="#xslt-parameters">Parameters</a></li></xsl:if>
      <xsl:if test="stylesheet/variable"><li><a href="#xslt-variables">Variables</a></li></xsl:if>
      <li class="files">
        <ul>
          <li><a href="#xslt-stylesheets">Stylesheets <small>&#x25bc;</small></a></li>
        <xsl:for-each select="stylesheet">
          <li><a href="#stylesheet-{replace(@path, '\W', '_')}"><xsl:value-of select="@name"/></a></li>
        </xsl:for-each>
        </ul>
      </li>
    </ul>
  </nav>
</div>
</xsl:if>
</xsl:template>


<!--
  Root template to generate the HTML from generated XSLT documentation.
-->
<xsl:template match="xsltdoc" mode="xsltdoc-main">
<div>
<xsl:if test="@scope = 'source'">
  <p><small>Read from the stylesheet sources, not from the compiled stylesheet:
  <code>use-when</code> and shadow attributes are not evaluated, packages named by <code>xsl:use-package</code>
  are not loaded, and implicit priorities follow the XSLT 2.0 rules.</small></p>
</xsl:if>
<xsl:if test="count(stylesheet) > 1">
  <xsl:apply-templates select="." mode="xsltdoc-summary"/>
</xsl:if>
<xsl:apply-templates mode="xsltdoc"/>
</div>
</xsl:template>

<!--
  Generates the navigation and summary page for when a collection of templates is being viewed.
-->
<xsl:template match="xsltdoc" mode="xsltdoc-summary">
  <section class="xsltdoc" id="xslt-summary">
    <h2>Summary</h2>
    <xsl:apply-templates select="stylesheet[1]/output" mode="xsltdoc"/>
    <xsl:call-template name="template-summary"/>
    <xsl:call-template name="function-summary"/>
  </section>
  <section class="xsltdoc" id="xslt-functions">
    <h2>All functions</h2>
    <xsl:apply-templates select="stylesheet/function" mode="xsltdoc">
      <xsl:with-param name="id-prefix" select="'all-'" tunnel="yes"/>
      <xsl:with-param name="level" select="3"/>
      <xsl:sort select="@name"/>
    </xsl:apply-templates>
  </section>
  <section class="xsltdoc" id="xslt-templates">
    <h2>All templates</h2>
    <xsl:apply-templates select="stylesheet/template" mode="xsltdoc">
      <xsl:with-param name="id-prefix" select="'all-'" tunnel="yes"/>
      <xsl:with-param name="level" select="3"/>
      <xsl:sort select="if (@match) then 0 else 1"/>
      <xsl:sort select="@mode"/>
      <xsl:sort select="@match|@name"/>
    </xsl:apply-templates>
  </section>
  <xsl:if test="stylesheet/parameter">
    <section class="xsltdoc" id="xslt-parameters">
      <xsl:call-template name="global-parameters"><xsl:with-param name="level" select="2"/></xsl:call-template>
    </section>
  </xsl:if>
  <xsl:if test="stylesheet/variable">
    <section class="xsltdoc" id="xslt-variables">
      <xsl:call-template name="global-variables"><xsl:with-param name="level" select="2"/></xsl:call-template>
    </section>
  </xsl:if>
  <section class="xsltdoc" id="xslt-stylesheets">
    <h2>All Stylesheets</h2>
    <xsl:for-each select="stylesheet">
      <h3 class="stylesheet-title"><xsl:value-of select="@name"/></h3>
      <p><code><xsl:value-of select="@path"/></code></p>
    </xsl:for-each>
  </section>
</xsl:template>

<!--
  Generates documentation for a single stylesheet.
-->
<xsl:template match="stylesheet" mode="xsltdoc">
<section class="xsltdoc" id="stylesheet-{replace(@path, '\W', '_')}">
  <!-- All documentation remains available without JavaScript. -->
  <xsl:if test="count(../stylesheet) > 1">
  <h2><xsl:value-of select="if (@package) then 'Package' else 'Stylesheet'"/>: <xsl:value-of select="@name"/></h2>
  <p class="path"><xsl:value-of select="@path"/></p>
  </xsl:if>
  <xsl:if test="@package">
    <p>Package <code><xsl:value-of select="@package"/></code>
    <xsl:if test="@package-version"> version <code><xsl:value-of select="@package-version"/></code></xsl:if></p>
  </xsl:if>
  <xsl:if test="@simplified = 'yes'">
    <p>Not a stylesheet module (<code>xsl:stylesheet</code>, <code>xsl:transform</code> or <code>xsl:package</code>): a simplified stylesheet has no declarations to document.</p>
  </xsl:if>
  <xsl:copy-of select="doc/description/*"/>
  <xsl:if test="import">
    <h3>Imports</h3>
    <ul class="imports"><xsl:apply-templates select="import" mode="xsltdoc"/></ul>
  </xsl:if>
  <xsl:if test="include">
    <h3>Includes</h3>
    <ul class="includes"><xsl:apply-templates select="include" mode="xsltdoc"/></ul>
  </xsl:if>
  <xsl:if test="use-package">
    <h3>Packages used</h3>
    <ul><xsl:apply-templates select="use-package" mode="xsltdoc"/></ul>
  </xsl:if>
  <xsl:apply-templates select="output" mode="xsltdoc"/>
  <xsl:if test="not(@simplified = 'yes')"><xsl:call-template name="namespaces"/></xsl:if>
  <xsl:if test="global-context-item|mode|expose">
    <h3>Modes and visibility</h3>
    <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT modes and visibility"><table class="basic-table" aria-label="XSLT modes and visibility">
      <thead><tr><th>Declaration</th><th>Attributes</th><th>Description</th></tr></thead>
      <tbody><xsl:apply-templates select="global-context-item|mode|expose" mode="xsltdoc"/></tbody>
    </table></div>
  </xsl:if>
  <xsl:if test="accumulator">
    <h3>Accumulators</h3>
    <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT accumulators"><table class="basic-table" aria-label="XSLT accumulators">
      <thead><tr><th>Name</th><th>Initial value</th><th>Type</th><th>Rules</th><th>Description</th></tr></thead>
      <tbody><xsl:apply-templates select="accumulator" mode="xsltdoc"/></tbody>
    </table></div>
  </xsl:if>
  <xsl:if test="unsupported">
    <h3>Other declarations</h3>
    <p>Not documented: <xsl:for-each select="unsupported"><code>xsl:<xsl:value-of select="@name"/></code><xsl:if test="position() != last()">, </xsl:if></xsl:for-each></p>
  </xsl:if>
  <xsl:call-template name="global-parameters"/>
  <xsl:call-template name="global-variables"/>
  <xsl:call-template name="template-summary"/>
  <xsl:call-template name="function-summary"/>
  <!-- details -->
  <xsl:if test="template">
    <h3>Templates</h3>
    <p><small>(In stylesheet order)</small></p>
    <xsl:apply-templates select="template" mode="xsltdoc"/>
  </xsl:if>
  <xsl:if test="function">
    <h3>Functions</h3>
    <xsl:apply-templates select="function" mode="xsltdoc">
      <xsl:sort select="@name"/>
    </xsl:apply-templates>
  </xsl:if>
</section>
</xsl:template>

<!-- Display a single include or import declaration -->
<xsl:template match="import|include" mode="xsltdoc">
<li>
  <xsl:choose>
    <xsl:when test="@followed = 'no'"><code><xsl:value-of select="@_href"/></code> <small>(shadow attribute, not followed)</small></xsl:when>
    <xsl:otherwise><a href="#stylesheet-{replace((@path, @href)[1], '\W', '_')}" class="goto"><xsl:value-of select="@href"/></a></xsl:otherwise>
  </xsl:choose>
  <xsl:if test="@use-when"><xsl:text> </xsl:text><small>use-when <code><xsl:value-of select="@use-when"/></code> (not evaluated)</small></xsl:if>
</li>
</xsl:template>

<!-- Display a package used: it is not loaded, so only its overrides and accepted components -->
<xsl:template match="use-package" mode="xsltdoc">
<li>
  <code><xsl:value-of select="@name"/></code>
  <xsl:if test="@package-version"> version <code><xsl:value-of select="@package-version"/></code></xsl:if>
  <xsl:for-each select="override">
    <br/><small>overrides <xsl:value-of select="@kind"/><xsl:text> </xsl:text><code><xsl:value-of select="(@name, @match)[1]"/></code></small>
  </xsl:for-each>
  <xsl:for-each select="accept">
    <br/><small>accepts <xsl:value-of select="@component"/><xsl:text> </xsl:text><code><xsl:value-of select="@names"/></code> as <xsl:value-of select="@visibility"/></small>
  </xsl:for-each>
</li>
</xsl:template>

<!-- Display a mode, an exposed component or the global context item -->
<xsl:template match="mode|expose|global-context-item" mode="xsltdoc">
<tr>
  <td><code>xsl:<xsl:value-of select="local-name()"/></code><xsl:if test="@name"><xsl:text> </xsl:text><var><xsl:value-of select="@name"/></var></xsl:if></td>
  <td><xsl:for-each select="@* except @name"><xsl:value-of select="name()"/>=<code><xsl:value-of select="."/></code><xsl:if test="position() != last()">, </xsl:if></xsl:for-each></td>
  <td><xsl:value-of select="doc/description/p[1]"/></td>
</tr>
</xsl:template>

<!-- Display an accumulator -->
<xsl:template match="accumulator" mode="xsltdoc">
<tr>
  <td><code><xsl:value-of select="@name"/></code></td>
  <td><xsl:value-of select="@initial-value"/></td>
  <td><xsl:value-of select="@as"/></td>
  <td><xsl:for-each select="rule"><code><xsl:value-of select="@match"/></code><xsl:if test="@phase"> (<xsl:value-of select="@phase"/>)</xsl:if><xsl:if test="position() != last()"><br/></xsl:if></xsl:for-each></td>
  <td><xsl:value-of select="doc/description/p[1]"/></td>
</tr>
</xsl:template>

<xsl:template match="output" mode="xsltdoc">
<h3>Output</h3>
<div class="table-scroll" tabindex="0" role="group" aria-label="XSLT output"><table class="basic-table" aria-label="XSLT output">
  <thead><tr>
  <xsl:for-each select="@*">
    <th><xsl:value-of select="name()"/></th>
  </xsl:for-each>
  </tr></thead>
  <tbody><tr>
  <xsl:for-each select="@*">
    <td><xsl:value-of select="."/></td>
  </xsl:for-each>
  </tr></tbody>
</table></div>
</xsl:template>

<!-- Display the summary of namespaces -->
<xsl:template name="namespaces">
<h3>Namespaces</h3>
<div class="table-scroll" tabindex="0" role="group" aria-label="XSLT namespaces"><table class="basic-table" aria-label="XSLT namespaces">
  <thead>
    <tr><th>Prefix</th><th>URI</th></tr>
  </thead>
  <tbody>
    <xsl:for-each select="namespaces/namespace">
      <tr>
        <td><xsl:value-of select="@prefix"/></td>
        <td><xsl:value-of select="@uri"/></td>
      </tr>
    </xsl:for-each>
  </tbody>
</table></div>
</xsl:template>

<!--
  Display the summary of global parameters

  @param level The level of the heading
-->
<xsl:template name="global-parameters">
<xsl:param name="level" select="3" as="xs:integer"/>
<xsl:if test="stylesheet/parameter|parameter">
  <xsl:element name="h{$level}">Global parameters</xsl:element>
  <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT global parameters"><table class="basic-table" aria-label="XSLT global parameters">
    <thead>
      <tr><th>Name</th><th>Value</th><th>Type</th><th>Description</th></tr>
    </thead>
    <tbody>
      <xsl:apply-templates select="stylesheet/parameter|parameter" mode="xsltdoc"/>
    </tbody>
  </table></div>
</xsl:if>
</xsl:template>

<!--
  Display the summary of global variables

  @param level The level of the heading
-->
<xsl:template name="global-variables">
<xsl:param name="level" select="3" as="xs:integer"/>
<xsl:if test="stylesheet/variable|variable">
  <xsl:element name="h{$level}">Global variables</xsl:element>
  <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT global variables"><table class="basic-table variables" aria-label="XSLT global variables">
    <thead>
      <tr><th>Name</th><th>Value</th><th>Type</th><th>Description</th></tr>
    </thead>
    <tbody>
      <xsl:apply-templates select="stylesheet/variable|variable" mode="xsltdoc"/>
    </tbody>
  </table></div>
</xsl:if>
</xsl:template>

<!-- Display the summary of templates -->
<xsl:template name="template-summary">
<xsl:if test="stylesheet/template|template">
  <h3>Templates summary</h3>
  <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT templates summary"><table class="basic-table templates" aria-label="XSLT templates summary">
    <thead>
      <tr><th>Match/Name</th><th>Priority</th><th>Mode</th><th>Description</th></tr>
    </thead>
    <tbody>
    <xsl:for-each select="stylesheet/template|template">
      <tr>
        <td>
          <xsl:if test="@match"><a href="#template_{generate-id()}"><code><xsl:value-of select="@match" /></code></a></xsl:if>
          <xsl:if test="@name"><i><xsl:value-of select="@name" /></i></xsl:if>
        </td>
        <td><xsl:sequence select="xf:priority-for-template(@priority, @implicit-priority)"/></td>
        <td><xsl:sequence select="xf:mode-for-template(@mode, @implicit-mode)"/></td>
        <td><xsl:value-of select="doc/description/p[1]"/></td>
      </tr>
    </xsl:for-each>
    </tbody>
  </table></div>
</xsl:if>
</xsl:template>

<!-- Display the summary of functions -->
<xsl:template name="function-summary">
<xsl:if test="stylesheet/function|function">
  <h3>Functions summary</h3>
  <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT functions summary"><table class="basic-table functions" aria-label="XSLT functions summary">
    <thead>
      <tr><th>Returns</th><th>Name/Description</th></tr>
    </thead>
    <tbody>
      <xsl:for-each select="stylesheet/function|function">
        <xsl:sort select="@name"/>
        <tr>
          <td><code><xsl:value-of select="if (@as) then @as else '?'" /></code></td>
          <td>
            <a href="#function_{generate-id()}"><var><xsl:value-of select="@name" /></var> (
            <xsl:for-each select="parameter">
              <var><xsl:value-of select="@name"/></var> as <code><xsl:value-of select="if (@as) then @as else '?'"/></code>
              <xsl:if test="position() != last()">,</xsl:if>
            </xsl:for-each>
            )</a>
            <div class="description"><xsl:value-of select="doc/description/p[1]"/></div>
          </td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table></div>
</xsl:if>
</xsl:template>

<!--
  A template, with a heading of the given level: under the heading of the list of templates.

  @param level The level of the heading of the template
-->
<xsl:template match="template" mode="xsltdoc">
<xsl:param name="id-prefix" select="''" tunnel="yes"/>
<xsl:param name="level" select="4" as="xs:integer"/>
<xsl:element name="h{$level}">
<xsl:attribute name="id" select="concat($id-prefix, 'template_', generate-id())"/>
<xsl:attribute name="class" select="'template-title'"/>
<xsl:text>Template</xsl:text>
<xsl:if test="@match">
  matching <var><xsl:value-of select="@match"/></var>
</xsl:if>
<xsl:if test="@name">
  named <var><xsl:value-of select="@name"/></var>
</xsl:if>
<xsl:if test="@mode">
  <xsl:for-each select="tokenize(@mode, ' ')">
    <span class="mode"><xsl:value-of select="."/></span>
  </xsl:for-each>
</xsl:if>
<xsl:if test="@priority">
  <span class="priority"><xsl:value-of select="@priority"/></span>
</xsl:if>
</xsl:element>
<div class="template-meta">
<xsl:if test="@match">
  <span>Priority: <var><xsl:value-of select="if (@priority) then @priority else @implicit-priority"/></var><xsl:if test="not(@priority)"><xsl:text> </xsl:text><small>(implicit)</small></xsl:if></span>
  <span>Mode: <var><xsl:value-of select="if (@mode) then @mode else (@implicit-mode, '#default')[1]"/></var><xsl:if test="not(@mode)"><xsl:text> </xsl:text><small>(implicit)</small></xsl:if></span>
</xsl:if>
<xsl:if test="@visibility"><span>Visibility: <var><xsl:value-of select="@visibility"/></var></span></xsl:if>
</div>
<xsl:copy-of select="doc/description/*" />
<xsl:call-template name="local-parameters">
  <xsl:with-param name="level" select="$level + 1"/>
</xsl:call-template>
</xsl:template>

<!--
  A function, with a heading of the given level: under the heading of the list of functions.

  @param level The level of the heading of the function
-->
<xsl:template match="function" mode="xsltdoc">
<xsl:param name="id-prefix" select="''" tunnel="yes"/>
<xsl:param name="level" select="4" as="xs:integer"/>
<xsl:element name="h{$level}">
<xsl:attribute name="id" select="concat($id-prefix, 'function_', generate-id())"/>
<xsl:attribute name="class" select="'function-title'"/>
<xsl:text>Function: </xsl:text><i><xsl:value-of select="@name"/></i>
</xsl:element>
<xsl:if test="@visibility"><div class="template-meta"><span>Visibility: <var><xsl:value-of select="@visibility"/></var></span></div></xsl:if>
<xsl:copy-of select="doc/description/*" />
<xsl:element name="h{$level + 1}">Usage</xsl:element>
<div>
<var><xsl:value-of select="@name" /></var> (
<xsl:for-each select="parameter">
  <var><xsl:value-of select="@name"/></var> as <code><xsl:value-of select="if (@as) then @as else '?'"/></code>
  <xsl:if test="position() != last()">,</xsl:if>
</xsl:for-each>
) &#x2192; <code><xsl:value-of select="if (@as) then @as else '?'"/></code></div>
<xsl:call-template name="local-parameters">
  <xsl:with-param name="level" select="$level + 1"/>
</xsl:call-template>
</xsl:template>

<!--
  The parameters of a template or function.

  @param level The level of the heading of the parameters
-->
<xsl:template name="local-parameters">
<xsl:param name="level" as="xs:integer"/>
<xsl:variable name="doc" select="doc" />
<xsl:if test="parameter">
  <xsl:element name="h{$level}">Parameters</xsl:element>
  <div class="table-scroll" tabindex="0" role="group" aria-label="XSLT declaration parameters"><table class="basic-table" aria-label="XSLT declaration parameters">
    <thead>
      <tr>
        <th>Name</th>
        <th>Type</th>
        <xsl:if test="self::template">
          <th>Value</th>
          <th>Tunnel</th>
        </xsl:if>
        <th>Description</th>
      </tr>
    </thead>
    <tbody>
      <xsl:for-each select="parameter">
        <tr>
          <td><var><xsl:value-of select="@name"/></var></td>
          <td><code><xsl:value-of select="@as"/></code></td>
          <xsl:if test="parent::template">
            <td><xsl:sequence select="xf:value-for-declaration(.)"/></td>
            <td><xsl:value-of select="@tunnel"/></td>
          </xsl:if>
          <td><xsl:copy-of select="$doc/parameter[@name = current()/@name]/@description"/></td>
        </tr>
      </xsl:for-each>
    </tbody>
  </table></div>
</xsl:if>
</xsl:template>


<xsl:template match="parameter|variable" mode="xsltdoc">
<tr>
  <td><code><xsl:value-of select="@name"/></code><xsl:if test="@static = ('yes', 'true', '1')"><xsl:text> </xsl:text><span class="tag" title="Static: set when the stylesheet is compiled">static</span></xsl:if></td>
  <td><xsl:sequence select="xf:value-for-declaration(.)"/></td>
  <td><xsl:value-of select="@as"/></td>
  <td><xsl:copy-of select="doc/description/*"/></td>
</tr>
</xsl:template>

<!--
  Display the value of a parameter or variable.

  @param declaration The parameter or variable

  @return its `select` expression, or a note when its value is set by its content
-->
<xsl:function name="xf:value-for-declaration" as="node()?">
<xsl:param name="declaration" as="element()"/>
<xsl:choose>
  <xsl:when test="$declaration/@select"><xsl:value-of select="$declaration/@select"/></xsl:when>
  <xsl:when test="$declaration/@content = 'yes'"><span class="implied" title="Set by its content, not a select expression">(content)</span></xsl:when>
</xsl:choose>
</xsl:function>

<!--
  Display the mode for a template.

  @param mode     The template mode
  @param implicit The implicit mode computed by the XSLT doc tool (the default mode in XSLT 3.0)

  @return the mode
-->
<xsl:function name="xf:mode-for-template" as="element(span)">
<xsl:param name="mode" as="xs:string?"/>
<xsl:param name="implicit" as="xs:string?"/>
<xsl:choose>
  <xsl:when test="$mode"><span><xsl:value-of select="$mode"/></span></xsl:when>
  <xsl:otherwise><span class="implied"><xsl:value-of select="($implicit, '#default')[1]"/></span></xsl:otherwise>
</xsl:choose>
</xsl:function>

<!--
  Display the priority for a template.

  @param priority The template priority
  @param implicit The implicit priority computed by the XSLT doc tool

  @return the specified or implied priority.
-->
<xsl:function name="xf:priority-for-template" as="element(span)">
<xsl:param name="priority" as="xs:string?"/>
<xsl:param name="implicit" as="xs:string?"/>
<xsl:choose>
  <xsl:when test="$priority"><span><xsl:value-of select="$priority"/></span></xsl:when>
  <xsl:when test="$implicit"><span class="implied"><xsl:value-of select="$implicit"/></span></xsl:when>
  <xsl:otherwise><span class="implied">N/A</span></xsl:otherwise>
</xsl:choose>
</xsl:function>

</xsl:stylesheet>
