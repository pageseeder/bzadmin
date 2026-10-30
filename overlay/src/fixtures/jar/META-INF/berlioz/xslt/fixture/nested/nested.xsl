<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixture: included by `main.xsl` from a sub-folder; imports the sibling of `main.xsl` again from the
  parent folder, and includes a stylesheet relative to itself.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fx="urn:bzadmin:fixture" xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all">

<xsl:import href="../sibling.xsl"/>
<xsl:include href="leaf.xsl"/>

<!--
  Marker for the nested stylesheet.
-->
<xsl:function name="fx:nested" as="xs:string">
  <xsl:sequence select="'fixture-nested'"/>
</xsl:function>

</xsl:stylesheet>
