package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pageseeder.bzadmin.xslt.XsltDocumenter;

/**
 * The XSLT pages render the output of {@code GetXsltDocumentation} and {@code ListXsltFiles}: the
 * scope of the documentation, XSLT 3.0 declarations and duplicate classpath stylesheets; and of
 * {@code GetXsltSource}: escaped source to highlight.
 */
final class XsltDocXsltTest {

  @TempDir
  Path temp;

  @Test
  void statesThatTheDocumentationIsReadFromTheSources() throws Exception {
    String html = documentation("<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:template match='/'/></xsl:stylesheet>");
    assertTrue(html.contains("Read from the stylesheet sources, not from the compiled stylesheet"), html);
    assertTrue(html.contains("Stylesheet: a.xsl"), html);
    assertTrue(html.indexOf("Stylesheet: a.xsl") < html.indexOf("<div class=\"panel\">"), html);
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Documentation</a>"), html);
    assertTrue(html.contains(">Source</a>"), html);
    assertFalse(html.contains("View source"), html);
  }

  @Test
  void rendersXslt3Declarations() throws Exception {
    String html = documentation("<xsl:package name='urn:p' package-version='1.2' version='3.0' default-mode='m'"
        + " xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:use-package name='urn:lib'><xsl:override><xsl:template name='t'/></xsl:override></xsl:use-package>"
        + "<xsl:import _href='{$base}.xsl'/>"
        + "<xsl:mode name='m' on-no-match='shallow-copy'/>"
        + "<xsl:accumulator name='count' initial-value='0'><xsl:accumulator-rule match='section' phase='start'/></xsl:accumulator>"
        + "<xsl:param name='debug' static='yes'/>"
        + "<xsl:template match='section' visibility='public'/>"
        + "<xsl:item-type name='t' as='xs:string'/>"
        + "</xsl:package>");
    assertTrue(html.contains("Package: a.xsl"), html);
    assertTrue(html.contains("Package <code>urn:p</code> version <code>1.2</code>"), html);
    assertTrue(html.contains("<h3>Packages used</h3>"), html);
    assertTrue(html.contains("overrides template <code>t</code>"), html);
    assertTrue(html.contains("(shadow attribute, not followed)"), html);
    assertTrue(html.contains("on-no-match=<code>shallow-copy</code>"), html);
    assertTrue(html.contains("<h3>Accumulators</h3>"), html);
    assertTrue(html.contains(">static</span>"), html);
    assertTrue(html.contains("<span class=\"implied\">m</span>"), "Default mode of the package: " + html);
    assertTrue(html.contains("Visibility: <var>public</var>"), html);
    assertTrue(html.contains("Not documented: <code>xsl:item-type</code>"), html);
  }

  @Test
  void showsWhenAValueIsSetByItsContent() throws Exception {
    String html = documentation("<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:variable name='v'><a/></xsl:variable>"
        + "<xsl:variable name='w' select='42'/>"
        + "<xsl:template match='/'><xsl:param name='l'>text</xsl:param></xsl:template>"
        + "</xsl:stylesheet>");
    assertTrue(html.contains("<td><span class=\"implied\" title=\"Set by its content, not a select expression\">(content)</span></td>"
        + "<td></td>"), html);
    assertTrue(html.contains("<td>42</td>"), html);
    assertTrue(html.contains("<td><var>l</var></td><td><code></code></td>"
        + "<td><span class=\"implied\" title=\"Set by its content, not a select expression\">(content)</span></td>"), html);
  }

  @Test
  void showsDuplicateClasspathStylesheets() throws Exception {
    String html = page("bzadmin-xslt", "xslt", "org.pageseeder.bzadmin.xslt.ListXsltFiles",
        "<file name='xslt' path='xslt' type='folder'><file name='html' path='xslt/html' type='folder'>"
        + "<file name='dup.xsl' path='xslt/html/dup.xsl' type='file' source='classpath' copies='2'/>"
        + "<file name='single.xsl' path='xslt/html/single.xsl' type='file' source='classpath'/>"
        + "</file></file>");
    assertTrue(html.contains("is in 2 places on the classpath"), html);
    assertTrue(html.contains(">×2</span>"), html);
    assertFalse(html.contains("×1"), html);
  }

  @Test
  void summarisesTheStylesheetsInKeyMetrics() throws Exception {
    String html = page("bzadmin-xslt", "xslt", "org.pageseeder.bzadmin.xslt.ListXsltFiles",
        "<file name='xslt' path='xslt' type='folder'><file name='html' path='xslt/html' type='folder'>"
        + "<file name='dup.xsl' path='xslt/html/dup.xsl' type='file' source='classpath' copies='2'/>"
        + "<file name='own.xsl' path='xslt/html/own.xsl' type='file' source='file'/>"
        + "<file name='other.xsl' path='xslt/html/other.xsl' type='file' source='override'/>"
        + "</file></file>");
    assertTrue(html.contains("<dt>Stylesheets</dt><dd class=\"key-metric-value\">3</dd>"
        + "<dd class=\"key-metric-detail\">2 in the XSLT folder, 1 from JAR overlays</dd>"), html);
    assertTrue(html.contains("<dt>Overrides</dt><dd class=\"key-metric-value\">1</dd>"), html);
    assertTrue(html.contains("<div class=\"key-metric warning\"><dt>Duplicates</dt><dd class=\"key-metric-value\">1</dd>"), html);
    assertTrue(html.contains("<dt>XSLT cache</dt><dd class=\"key-metric-value muted\" title=\"Not available\"><span role=\"img\" aria-label=\"Not available\">—</span></dd>"), "No Berlioz control");
  }

