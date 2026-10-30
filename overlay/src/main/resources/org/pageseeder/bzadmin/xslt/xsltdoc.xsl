<?xml version="1.0" encoding="UTF-8"?>
<!--
  Stylesheet generating the documentation for XSLT 2.0 stylesheet in XML format.

  This stylesheet parses the code comments and annotations attached to declaration elements.

  It also computes some metadata that can be useful to understand how the stylesheet works.

  Adapted for Berlioz Admin; historical provenance is documented in docs/design/xslt.md.
  Changes from the original implementation:
  - each stylesheet reachable through imports and includes is documented once, in document order,
    instead of once per import path;
  - the `path` of a stylesheet is relative to the application's XSLT folder, or the JAR entry for
    a stylesheet on the classpath, instead of characters removed from its URI;
  - XSLT 3.0 packages, modes, accumulators, packages used, exposed components and the global
    context item are documented, and other declarations are reported as unsupported.

  The documentation describes the sources, not the compiled stylesheet (`scope="source"`):
  `use-when` and shadow attributes are not evaluated, so every import and include with an `href`
  is followed and one with only a shadow `_href` is not; packages named by `xsl:use-package` are
  not loaded; implicit priorities follow the XSLT 2.0 rules.

  Referenced stylesheets are loaded with `document()`, so the caller's URI resolver decides which
  resources can be read.

  @author Christophe Lauret
-->
<xsl:stylesheet version="2.0"
              xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
               xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:f="local://functions"
exclude-result-prefixes="#all">

<!-- Output as XML without declaration as it may be injected inside other XML content -->
<xsl:output encoding="utf-8" method="xml" indent="yes" omit-xml-declaration="yes"/>

<!--
  URI of the application's XSLT folder, ending with '/'.
-->
<xsl:param name="xslt-folder" select="''" as="xs:string"/>

<!--
  Start of XSLT documentation.

  Process all XSLT declarations of the stylesheet and of every stylesheet it imports or includes,
  directly or indirectly.
-->
<xsl:template match="/">
<xsltdoc scope="source">
  <xsl:apply-templates select="f:stylesheets(., ())/*"/>
</xsltdoc>
</xsl:template>

<!--
  Returns the specified stylesheets followed by the stylesheets they import or include, depth first,
  skipping those already visited.

  @param documents The stylesheets to visit, in order.
  @param visited   The URIs of the stylesheets already visited.

  @return the stylesheets in document order, each once.
-->
<xsl:function name="f:stylesheets" as="document-node()*">
  <xsl:param name="documents" as="document-node()*"/>
  <xsl:param name="visited"   as="xs:anyURI*"/>
  <xsl:variable name="first" select="$documents[1]"/>
  <xsl:choose>
    <xsl:when test="empty($first)"/>
    <xsl:when test="base-uri($first) = $visited">
      <xsl:sequence select="f:stylesheets(subsequence($documents, 2), $visited)"/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:variable name="references" select="$first/*/(xsl:import|xsl:include)"/>
      <xsl:variable name="referenced" select="for $r in $references return document($r/@href, $r)"/>
      <xsl:variable name="descendants" select="f:stylesheets($referenced, ($visited, base-uri($first)))"/>
      <xsl:sequence select="$first, $descendants,
                            f:stylesheets(subsequence($documents, 2), ($visited, base-uri($first), for $d in $descendants return base-uri($d)))"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:function>

<!--
  Returns the path to display for the stylesheet at the specified URI.

  @param uri The URI of the stylesheet.

  @return the path relative to the XSLT folder prefixed with 'xslt/', the JAR file name and entry
          for a stylesheet in a JAR, or the URI otherwise.
-->
<xsl:function name="f:path" as="xs:string">
  <xsl:param name="uri" as="xs:string"/>
  <xsl:sequence select="if ($xslt-folder and starts-with($uri, $xslt-folder)) then concat('xslt/', substring-after($uri, $xslt-folder))
                        else if (starts-with($uri, 'jar:')) then concat(tokenize(substring-before($uri, '!/'), '/')[last()], '!/', substring-after($uri, '!/'))
                        else $uri"/>
</xsl:function>

