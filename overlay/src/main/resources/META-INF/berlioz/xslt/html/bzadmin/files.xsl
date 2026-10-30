<?xml version="1.0"?>
<!--
  Display the PSML files in the Berlioz application, one folder at a time

  The tree lists the folders on demand: the frontend loads the folders of a folder from the
  'files-folders' page when it is opened.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              exclude-result-prefixes="#all">

<!--
  Display a folder: the tree on the left, the entries of the folder on the right
-->
<xsl:template match="content[@name=('files', 'files-document')]" mode="bzadmin-content">
<xsl:variable name="root" select="(psml-folder/@root, psml-document/@root, no-psml-folder/@root, no-psml-document/@root, no-psml-root/@name, 'psml')[1]"/>
<xsl:variable name="path" select="(psml-folder/@path, psml-document/@path, no-psml-folder/@path, no-psml-document/@path, '/')[1]"/>
<xsl:variable name="folder" select="if (@name = 'files-document') then replace($path, '/[^/]*$', '') else $path"/>
<section class="psml-browser">
  <h2 class="psml-path"><xsl:sequence select="f:psml-breadcrumbs($root, $path, false())"/></h2>
  <xsl:choose>
    <xsl:when test="no-psml-root">
      <div class="panel"><div class="alert callout"><p><svg-icon name="warning"/> No PSML root with this name.</p></div></div>
    </xsl:when>
    <xsl:when test="psml-folder/@exists = 'false'">
      <div class="panel"><div class="callout"><p><svg-icon name="info"/> This application has no PSML folder.</p></div></div>
    </xsl:when>
    <xsl:otherwise>
      <div class="psml-context">
        <section class="panel psml-tree-panel" aria-label="PSML folders">
          <div class="directory-tree tree file-tree" data-folders="{f:to-path('/bzadmin/files/folders')}" data-root="{$root}" data-path="{if ($folder = '') then '/' else $folder}">
            <ul class="no-bullet">
              <xsl:call-template name="psml-tree-folder">
                <xsl:with-param name="root" select="$root"/>
                <xsl:with-param name="path" select="'/'"/>
                <xsl:with-param name="name" select="$root"/>
              </xsl:call-template>
            </ul>
          </div>
        </section>
        <section class="panel psml-selection" aria-label="Selected PSML context">
          <xsl:choose>
            <xsl:when test="psml-folder|no-psml-folder|psml-document">
              <xsl:apply-templates select="psml-folder|no-psml-folder|psml-document" mode="bzadmin-content-files"/>
            </xsl:when>
            <xsl:otherwise>
              <div class="alert callout"><p><svg-icon name="warning"/> <xsl:value-of select="if (no-psml-document) then f:psml-problem(no-psml-document/@problem, 'document') else 'No document specified.'"/></p></div>
            </xsl:otherwise>
          </xsl:choose>
        </section>
      </div>
      <xsl:apply-templates select="psml-document" mode="psml-source"/>
    </xsl:otherwise>
  </xsl:choose>
</section>
</xsl:template>

<!-- The entries of a folder in a table, in ranges -->
<xsl:template match="psml-folder" mode="bzadmin-content-files">
<xsl:variable name="offset" select="xs:integer(@offset)"/>
<xsl:variable name="limit" select="xs:integer(@limit)"/>
<xsl:variable name="total" select="xs:integer(@total)"/>
<xsl:variable name="count" select="count(entry)"/>
<h3>Folder contents</h3>
<xsl:if test="@truncated = 'true'">
  <div class="alert callout">
    <p><svg-icon name="warning"/> This folder has too many entries: only the first <xsl:value-of select="$total"/> found are listed, and sorted.</p>
  </div>
</xsl:if>
<div class="table-scroll" tabindex="0" role="group" aria-label="Folder contents"><table class="basic-table" aria-label="Folder contents">
  <colgroup>
    <col class="name"/>
    <col class="modified"/>
    <col class="size"/>
  </colgroup>
  <thead>
    <tr>
      <th>Name</th>
      <th>Last Modified</th>
      <th>Size</th>
    </tr>
  </thead>
  <tbody>
    <xsl:for-each select="entry">
    <tr class="{@type}">
      <xsl:choose>
        <xsl:when test="@type = 'folder'">
          <td><a href="{f:psml-href('/bzadmin/files', ../@root, @path)}"><svg-icon name="folder-o"/><xsl:text> </xsl:text><xsl:value-of select="@name"/></a></td>
          <td><xsl:sequence select="f:last-modified(@modified)"/></td>
          <td>Folder</td>
        </xsl:when>
        <xsl:otherwise>
          <td><a href="{f:psml-href('/bzadmin/files/document', ../@root, @path)}"><xsl:value-of select="@name"/></a></td>
          <td><xsl:sequence select="f:last-modified(@modified)"/></td>
          <td><xsl:value-of select="f:to-size(@size)"/></td>
        </xsl:otherwise>
      </xsl:choose>
    </tr>
    </xsl:for-each>
    <xsl:if test="$total = 0">
      <tr><td colspan="3">No folders or PSML files in this folder.</td></tr>
    </xsl:if>
  </tbody>