  @Test
  void documentationAnchorsAreUniqueAndAvailableWithoutScript() throws Exception {
    Files.writeString(this.temp.resolve("b.xsl"),
        "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:param name='p'/><xsl:variable name='v' select='1'/>"
        + "<xsl:function name='f:shared'/><xsl:template match='b'/></xsl:stylesheet>");
    String html = documentation("<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:include href='b.xsl'/><xsl:function name='f:shared'/>"
        + "<xsl:function name='f:shared'><xsl:param name='arg'/></xsl:function>"
        + "<xsl:template match='a'/></xsl:stylesheet>");
    Set<String> ids = new HashSet<>();
    Matcher id = Pattern.compile(" id=\"([^\"]+)\"").matcher(html);
    while (id.find()) assertTrue(ids.add(id.group(1)), "Duplicate ID: " + id.group(1));
    Matcher link = Pattern.compile("href=\"#([^\"]+)\"").matcher(html);
    while (link.find()) assertTrue(ids.contains(link.group(1)), "Missing anchor: " + link.group(1));
    assertFalse(html.contains("hidden=\"hidden\""), "Documentation must work without script");
    assertTrue(html.contains("aria-label=\"XSLT documentation\""), html);
  }

  @Test
  void listsGlobalParametersAndVariablesInTheirOwnTabs() throws Exception {
    Files.writeString(this.temp.resolve("b.xsl"),
        "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:param name='p'/><xsl:template match='b'/></xsl:stylesheet>");
    String html = documentation("<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:include href='b.xsl'/><xsl:template match='a'/></xsl:stylesheet>");
    assertTrue(html.contains("<li><a href=\"#xslt-parameters\">Parameters</a></li>"), html);
    assertTrue(html.contains("<section class=\"xsltdoc\" id=\"xslt-parameters\"><h2>Global parameters</h2>"), html);
    String summary = html.substring(html.indexOf("id=\"xslt-summary\""), html.indexOf("id=\"xslt-functions\""));
    assertFalse(summary.contains("Global parameters"), "Not repeated in the summary: " + summary);
    assertFalse(html.contains("#xslt-variables"), "No tab without variables");
    assertFalse(html.contains("id=\"xslt-variables\""), html);
  }

  private String documentation(String stylesheet) throws Exception {
    Path file = Files.writeString(this.temp.resolve("a.xsl"), stylesheet);
    String xml = new XsltDocumenter(this.temp, XsltDocXsltTest.class.getClassLoader()).document(file);
    return page("bzadmin-xslt-doc", "xslt-doc", "org.pageseeder.bzadmin.xslt.GetXsltDocumentation", xml);
  }

  @Test
  void escapesTheSourceOfAStylesheet() throws Exception {
    String html = page("bzadmin-xslt-source", "xslt-source", "org.pageseeder.bzadmin.xslt.GetXsltSource",
        "<xslt-source path='xslt/html/a b.xsl' name='a b.xsl' location='classpath' size='2000000' truncated='true'>"
        + "&lt;script&gt;alert(1)&lt;/script&gt;</xslt-source>");
    assertTrue(html.contains("<xml-source><pre><code>&lt;script&gt;alert(1)&lt;/script&gt;</code></pre></xml-source>"), html);
    assertFalse(html.contains("<script>alert"), html);
    assertTrue(html.contains("Only the start of this stylesheet is shown"), html);
    assertTrue(html.indexOf("Stylesheet: a b.xsl") < html.indexOf("<section class=\"panel\">"), html);
    assertTrue(html.indexOf("<code>xslt/html/a b.xsl</code>") < html.indexOf("<section class=\"panel\">"), html);
    assertTrue(html.contains("class=\"choice active\" aria-current=\"page\">Source</a>"), html);
    assertTrue(html.contains(">classpath</span>"), html);
    assertTrue(html.contains("href=\"/ctx/bzadmin/xslt/doc.html?path=xslt%2Fhtml%2Fa%20b.xsl\""), "Link to its documentation");
  }

  @Test
  void linksTheDocumentationToTheSource() throws Exception {
    String html = page("bzadmin-xslt-doc", "xslt-doc", "org.pageseeder.bzadmin.xslt.GetXsltDocumentation",
        "<xsltdoc-error path='xslt/html/a.xsl' type='not-found'>Not found</xsltdoc-error>");
    assertTrue(html.contains("href=\"/ctx/bzadmin/xslt/source.html?path=xslt%2Fhtml%2Fa.xsl\""), html);
  }

  private static String page(String service, String name, String generator, String content) throws Exception {
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/xslt.html'/><path context='/ctx' info='/bzadmin/xslt' extension='.html'/>"
        + "<berlioz version='0.14.4' mode='test'/></header>"
        + "<content generator='" + generator + "' name='" + name + "' target='main' status='ok'>"
        + content + "</content></root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}