<!--
  Document element.

  Report namespaces in use and process all XSLT declarations in stylesheet.

  <stylesheet
    id? = id
    extension-element-prefixes? = tokens
    exclude-result-prefixes? = tokens
    version = number
    xpath-default-namespace? = uri
    default-validation? = "preserve" | "strip"
    default-collation? = uri-list
    input-type-annotations? = "preserve" | "strip" | "unspecified">

    <doc>?

    <namespaces>?
      <namespace prefix = qname uri = uri />
    </namespace>

    other declarations

  </stylesheet>

-->
<xsl:template match="xsl:stylesheet|xsl:transform|xsl:package">
<stylesheet name="{tokenize(base-uri(), '/')[last()]}" path="{f:path(base-uri())}">
  <xsl:copy-of select="@* except self::xsl:package/@name"/>
  <!-- An XSLT 3.0 package: its name would replace the file name -->
  <xsl:if test="self::xsl:package">
    <xsl:attribute name="package" select="@name"/>
  </xsl:if>
  <xsl:sequence select="f:get-description(.)"/>
  <!-- Compute namespaces in scope -->
  <namespaces>
    <xsl:variable name="stylesheet" select="."/>
    <xsl:for-each select="in-scope-prefixes($stylesheet)">
      <namespace prefix="{.}" uri="{namespace-uri-for-prefix(., $stylesheet)}"/>
    </xsl:for-each>
  </namespaces>
  <xsl:apply-templates select="xsl:*" />
</stylesheet>
</xsl:template>

<!--
  A document that is not a stylesheet module, such as a simplified stylesheet (a literal result
  element as root): only its name and path are documented.
-->
<xsl:template match="/*[not(self::xsl:stylesheet or self::xsl:transform or self::xsl:package)]" priority="1">
<stylesheet name="{tokenize(base-uri(), '/')[last()]}" path="{f:path(base-uri())}" simplified="yes"/>
</xsl:template>

<!-- ========================================================================================== -->
<!-- DECLARATION ELEMENTS                                                                       -->
<!-- ========================================================================================== -->

<!--
  Documentation for attribute sets.

  <attribute-set
    name = qname
    use-attribute-sets? = qnames>

    <doc>?

    <attribute
      name = { qname }
      namespace? = { uri-reference }
      select? = expression
      separator? = { string }
      type? = qname
      validation? = "strict" | "lax" | "preserve" | "strip">
      {sequence-constructor}
    </attribute> *

  </attribute-set>

-->
<xsl:template match="xsl:attribute-set">
<attribute-set>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
  <xsl:for-each select="xsl:attribute">
    <attribute>
      <xsl:copy-of select="@*" />
    </attribute>
  </xsl:for-each>
</attribute-set>
</xsl:template>

<!--
  Documentation for character maps.

  <character-map
    name = qname
    use-character-maps? = qnames>

    <doc>?

    <output-character
      character = char
      string = string />*

  </character-map>

-->
<xsl:template match="xsl:character-map">
<character-map>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
  <xsl:for-each select="xsl:output-character">
    <output-character>
      <xsl:copy-of select="@*"/>
    </output-character>
  </xsl:for-each>
</character-map>
</xsl:template>

<!--
  Documentation for decimal formats used in the stylesheet.

  <decimal-format
    name? = qname
    decimal-separator? = char
    grouping-separator? = char
    infinity? = string
    minus-sign? = char
    NaN? = string
    percent? = char
    per-mille? = char
    zero-digit? = char
    digit? = char
    pattern-separator? = char>

    <doc />?

  </decimal-format>
-->
<xsl:template match="xsl:decimal-format">
<decimal-format>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</decimal-format>
</xsl:template>

<!--
  Documentation for imported schemas declarations.

  <import-schema
    namespace? = uri-reference
    schema-location? = uri-reference>
    <doc>?
  </import-schema>
-->
<xsl:template match="xsl:import-schema">
<import-schema>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</import-schema>
</xsl:template>

<!--
  Documentation for imported style sheets declaration.

  <import href = uri-reference>
    <doc>?
  </import>
-->
<xsl:template match="xsl:import">
<import>
  <xsl:copy-of select="@*"/>
  <xsl:if test="@href"><xsl:attribute name="path" select="f:path(base-uri(document(@href, .)))"/></xsl:if>
  <xsl:sequence select="f:followed(.)"/>
  <xsl:sequence select="f:get-description(.)"/>
</import>
</xsl:template>

<!--
  Documentation for included style sheets declarations.

  <include href = uri-reference>
    <doc/>?
  </inlcude>
