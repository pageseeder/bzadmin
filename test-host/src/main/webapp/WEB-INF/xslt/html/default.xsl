<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                exclude-result-prefixes="xsl xs f">

<!-- General Output properties. -->
<xsl:output method="html" encoding="utf-8" indent="yes" undeclare-prefixes="no" media-type="text/html" />

<!-- The context path of the application, before links that start with '/' -->
<xsl:variable name="context" select="string(/root/header/path/@context)"/>

<!--
  Main template called in all cases.
-->
<xsl:template match="/root">
<!-- Display the HTML Doctype -->
<xsl:text disable-output-escaping="yes"><![CDATA[<!doctype html>
]]></xsl:text>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title><xsl:value-of select="(content//documentinfo/uri/@title, 'Berlioz Admin development application')[1]"/></title>
  <link rel="shortcut icon" type="image/svg+xml" href="{$context}/favicon.svg"/>
  <link rel="stylesheet" href="{$context}/style/home.css"/>
</head>
<body>
  <main>
    <xsl:apply-templates select="content[@name='home']/psml-content/document"/>
    <xsl:if test="not(content[@name='home']/psml-content)">
      <p><a href="{$context}/bzadmin/config.html">Berlioz administration</a></p>
    </xsl:if>
  </main>
  <xsl:for-each select="content[@name='home']/psml-content">
    <footer>
      <xsl:choose>
        <xsl:when test="@cache = 'hit'">Served from the PSML cache (stored <xsl:value-of select="@cached"/>).</xsl:when>
        <xsl:when test="@cache = 'miss'">Read from <code>WEB-INF/psml<xsl:value-of select="@path"/></code> and stored in the PSML cache.</xsl:when>
        <xsl:otherwise>Read from <code>WEB-INF/psml<xsl:value-of select="@path"/></code> (no cache).</xsl:otherwise>
      </xsl:choose>
    </footer>
  </xsl:for-each>
</body>
</html>
</xsl:template>

<!-- PSML ==================================================================================== -->

<xsl:template match="document">
  <xsl:apply-templates select="section/fragment"/>
</xsl:template>

<xsl:template match="fragment">
  <section id="{@id}">
    <xsl:apply-templates/>
  </section>
</xsl:template>

<xsl:template match="heading">
  <xsl:element name="h{(@level, 1)[1]}">
    <xsl:apply-templates/>
  </xsl:element>
</xsl:template>

<xsl:template match="para">
  <p><xsl:apply-templates/></p>
</xsl:template>

<xsl:template match="list">
  <ul><xsl:apply-templates select="item"/></ul>
</xsl:template>

<xsl:template match="nlist">
  <ol><xsl:apply-templates select="item"/></ol>
</xsl:template>

<xsl:template match="item">
  <li><xsl:apply-templates/></li>
</xsl:template>

<xsl:template match="bold">
  <strong><xsl:apply-templates/></strong>
</xsl:template>

<xsl:template match="italic">
  <em><xsl:apply-templates/></em>
</xsl:template>

<xsl:template match="monospace">
  <code><xsl:apply-templates/></code>
</xsl:template>

<xsl:template match="link">
  <a href="{if (starts-with(@href, '/')) then concat($context, @href) else @href}"><xsl:apply-templates/></a>
</xsl:template>

</xsl:stylesheet>
