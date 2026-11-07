<?xml version="1.0" encoding="UTF-8"?>
<!--
  The XML output of the Berlioz Admin services, which Berlioz selects when the host has no
  'WEB-INF/xslt/xml/bzadmin.xsl'. Hosts without kickstart have no fallback stylesheet, and the
  operations and XML representations of the pages depend on this one.

  The envelope is filtered through an allowlist, as the XML fallback of kickstart does:

    - the <header> element is removed;
    - <root> keeps only '@service', '@group' and '@flags';
    - <content> keeps only '@name', '@target' and '@status';
    - the generator payload inside <content> is copied unchanged.

  Anything added to the envelope in future remains private by default.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:output method="xml" media-type="application/xml" indent="no" encoding="utf-8"/>

<xsl:template match="/">
  <xsl:apply-templates select="root"/>
</xsl:template>

<xsl:template match="root">
  <root>
    <xsl:copy-of select="@service | @group | @flags"/>
    <xsl:apply-templates select="content"/>
  </root>
</xsl:template>

<xsl:template match="content">
  <content>
    <xsl:copy-of select="@name | @target | @status"/>
    <xsl:copy-of select="node()"/>
  </content>
</xsl:template>

</xsl:stylesheet>