-->
<xsl:template match="xsl:include">
<include>
  <xsl:copy-of select="@*"/>
  <xsl:if test="@href"><xsl:attribute name="path" select="f:path(base-uri(document(@href, .)))"/></xsl:if>
  <xsl:sequence select="f:followed(.)"/>
  <xsl:sequence select="f:get-description(.)"/>
</include>
</xsl:template>

<!--
  Documentation for key declarations.

  <key
    name = qname
    match = pattern
    use? = expression
    collation? = uri>
    <doc/>?
  </key>
-->
<xsl:template match="xsl:key">
<key>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</key>
</xsl:template>

<!--
  Documentation for namespace aliases.

  <namespace-alias
    stylesheet-prefix = prefix | "#default"
    result-prefix = prefix | "#default">
    <doc/>?
  </namespace-alias>
-->
<xsl:template match="xsl:namespace-alias">
<namespace-alias>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</namespace-alias>
</xsl:template>

<!--
  Documentation for output specifications.

  <output
    name? = qname
    method? = "xml" | "html" | "xhtml" | "text" | qname-but-not-ncname
    byte-order-mark? = "yes" | "no"
    cdata-section-elements? = qnames
    doctype-public? = string
    doctype-system? = string
    encoding? = string
    escape-uri-attributes? = "yes" | "no"
    include-content-type? = "yes" | "no"
    indent? = "yes" | "no"
    media-type? = string
    normalization-form? = "NFC" | "NFD" | "NFKC" | "NFKD" | "fully-normalized" | "none" | nmtoken
    omit-xml-declaration? = "yes" | "no"
    standalone? = "yes" | "no" | "omit"
    undeclare-prefixes? = "yes" | "no"
    use-character-maps? = qnames
    version? = nmtoken>

    <doc/>?

  </output>
-->
<xsl:template match="xsl:output">
<output>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</output>
</xsl:template>

<!--
  Documentation for templates.

  This template also include the implicit mode and priority for the template.

  <template
    match? = pattern
    name? = qname
    priority? = number
    mode? = tokens
    as? = sequence-type
    implicit-mode? = #default
    implicit-priority? = numbers
    >
    <doc>?
    <parameter>*
  </template>
-->
<xsl:template match="xsl:template">
<template>
  <xsl:copy-of select="@*"/>
  <xsl:if test="not(@mode)">
    <!-- XSLT 3.0: the default mode of the nearest ancestor that declares one -->
    <xsl:attribute name="implicit-mode" select="(ancestor::*/@default-mode, '#default')[1]"/>
  </xsl:if>
  <xsl:if test="not(@priority) and @match">
    <xsl:attribute name="implicit-priority"><xsl:value-of select="f:compute-priority(@match)" separator=","/></xsl:attribute>
  </xsl:if>
  <xsl:apply-templates select="xsl:param"/>
  <xsl:sequence select="f:get-description(.)"/>
</template>
</xsl:template>

<!--
  Documentation for functions.

  <function
    name = qname
    as? = sequence-type
    override? = "yes" | "no">
     <doc/>?
     <parameter >*
  </function>
-->
<xsl:template match="xsl:function">
<function>
  <xsl:copy-of select="@*"/>
  <xsl:apply-templates select="xsl:param"/>
  <xsl:sequence select="f:get-description(.)"/>
</function>
</xsl:template>

<!--
  Documentation for the strip space declaration.

  <strip-space elements = tokens >
    <doc/>?
  </strip-space>
-->
<xsl:template match="xsl:strip-space">
<strip-space>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</strip-space>
</xsl:template>

<!--
  Documentation for the preserv space declaration.

  <preserve-space elements = tokens>
    <doc/>?
  </preserve-space>
-->
<xsl:template match="xsl:preserve-space">
<preserve-space>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</preserve-space>
</xsl:template>

<!-- ========================================================================================== -->
<!-- XSLT 3.0 DECLARATION ELEMENTS                                                              -->
<!-- ========================================================================================== -->

<!--
  Documentation for mode declarations.

  <mode
    name? = eqname
    streamable? = boolean
    on-no-match? = "deep-copy" | "shallow-copy" | "deep-skip" | "shallow-skip" | "text-only-copy" | "fail"
    on-multiple-match? = "use-last" | "fail"
    warning-on-no-match? = boolean
    warning-on-multiple-match? = boolean
    typed? = boolean | "strict" | "lax" | "unspecified"
    visibility? = "public" | "private" | "final">
    <doc>?
  </mode>
