<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixture entry point in a JAR: imports a sibling and includes a nested stylesheet, both relatively.
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fx="urn:bzadmin:fixture" exclude-result-prefixes="#all">

<xsl:import href="sibling.xsl"/>
<xsl:include href="nested/nested.xsl"/>

<xsl:output method="html" encoding="utf-8"/>

<!-- Prints a marker from each stylesheet in the chain -->
<xsl:template match="/">
<html>
  <body>
    <p id="main">fixture-main</p>
    <p id="sibling"><xsl:value-of select="fx:sibling()"/></p>
    <p id="nested"><xsl:value-of select="fx:nested()"/></p>
    <p id="leaf"><xsl:value-of select="fx:leaf()"/></p>
  </body>
</html>
</xsl:template>

</xsl:stylesheet>