</table></div>
<xsl:if test="$total gt $count">
  <xsl:variable name="base" select="f:psml-href('/bzadmin/files', @root, @path)"/>
  <nav class="psml-pages" aria-label="Folder pages">
    <xsl:if test="$offset gt 0">
      <a href="{$base}&amp;offset={max(($offset - $limit, 0))}"><svg-icon name="angle-left"/> Previous</a>
    </xsl:if>
    <span>Entries <xsl:value-of select="if ($count = 0) then $offset else $offset + 1"/>–<xsl:value-of select="$offset + $count"/> of <xsl:value-of select="$total"/></span>
    <xsl:if test="$offset + $count lt $total">
      <a href="{$base}&amp;offset={$offset + $limit}">Next <svg-icon name="angle-right"/></a>
    </xsl:if>
  </nav>
</xsl:if>
</xsl:template>

<xsl:template match="no-psml-folder" mode="bzadmin-content-files">
<h3>Folder contents</h3>
<div class="alert callout">
  <p><svg-icon name="warning"/> <xsl:value-of select="f:psml-problem(@problem, 'folder')"/></p>
</div>
</xsl:template>

<!--
  The folders of a folder, for the tree: the frontend inserts the list in the folder
-->
<xsl:template match="content[@name='files-folders']" mode="bzadmin-content">
<section id="psml-folders">
  <xsl:for-each select="psml-folder">
    <ul class="no-bullet">
      <xsl:for-each select="entry[@type = 'folder']">
        <xsl:call-template name="psml-tree-folder">
          <xsl:with-param name="root" select="../@root"/>
          <xsl:with-param name="path" select="@path"/>
          <xsl:with-param name="name" select="@name"/>
        </xsl:call-template>
      </xsl:for-each>
      <xsl:variable name="more" select="xs:integer(@total) - xs:integer(@offset) - count(entry)"/>
      <xsl:if test="$more gt 0">
        <li class="leaf"><a href="{f:psml-href('/bzadmin/files', @root, @path)}" class="goto"><xsl:value-of select="$more"/> more folders</a></li>
      </xsl:if>
    </ul>
  </xsl:for-each>
  <xsl:for-each select="no-psml-folder">
    <div class="alert callout"><p><svg-icon name="warning"/> <xsl:value-of select="f:psml-problem(@problem, 'folder')"/></p></div>
  </xsl:for-each>
</section>
</xsl:template>

<!-- A folder of the tree, whose folders the frontend loads when it is opened -->
<xsl:template name="psml-tree-folder">
<xsl:param name="root" as="xs:string"/>
<xsl:param name="path" as="xs:string"/>
<xsl:param name="name" as="xs:string"/>
<li>
  <details data-path="{$path}">
    <summary>
      <svg-icon name="caret-right" class="caret"/><xsl:text> </xsl:text>
      <a href="{f:psml-href('/bzadmin/files', $root, $path)}" class="goto"><svg-icon name="folder-o"/><xsl:text> </xsl:text><xsl:value-of select="$name"/></a>
    </summary>
  </details>
</li>
</xsl:template>

<!--
  Display the metadata and the source of a document
-->
<xsl:template match="psml-document" mode="bzadmin-content-files">
<h3>Document details</h3>
<xsl:if test="@status != 'ok'">
  <div class="alert callout">
    <p><svg-icon name="warning"/>
      <xsl:text> </xsl:text>
      <xsl:choose>
        <xsl:when test="@status = 'malformed'">This document is not well-formed<xsl:if test="@line"> (line <xsl:value-of select="@line"/><xsl:if test="@column">, column <xsl:value-of select="@column"/></xsl:if>)</xsl:if>: <xsl:value-of select="@message"/></xsl:when>
        <xsl:when test="@status = 'too-large'">This document is too large to read its metadata.</xsl:when>
        <xsl:when test="@status = 'doctype'">This document has a document type declaration: its metadata is not read.</xsl:when>
        <xsl:when test="@status = 'too-deep'">This document is too deep: its metadata was only read in part.</xsl:when>
        <xsl:when test="@status = 'too-many-elements'">This document has too many elements: its metadata was only read in part.</xsl:when>
        <xsl:otherwise>The metadata of this document could not be read (<xsl:value-of select="@status"/>).</xsl:otherwise>
      </xsl:choose>
    </p>
  </div>