-->
<xsl:template match="xsl:mode">
<mode>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</mode>
</xsl:template>

<!--
  Documentation for accumulators: the rules are summarised by their pattern and phase.

  <accumulator
    name = eqname
    initial-value = expression
    as? = sequence-type
    streamable? = boolean>
    <doc>?
    <rule match = pattern phase? = "start" | "end" select? = expression /> *
  </accumulator>
-->
<xsl:template match="xsl:accumulator">
<accumulator>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
  <xsl:for-each select="xsl:accumulator-rule">
    <rule>
      <xsl:copy-of select="@match|@phase|@select"/>
    </rule>
  </xsl:for-each>
</accumulator>
</xsl:template>

<!--
  Documentation for the global context item.

  <global-context-item as? = item-type use? = "required" | "optional" | "absent">
    <doc>?
  </global-context-item>
-->
<xsl:template match="xsl:global-context-item">
<global-context-item>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</global-context-item>
</xsl:template>

<!--
  Documentation for the packages used: the package is not loaded, so its components are not
  documented; the overriding and accepted components are listed by kind and name.

  <use-package name = uri package-version? = string>
    <doc>?
    <override kind = component-kind name = eqname />*
    <accept component = component-kind names = tokens visibility = visibility />*
  </use-package>
-->
<xsl:template match="xsl:use-package">
<use-package followed="no">
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
  <xsl:for-each select="xsl:override/xsl:*">
    <override kind="{local-name()}">
      <xsl:copy-of select="@name|@match|@mode"/>
    </override>
  </xsl:for-each>
  <xsl:for-each select="xsl:accept">
    <accept>
      <xsl:copy-of select="@*"/>
    </accept>
  </xsl:for-each>
</use-package>
</xsl:template>

<!--
  Documentation for the visibility of components exposed by a package.

  <expose component = "template" | "function" | "attribute-set" | "variable" | "mode" | "*"
    names = tokens
    visibility = "public" | "private" | "final" | "abstract">
    <doc>?
  </expose>
-->
<xsl:template match="xsl:expose">
<expose>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:get-description(.)"/>
</expose>
</xsl:template>

<!--
  Any other declaration in the XSLT namespace (e.g. a later XSLT version), reported by name only.
-->
<xsl:template match="xsl:*" priority="-1">
<unsupported name="{local-name()}"/>
</xsl:template>

<!-- ========================================================================================== -->
<!-- CONTEXTUAL ELEMENTS                                                                        -->
<!-- ========================================================================================== -->

<!--
  Documentation for XSLT parameters.

  <parameter
    name = qname
    select? = expression
    as? = sequence-type
    required? = "yes" | "no"
    tunnel? = "yes" | "no"
    content? = "yes">

  </parameter>

-->
<xsl:template match="xsl:param">
<parameter>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:content(.)"/>
  <xsl:sequence select="f:get-description(.)"/>
</parameter>
</xsl:template>

<!--
  Documentation for XSLT variables

  <variable
    name = qname
    select? = expression
    as? = sequence-type
    content? = "yes">
  </variable>
-->
<xsl:template match="xsl:variable">
<variable>
  <xsl:copy-of select="@*"/>
  <xsl:sequence select="f:content(.)"/>
  <xsl:sequence select="f:get-description(.)"/>
</variable>
</xsl:template>


<!-- FUNCTIONS =================================================================================================== -->

<!--
  Indicates whether an import or include is followed: only its `href` is, since shadow attributes
  and `use-when` are not evaluated.

  @param reference The `xsl:import` or `xsl:include` element.

  @return a `followed` attribute.
-->
<xsl:function name="f:followed" as="attribute(followed)">
  <xsl:param name="reference" as="element()"/>
  <xsl:attribute name="followed" select="if ($reference/@href) then 'yes' else 'no'"/>
</xsl:function>

<!--
  Indicates whether a parameter or variable is set by its sequence constructor instead of `select`.
  Its type and value are not computed: that would need the compiled stylesheet.

  @param declaration The `xsl:param` or `xsl:variable` element.

  @return a `content` attribute, or nothing if it has a `select` or no content.
-->
<xsl:function name="f:content" as="attribute(content)?">
  <xsl:param name="declaration" as="element()"/>
  <xsl:if test="not($declaration/@select) and $declaration/(* | text()[normalize-space()])">
    <xsl:attribute name="content" select="'yes'"/>
  </xsl:if>
