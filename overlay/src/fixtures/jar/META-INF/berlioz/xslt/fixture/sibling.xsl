<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixture: imported by `main.xsl` from the same folder.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fx="urn:bzadmin:fixture" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all">

<!--
  Marker for the sibling stylesheet.
-->
<xsl:function name="fx:sibling" as="xs:string">
  <xsl:sequence select="'fixture-sibling'"/>
</xsl:function>

</xsl:stylesheet>
