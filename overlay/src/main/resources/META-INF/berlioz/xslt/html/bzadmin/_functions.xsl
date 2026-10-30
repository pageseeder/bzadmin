<?xml version="1.0"?>
<!--
  A collection of reusable Berlioz Admin functions.

  @author Christophe Lauret
  @version 17 December 2013
-->
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                              xmlns:f="http://pageseeder.org/berlioz/xslt/function"
                              xmlns:xs="http://www.w3.org/2001/XMLSchema"
                              exclude-result-prefixes="#all">

<!-- Reference to the Berlioz header -->
<xsl:variable name="bzheader" select="/root/header"/>

<!--
  Constructs the path adding the required context, prefix and extension.

  @param path the path to go to regardless of context or extension.
  @return the reconstructed path
-->
<xsl:function name="f:to-path" as="xs:string">
  <xsl:param name="path" as="xs:string"/>
  <xsl:value-of select="concat($bzheader/path/@context, $bzheader/path/@prefix, $path, $bzheader/path/@extension)"/>
</xsl:function>

<!--
  Return the title based on the specified Berlioz service
  @param service the service
  @return the corresponding page title
-->
<xsl:function name="f:to-page-title" as="xs:string">
  <xsl:param name="service" as="xs:string"/>
  <xsl:choose>
    <xsl:when test="starts-with($service, 'bzadmin-config')"  >Config</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-services')">Services</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-cache')"   >Cache</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-files')"   >PSML</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-xslt')"    >XSLT</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-application')">Application</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-logs')"    >Logs</xsl:when>
    <xsl:when test="starts-with($service, 'bzadmin-system')"  >System</xsl:when>
  </xsl:choose>
</xsl:function>

<!--
  The class of the generator producing the content: for the generators of Berlioz
  used through org.pageseeder.bzadmin.guard, the class they delegate to.

  @param class the class of the generator in the services.
-->
<xsl:function name="f:generator-class" as="xs:string">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:sequence select="if (starts-with($class, 'org.pageseeder.bzadmin.guard.'))
                        then concat('org.pageseeder.', substring-after($class, 'org.pageseeder.bzadmin.guard.'))
                        else string($class)"/>
</xsl:function>

<!--
  The known package of a class, labelled by its last segment.
  Known packages describe namespaces, not verified JAR provenance.

  @param class the fully qualified class name
  @return the package, or an empty string
-->
<xsl:function name="f:known-package" as="xs:string">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:variable name="packages" select="('org.pageseeder.berlioz', 'org.pageseeder.bzadmin', 'org.pageseeder.bastille',
    'org.pageseeder.flint', 'org.pageseeder.bridge', 'net.sf.ehcache', 'org.hibernate')"/>
  <xsl:sequence select="string($packages[starts-with($class, concat(., '.'))][1])"/>
</xsl:function>

<xsl:function name="f:generator-origin" as="xs:string">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:sequence select="string(tokenize(f:known-package(f:generator-class($class)), '\.')[last()])"/>
</xsl:function>

<xsl:function name="f:generator-label" as="xs:string">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:variable name="normalized" select="f:generator-class($class)"/>
  <xsl:variable name="origin" select="f:generator-origin($normalized)"/>
  <xsl:sequence select="if ($origin != '') then concat('[', $origin, '].', tokenize($normalized, '\.')[last()]) else $normalized"/>
</xsl:function>

<!-- Native popovers expose selectable names without changing table layout. -->
<xsl:function name="f:generator-name" as="element()">
  <xsl:param name="class" as="xs:string?"/>
  <xsl:param name="id" as="xs:string"/>
  <xsl:variable name="normalized" select="f:generator-class($class)"/>
  <xsl:variable name="origin" select="f:generator-origin($normalized)"/>
  <xsl:choose>
    <xsl:when test="$origin != ''">
      <div class="generator-name">
        <button type="button" class="info-button link-button generator-name-button" popovertarget="{$id}" aria-label="{concat(f:generator-label($normalized), ': full class name')}"><span class="generator-origin">[<xsl:value-of select="$origin"/>]</span><xsl:text>.</xsl:text><xsl:value-of select="tokenize($normalized, '\.')[last()]"/></button>
        <div id="{$id}" class="info-popover generator-name-popover" popover="" role="group" aria-label="Full class name">
          <code class="generator-full-name"><xsl:value-of select="$normalized"/></code>
          <button type="button" class="link-button" popovertarget="{$id}" popovertargetaction="hide">Close</button>
        </div>
      </div>
    </xsl:when>
    <xsl:otherwise><span class="generator-full-name"><xsl:value-of select="$normalized"/></span></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Abbreviate the classname
  @param the fullname of the class.