</xsl:function>

<!--

-->
<xsl:function name="f:get-description">
  <xsl:param name="e" as="element()"/>
  <xsl:variable name="comment" select="$e/preceding-sibling::node()[not(self::text())][1][self::comment()]"/>
  <xsl:if test="$comment">
  <doc>
    <xsl:sequence select="f:analyse-comment($comment)"/>
  </doc>
  </xsl:if>
</xsl:function>

<xsl:function name="f:analyse-comment">
  <xsl:param name="c" as="comment()"/>
  <xsl:variable name="buffer">
    <xsl:analyze-string regex="\s+@([\w\-]+)(\s+(.+))?" select="$c">
      <xsl:matching-substring>
        <xsl:sequence select="f:analyse-annotation(regex-group(1), regex-group(3))"/>
      </xsl:matching-substring>
      <xsl:non-matching-substring>
        <text><xsl:value-of select="." /></text>
      </xsl:non-matching-substring>
    </xsl:analyze-string>
  </xsl:variable>
  <description>
    <xsl:sequence select="f:analyse-description(string-join($buffer//text, ' '))"/>
  </description>
  <xsl:for-each select="$buffer//*[not(self::text)]">
    <xsl:sequence select="."/>
  </xsl:for-each>
</xsl:function>

<xsl:function name="f:analyse-annotation">
  <xsl:param name="name" as="xs:string"/>
  <xsl:param name="text" as="xs:string?"/>
  <xsl:choose>
    <xsl:when test="$name = 'param' and contains($text, ' ')">
      <parameter name="{f:trim(substring-before($text, ' '))}" description="{f:trim(substring-after($text, ' '))}"/>
    </xsl:when>
    <xsl:when test="$name = 'context'and contains($text, ' ')">
      <context select="{f:trim(substring-before($text, ' '))}" description="{f:trim(substring-after($text, ' '))}"/>
    </xsl:when>
    <xsl:when test="$name = 'see' and contains($text, ' ')">
      <see href="{f:trim(substring-before($text, ' '))}" description="{f:trim(substring-after($text, ' '))}"/>
    </xsl:when>
    <xsl:when test="$name = 'see'">
      <see href="{f:trim($text)}" description="{f:trim($text)}"/>
    </xsl:when>
    <xsl:when test="$name = 'version'">
      <version name="{f:trim($text)}"/>
    </xsl:when>
    <xsl:when test="$name = 'author'">
      <author name="{f:trim($text)}"/>
    </xsl:when>
    <xsl:when test="$name = 'return' or $name = 'returns'">
      <return description="{f:trim($text)}"/>
    </xsl:when>
    <xsl:when test="$name = 'context'">
      <context select="{f:trim($text)}"/>
    </xsl:when>
    <xsl:when test="$name = 'public' or $name = 'private'">
      <visibility is="{$name}"/>
    </xsl:when>
    <xsl:otherwise>
      <annotation name="{$name}">
        <xsl:if test="$text"><xsl:attribute name="text" select="f:trim($text)"/></xsl:if>
      </annotation>
    </xsl:otherwise>
  </xsl:choose>
</xsl:function>

<xsl:function name="f:analyse-description">
  <xsl:param name="description" as="xs:string"/>
  <xsl:variable name="analysed" select="f:parse-lines(tokenize($description, '\n'))" as="element()*"/>
  <xsl:for-each-group select="$analysed" group-adjacent="boolean(self::p)">
    <xsl:choose>
      <xsl:when test="self::p">
        <p><xsl:sequence select="f:beautify(string-join(current-group()/text(), ' '))"/></p>
      </xsl:when>
      <xsl:otherwise>
        <xsl:for-each-group select="current-group()" group-adjacent="boolean(self::li)">
          <xsl:choose>
            <xsl:when test="self::li">
              <xsl:element name="{if (@numbered) then 'ol' else 'ul'}">
                <xsl:for-each select="current-group()">
                  <li><xsl:sequence select="f:beautify(text())"/></li>
                </xsl:for-each>
              </xsl:element>
            </xsl:when>
            <xsl:otherwise>
              <xsl:for-each-group select="current-group()" group-adjacent="boolean(self::pre)">
                <xsl:if test="self::pre">
                <pre><xsl:for-each select="current-group()">
                  <xsl:value-of select="."/><xsl:text>&#xa;</xsl:text>
                </xsl:for-each></pre>
                </xsl:if>
              </xsl:for-each-group>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:for-each-group>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:for-each-group>