</xsl:if>
<div class="table-scroll" tabindex="0" role="group" aria-label="PSML document metadata"><table class="basic-table psml-metadata" aria-label="PSML document metadata">
  <tbody>
    <tr><th>Title</th><td><xsl:value-of select="@title"/></td></tr>
    <tr><th>Type</th><td><xsl:value-of select="@type"/></td></tr>
    <tr><th>Level</th><td><xsl:value-of select="@level"/></td></tr>
    <tr><th>Document ID</th><td><xsl:value-of select="@docid"/></td></tr>
    <tr><th>Root element</th><td><xsl:value-of select="@element"/></td></tr>
    <tr><th>Sections</th><td><xsl:value-of select="@sections"/></td></tr>
    <tr><th>Fragments</th><td><xsl:value-of select="@fragments"/></td></tr>
    <tr><th>Size</th><td><xsl:value-of select="f:to-size(@size)"/></td></tr>
    <tr><th>Last modified</th><td><xsl:sequence select="f:last-modified(@modified)"/></td></tr>
  </tbody>
</table></div>
</xsl:template>

<xsl:template match="psml-document" mode="psml-source">
<section class="panel psml-source-panel" aria-label="Document source">
<h3>Source</h3>
<xsl:if test="source/@truncated = 'true'">
  <div class="alert callout">
    <p><svg-icon name="warning"/> Only the start of this document is shown: it has <xsl:value-of select="f:to-size(source/@size)"/>.</p>
  </div>
</xsl:if>
<xml-source><pre class="psml-source"><code><xsl:value-of select="source"/></code></pre></xml-source>
</section>
</xsl:template>

<!--
  The link to a page of a PSML root
  @param service the path of the service, e.g. '/bzadmin/files'
  @param root    the name of the root
  @param path    the path in the root
-->
<xsl:function name="f:psml-href" as="xs:string">
  <xsl:param name="service" as="xs:string"/>
  <xsl:param name="root" as="xs:string"/>
  <xsl:param name="path" as="xs:string"/>
  <xsl:sequence select="concat(f:to-path($service), '?root=', encode-for-uri($root), '&amp;path=', encode-for-uri($path))"/>
</xsl:function>

<!--
  The links to the folders of a path, from the root
  @param root the name of the root
  @param path the path of the folder or document
  @param link whether to link the last name too
-->
<xsl:function name="f:psml-breadcrumbs">
  <xsl:param name="root" as="xs:string"/>
  <xsl:param name="path" as="xs:string"/>
  <xsl:param name="link" as="xs:boolean"/>
  <xsl:variable name="names" select="tokenize($path, '/')[. != '']"/>
  <xsl:choose>
    <xsl:when test="empty($names) and not($link)"><xsl:value-of select="$root"/></xsl:when>
    <xsl:otherwise><a href="{f:psml-href('/bzadmin/files', $root, '/')}"><xsl:value-of select="$root"/></a></xsl:otherwise>
  </xsl:choose>
  <xsl:for-each select="$names">
    <xsl:variable name="i" select="position()"/>
    <svg-icon name="angle-right"/>
    <xsl:choose>
      <xsl:when test="$i = count($names) and not($link)"><xsl:value-of select="."/></xsl:when>
      <xsl:otherwise><a href="{f:psml-href('/bzadmin/files', $root, concat('/', string-join(subsequence($names, 1, $i), '/')))}"><xsl:value-of select="."/></a></xsl:otherwise>
    </xsl:choose>
  </xsl:for-each>
</xsl:function>

<!--
  The message for a problem of a PSML request
  @param problem the problem code
  @param what    'folder' or 'document'
-->
<xsl:function name="f:psml-problem" as="xs:string">
  <xsl:param name="problem" as="xs:string"/>
  <xsl:param name="what" as="xs:string"/>
  <xsl:sequence select="if ($problem = 'invalid-path') then 'This path is not allowed.'
                        else if ($problem = 'unreadable') then concat('This ', $what, ' cannot be read.')
                        else concat('No ', $what, ' at this path.')"/>
</xsl:function>

</xsl:stylesheet>