-->
<xsl:function name="f:abbr-classname">
  <xsl:param name="classname"/>
  <xsl:variable name="package" select="f:known-package($classname)"/>
  <xsl:choose>
    <xsl:when test="$package != ''"><abbr title="{$package}">[<xsl:value-of select="tokenize($package, '\.')[last()]"/>]</abbr><xsl:value-of select="substring($classname, string-length($package) + 1)"/></xsl:when>
    <xsl:otherwise><xsl:value-of select="$classname"/></xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Return a long string representation of a duration
  @param time The time in seconds
-->
<xsl:function name="f:duration-long">
  <xsl:param name="time"/>
  <span title="{$time}s">
  <xsl:choose>
    <xsl:when test="not($time)">--</xsl:when>
    <xsl:when test="$time = 0"><i>eternal</i></xsl:when>
    <xsl:otherwise>
      <xsl:variable name="duration" select="xs:duration(concat('PT', $time, 'S'))"/>
      <xsl:variable name="days"     select="days-from-duration($duration)"/>
      <xsl:variable name="hours"    select="hours-from-duration($duration)"/>
      <xsl:variable name="minutes"  select="minutes-from-duration($duration)"/>
      <xsl:variable name="seconds"  select="seconds-from-duration($duration)"/>
      <xsl:choose>
        <xsl:when test="$days gt 1"><xsl:value-of select="$days"/> days </xsl:when>
        <xsl:when test="$days gt 0"><xsl:value-of select="$days"/> day </xsl:when>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="$hours gt 1"><xsl:value-of select="$hours"/> hours </xsl:when>
        <xsl:when test="$hours gt 0"><xsl:value-of select="$hours"/> hour </xsl:when>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="$minutes gt 1"><xsl:value-of select="$minutes"/> mins </xsl:when>
        <xsl:when test="$minutes gt 0"><xsl:value-of select="$minutes"/> min </xsl:when>
      </xsl:choose>
      <xsl:choose>
        <xsl:when test="$seconds gt 1"><xsl:value-of select="$seconds"/> seconds </xsl:when>
        <xsl:when test="$seconds gt 0"><xsl:value-of select="$seconds"/> second </xsl:when>
      </xsl:choose>
    </xsl:otherwise>
  </xsl:choose>
  </span>
</xsl:function>

<!--
  Return a long string representation of a duration
  @param time The time in seconds
-->
<xsl:function name="f:duration-short">
  <xsl:param name="time"/>
  <span title="{$time}s">
  <xsl:choose>
    <xsl:when test="not($time)">--</xsl:when>
    <xsl:when test="$time = 0"><i>eternal</i></xsl:when>
    <xsl:otherwise>
      <!-- Use the largest unit with at least 2 of it, based on the total so that no unit is lost -->
      <xsl:variable name="total" select="xs:integer(round(number($time)))"/>
      <xsl:choose>
        <xsl:when test="$total ge 2*86400"><xsl:value-of select="$total idiv 86400"/>d </xsl:when>
        <xsl:when test="$total ge 2*3600"><xsl:value-of select="$total idiv 3600"/>h </xsl:when>
        <xsl:when test="$total ge 2*60"><xsl:value-of select="$total idiv 60"/>m </xsl:when>
        <xsl:otherwise><xsl:value-of select="$total"/>s </xsl:otherwise>
      </xsl:choose>
    </xsl:otherwise>
  </xsl:choose>
  </span>
</xsl:function>

<!--
  Return a long string representation of a duration
  @param time The time in seconds
-->
<xsl:function name="f:datetime-diff">
  <xsl:param name="datetime" as="xs:dateTime"/>
  <time title="{format-dateTime($datetime, '[D1] [MNn,*-3] [Y0001] at [H01]:[m01]:[s01]')}" datetime="{$datetime}">
    <xsl:variable name="now"      select="current-dateTime()"/>
    <xsl:variable name="past"     select="$datetime lt $now"/>
    <xsl:variable name="diff"     select="if ($past) then $now - $datetime else $datetime - $now"/>
    <xsl:variable name="total"    select="xs:integer(round($diff div xs:dayTimeDuration('PT1S')))"/>
    <xsl:if test="not($past)">in </xsl:if>
    <!-- Use the largest unit with at least 2 of it, based on the total so that no unit is lost -->
    <xsl:choose>
      <xsl:when test="$total ge 2*86400"><xsl:value-of select="$total idiv 86400"/> days</xsl:when>
      <xsl:when test="$total ge 2*3600"><xsl:value-of select="$total idiv 3600"/> hrs</xsl:when>
      <xsl:when test="$total ge 2*60"><xsl:value-of select="$total idiv 60"/> mins</xsl:when>
      <xsl:otherwise><xsl:value-of select="$total"/> secs</xsl:otherwise>
    </xsl:choose>
    <xsl:if test="$past"> ago</xsl:if>
  </time>