</xsl:function>

<xsl:function name="f:trim">
  <xsl:param name="t" as="xs:string"/>
  <xsl:sequence select="if (matches($t, '\S+')) then replace($t, '^\s*(.+?)\s*$', '$1') else ''"/>
</xsl:function>

<xsl:function name="f:linkify">
  <xsl:param name="t" as="xs:string"/>
  <xsl:analyze-string regex="(https?://\S+)" select="$t">
    <xsl:matching-substring>
      <a href="{.}"><xsl:value-of select="."/></a>
    </xsl:matching-substring>
    <xsl:non-matching-substring>
      <xsl:value-of select="."/>
    </xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:function>

<xsl:function name="f:parse-lines" as="element()*">
  <xsl:param name="lines" as="xs:string*"/>
  <xsl:sequence select="f:parse-line($lines, 1, count($lines))"/>
</xsl:function>

<xsl:function name="f:parse-line" as="element()*">
  <xsl:param name="lines"      as="xs:string*"/>
  <xsl:param name="i"    as="xs:integer"/>
  <xsl:param name="total" as="xs:integer"/>
  <xsl:if test="not($i gt $total)">
    <xsl:variable name="line" select="$lines[$i]"/>
    <xsl:choose>
      <xsl:when test="not(matches($line, '\S'))">
        <break/>
      </xsl:when>
      <xsl:when test="matches($line, '^\s*(-|\+|\*|\d+\.)\s.+')">
        <li>
          <xsl:if test="matches($line, '^\s*(\d+\.)\s.+')"><xsl:attribute name="numbered">true</xsl:attribute></xsl:if>
          <xsl:if test="not(matches($lines[$i -1], '^\s*(-|\+|\*|\d+\.)\s.+'))"><xsl:attribute name="start">true</xsl:attribute></xsl:if>
          <xsl:sequence select="f:trim(replace($line, '^\s*(-|\+|\*|\d+\.)\s+(.+)$', '$2'))"/>
        </li>
      </xsl:when>
      <xsl:when test="matches($line, '^\s{4}')">
        <pre><xsl:sequence select="substring($line, 5)"/></pre>
      </xsl:when>
      <xsl:otherwise>
        <p><xsl:sequence select="f:trim($line)"/></p>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:sequence select="f:parse-line($lines, $i+1, $total)"/>
  </xsl:if>
</xsl:function>

<!--
  Beautify the text by using markdown style inline markup such as codes, links and emphases.

  @param t the string to beautify
  @return the corresponding set of nodes
