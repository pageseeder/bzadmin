<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixture: included by `nested/nested.xsl`, two levels below the entry point.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fx="urn:bzadmin:fixture" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all">

<!--
  Marker for the leaf stylesheet.
-->
<xsl:function name="fx:leaf" as="xs:string">
  <xsl:sequence select="'fixture-leaf'"/>
</xsl:function>

</xsl:stylesheet>