</xsl:function>

<!--
  Return a long string representation of a duration
  @param time The time in seconds
-->
<xsl:function name="f:last-modified">
  <xsl:param name="datetime" as="xs:dateTime"/>
  <time title="{format-dateTime($datetime, '[D1] [MNn,*-3] [Y0001] at [H01]:[m01]:[s01]')}" datetime="{$datetime}">
    <xsl:variable name="now"      select="current-dateTime()"/>
    <xsl:variable name="past"     select="$datetime lt $now"/>
    <xsl:variable name="diff"     select="if ($past) then $now - $datetime else $datetime - $now"/>
    <xsl:variable name="total"    select="xs:integer(round($diff div xs:dayTimeDuration('PT1S')))"/>
    <xsl:variable name="days"     select="$total idiv 86400"/>
    <xsl:if test="not($past)">in </xsl:if>
    <!-- Use the largest unit with at least 2 of it, based on the total so that no unit is lost -->
    <xsl:choose>
      <xsl:when test="$days gt 730"><xsl:value-of select="$days idiv 365"/> years</xsl:when>
      <xsl:when test="$days gt 60"><xsl:value-of select="$days idiv 30"/> months</xsl:when>
      <xsl:when test="$days gt 14"><xsl:value-of select="$days idiv 7"/> weeks</xsl:when>
      <xsl:when test="$total ge 2*86400"><xsl:value-of select="$days"/> days</xsl:when>
      <xsl:when test="$total ge 2*3600"><xsl:value-of select="$total idiv 3600"/> hrs</xsl:when>
      <xsl:when test="$total ge 2*60"><xsl:value-of select="$total idiv 60"/> mins</xsl:when>
      <xsl:otherwise><xsl:value-of select="$total"/> secs</xsl:otherwise>
    </xsl:choose>
    <xsl:if test="$past"> ago</xsl:if>
  </time>
</xsl:function>

<!--
  Returns the size of the file based on the length in bytes

  Examples:
   - 546     -> '546 bytes'
   - 92274   -> '90.1 KB'
   - 1150016 -> '1.1 MB'

  @param the length to
-->
<xsl:function name="f:to-size">
  <xsl:param name="length" as="xs:integer"/>
  <xsl:choose>
    <xsl:when test="$length lt 1000"><xsl:value-of select="$length"/> bytes</xsl:when>
    <xsl:when test="$length lt (1000*1000)"><xsl:value-of select="round-half-to-even($length div 1000,1)"/> KB</xsl:when>
    <xsl:when test="$length lt (1000*1000*1000)"><xsl:value-of select="round-half-to-even($length div (1000*1000),1)"/> MB</xsl:when>
    <xsl:when test="$length lt (1000*1000*1000*1000)"><xsl:value-of select="round-half-to-even($length div (1000*1000*1000),1)"/> GB</xsl:when>
    <xsl:when test="$length lt (1000*1000*1000*1000*1000)"><xsl:value-of select="round-half-to-even($length div (1000*1000*1000*1000),1)"/> TB</xsl:when>
    <xsl:otherwise>--</xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!-- A size in bytes, as a string (see f:to-size) -->
<xsl:function name="f:size" as="xs:string">
  <xsl:param name="bytes"/>
  <xsl:sequence select="string-join(f:to-size(xs:integer($bytes)), '')"/>
</xsl:function>

<!-- A fraction from 0 to 1 as a percentage -->
<xsl:function name="f:to-percent" as="xs:string">
  <xsl:param name="fraction"/>
  <xsl:sequence select="concat(format-number(number($fraction) * 100, '0.0'), '%')"/>
</xsl:function>

<!--
  Returns a pretty version of the URI pattern with variables identified and wrapped in a <var>

  Example: /search/{category}/{term} -> /search/<var>{category}</var>/<var>{term}</var>

  @param pattern The URI pattern
-->
<xsl:function name="f:pretty-pattern">
  <xsl:param name="pattern"/>
  <xsl:analyze-string select="$pattern" regex="\{{[^}}]+\}}">
    <xsl:matching-substring><var><xsl:value-of select="."/></var></xsl:matching-substring>
    <xsl:non-matching-substring><xsl:value-of select="."/></xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:function>

</xsl:stylesheet>