-->
<xsl:function name="f:beautify" as="node()*">
  <xsl:param name="t" as="xs:string"/>
  <xsl:analyze-string select="$t" flags="x" regex='(_(.*?)_)
                                                 | (\*(.*?)\*)
                                                 | (__(.*?)__)
                                                 | (\*\*(.*?)\*\*)
                                                 | (`(.*?)`)
                                                 | (\[(.*?)\]\((.*?)\))
                                                 | (&lt;((https?://|mailto:).*?)>)'>
   <xsl:matching-substring>
     <xsl:choose>
       <!-- Simple emphases -->
       <xsl:when test="regex-group(1)">
         <em><xsl:sequence select="f:beautify(regex-group(2))"/></em>
       </xsl:when>
       <xsl:when test="regex-group(3)">
         <em><xsl:sequence select="f:beautify(regex-group(4))"/></em>
       </xsl:when>
       <!-- Strong emphases -->
       <xsl:when test="regex-group(5)">
         <strong><xsl:sequence select="f:beautify(regex-group(6))"/></strong>
       </xsl:when>
       <xsl:when test="regex-group(7)">
         <strong><xsl:sequence select="f:beautify(regex-group(8))"/></strong>
       </xsl:when>
       <!-- Code -->
       <xsl:when test="regex-group(9)">
         <code><xsl:sequence select="regex-group(10)"/></code>
       </xsl:when>
       <!-- Links -->
       <xsl:when test="regex-group(11)">
         <a href="{regex-group(13)}"><xsl:sequence select="regex-group(12)"/></a>
       </xsl:when>
       <xsl:when test="regex-group(14)">
         <a href="{regex-group(15)}"><xsl:sequence select="regex-group(15)"/></a>
       </xsl:when>
     </xsl:choose>
   </xsl:matching-substring>
   <xsl:non-matching-substring>
    <xsl:value-of select="."/>
   </xsl:non-matching-substring>
  </xsl:analyze-string>
</xsl:function>

<!--
  Computes the implicit priority based on XSLT 2.0 conflict resolution rules.

  @see http://www.w3.org/TR/xslt20/#conflict

  @param pattern The value of the match attribute in the template

  @return the priority or sequence of priorities
-->
<xsl:function name="f:compute-priority" as="xs:string*">
<xsl:param name="pattern"/>
<xsl:variable name="alternatives" select="f:split-pattern(string($pattern))"/>
<xsl:choose>
  <xsl:when test="count($alternatives) gt 1">
    <xsl:sequence select="for $i in $alternatives return f:compute-priority(f:trim($i))"/>
  </xsl:when>
  <xsl:otherwise>
    <xsl:choose>
      <!-- Lowest priority -->
      <xsl:when test="$pattern = '/'">-0.5</xsl:when>
      <xsl:when test="$pattern = 'node()'">-0.5</xsl:when>
      <xsl:when test="$pattern = '*'">-0.5</xsl:when>
      <xsl:when test="$pattern = '@*'">-0.5</xsl:when>
      <xsl:when test="$pattern = 'element()'">-0.5</xsl:when>
      <xsl:when test="$pattern = 'attribute()'">-0.5</xsl:when>
      <xsl:when test="$pattern = 'element(*)'">-0.5</xsl:when>
      <xsl:when test="$pattern = 'attribute(*)'">-0.5</xsl:when>
      <!-- Wild cards and namespaces -->
      <xsl:when test="matches($pattern, '^[\w\-]+:\*$')">-0.25</xsl:when>
      <xsl:when test="matches($pattern, '^\*:[\w\-]+$')">-0.25</xsl:when>
      <!-- Specify a predicate -->
      <xsl:when test="contains($pattern, '[')">0.5</xsl:when>
      <!-- Specify ancestry -->
      <xsl:when test="contains($pattern, '/')">0.5</xsl:when>
      <!-- Default to '0' -->
      <xsl:otherwise>0</xsl:otherwise>
    </xsl:choose>
  </xsl:otherwise>
</xsl:choose>
</xsl:function>

<!--
  Splits a pattern into its alternatives, on each `|` or `union` outside predicates, parentheses
  and string literals.

  @param pattern The value of the match attribute in the template

  @return the alternatives, or the pattern itself if it has only one
-->
<xsl:function name="f:split-pattern" as="xs:string*">
<xsl:param name="pattern" as="xs:string"/>
<xsl:variable name="tokens" as="xs:string*">
  <xsl:analyze-string select="$pattern" regex="'[^']*'|&quot;[^&quot;]*&quot;|[\[\]()|]|\sunion\s|[^'&quot;\[\]()|\s]+|.">
    <xsl:matching-substring><xsl:sequence select="."/></xsl:matching-substring>
  </xsl:analyze-string>
</xsl:variable>
<xsl:sequence select="f:split-tokens($tokens, 0, '')"/>
</xsl:function>

<!--
  Joins the tokens of a pattern into alternatives, tracking the depth of brackets and parentheses.

  @param tokens  The remaining tokens
  @param depth   The current depth of brackets and parentheses
  @param current The alternative being built

  @return the alternatives
-->
<xsl:function name="f:split-tokens" as="xs:string*">
<xsl:param name="tokens" as="xs:string*"/>
<xsl:param name="depth" as="xs:integer"/>
<xsl:param name="current" as="xs:string"/>
<xsl:variable name="token" select="$tokens[1]"/>
<xsl:choose>
  <xsl:when test="empty($tokens)">
    <xsl:sequence select="$current"/>
  </xsl:when>
  <xsl:when test="$depth = 0 and ($token = '|' or normalize-space($token) = 'union')">
    <xsl:sequence select="$current, f:split-tokens(subsequence($tokens, 2), 0, '')"/>
  </xsl:when>
  <xsl:otherwise>
    <xsl:variable name="delta" select="if ($token = ('[', '(')) then 1 else if ($token = (']', ')')) then -1 else 0"/>
    <xsl:sequence select="f:split-tokens(subsequence($tokens, 2), $depth + $delta, concat($current, $token))"/>
  </xsl:otherwise>
</xsl:choose>
</xsl:function>

</xsl:stylesheet>
