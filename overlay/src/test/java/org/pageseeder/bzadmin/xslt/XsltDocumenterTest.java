package org.pageseeder.bzadmin.xslt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import net.sf.saxon.xpath.XPathFactoryImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pageseeder.bzadmin.xslt.XsltDocumentationException.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Documentation of stylesheets in a XSLT folder and on the classpath.
 *
 * <p>The test classpath has the overlay and the fixture as JARs, so classpath stylesheets have
 * {@code jar:} URLs, as in a host application.
 */
final class XsltDocumenterTest {

  private static final ClassLoader LOADER = XsltDocumenterTest.class.getClassLoader();

  @TempDir
  Path temp;

  private static Path fixtureFolder() {
    return Paths.get(System.getProperty("bzadmin.fixtures.webapp"), "WEB-INF/xslt");
  }

  // Classpath -----------------------------------------------------------------------------------

  @Test
  void documentsClasspathImportsOnceInDocumentOrder() throws Exception {
    String xml = new XsltDocumenter(fixtureFolder(), LOADER).document(fixtureFolder().resolve("html/fixture.xsl"));
    // sibling.xsl is imported by main.xsl and nested/nested.xsl, but documented once
    assertEquals(List.of("fixture.xsl", "main.xsl", "sibling.xsl", "nested.xsl", "leaf.xsl"), attributes(xml, "name"));
    assertTrue(xml.contains("name=\"fx:leaf\""), "Declarations of the deepest stylesheet");
  }

  @Test
  void showsPathsInTheXsltFolderAndJar() throws Exception {
    List<String> paths = attributes(new XsltDocumenter(fixtureFolder(), LOADER).document(fixtureFolder().resolve("html/fixture.xsl")), "path");
    assertEquals("xslt/html/fixture.xsl", paths.get(0));
    for (String entry : new String[]{"main.xsl", "sibling.xsl", "nested/nested.xsl", "nested/leaf.xsl"}) {
      assertTrue(paths.stream().anyMatch(p -> p.matches("bzadmin-fixture-[^/]+\\.jar!/META-INF/berlioz/xslt/fixture/" + entry)), entry + " in " + paths);
    }
  }

  @Test
  void documentsTheOverlayFromItsJar() throws Exception {
    Path stub = write("html/bzadmin.xsl", stylesheet("<xsl:import href='classpath:META-INF/berlioz/xslt/html/bzadmin.xsl'/>"));
    String xml = new XsltDocumenter(this.temp, LOADER).document(stub);
    List<String> paths = attributes(xml, "path");
    assertEquals(11, paths.size(), paths.toString());
    assertEquals(11, paths.stream().distinct().count(), "Each stylesheet once");
    assertTrue(paths.stream().anyMatch(p -> p.matches("pso-bzadmin-[^/]+\\.jar!/META-INF/berlioz/xslt/html/bzadmin/_functions.xsl")), paths.toString());
  }

  @Test
  void documentsTheOverlayFromTheClasspathWithoutXsltFolder() throws Exception {
    XsltDocumenter documenter = new XsltDocumenter(this.temp.resolve("none"), LOADER);
    List<String> paths = attributes(documenter.documentResource("META-INF/berlioz/xslt/html/bzadmin.xsl"), "path");
    assertEquals(10, paths.size(), paths.toString());
    assertTrue(paths.get(0).matches("pso-bzadmin-[^/]+\\.jar!/META-INF/berlioz/xslt/html/bzadmin.xsl"), paths.toString());
  }

  @Test
  void failsOnMissingClasspathResource() {
    XsltDocumenter documenter = new XsltDocumenter(this.temp, LOADER);
    XsltDocumentationException ex = assertThrows(XsltDocumentationException.class,
        () -> documenter.documentResource("META-INF/berlioz/xslt/none.xsl"));
    assertEquals(Type.NOT_FOUND, ex.type(), ex.getMessage());
  }

  @Test
  void failsOnMissingClasspathStylesheet() throws Exception {
    Path stub = write("stub.xsl", stylesheet("<xsl:import href='classpath:META-INF/berlioz/xslt/none.xsl'/>"));
    assertFailure(stub, Type.NOT_FOUND, "'classpath:META-INF/berlioz/xslt/none.xsl' was not found on the classpath");
  }

  @Test
  void deniesJarOutsideTheClasspath() throws Exception {
    Path jar = this.temp.resolve("other.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      out.putNextEntry(new JarEntry("other.xsl"));
      out.write(stylesheet("").getBytes("UTF-8"));
    }
    Path stub = write("xslt/stub.xsl", stylesheet("<xsl:import href='jar:" + jar.toUri() + "!/other.xsl'/>"));
    assertFailure(stub, Type.NOT_FOUND, "'other.jar!/other.xsl' was not found on the classpath");
  }

  @Test
  void documentsClasspathDirectory() throws Exception {
    write("classes/META-INF/berlioz/xslt/dir/main.xsl", stylesheet("<xsl:import href='sub/child.xsl'/>"));
    write("classes/META-INF/berlioz/xslt/dir/sub/child.xsl", stylesheet(""));
    Path stub = write("xslt/stub.xsl", stylesheet("<xsl:import href='classpath:META-INF/berlioz/xslt/dir/main.xsl'/>"));
    try (URLClassLoader loader = new URLClassLoader(new URL[]{this.temp.resolve("classes").toUri().toURL()}, null)) {
      String xml = new XsltDocumenter(this.temp.resolve("xslt"), loader).document(stub);
      assertEquals(List.of("stub.xsl", "main.xsl", "child.xsl"), attributes(xml, "name"));
    }
  }

  @Test
  void deniesFilesOutsideClasspathDirectory() throws Exception {
    write("outside.xsl", stylesheet(""));
    write("classes/META-INF/berlioz/xslt/dir/main.xsl", stylesheet("<xsl:import href='../../../../../outside.xsl'/>"));
    Path stub = write("xslt/stub.xsl", stylesheet("<xsl:import href='classpath:META-INF/berlioz/xslt/dir/main.xsl'/>"));
    try (URLClassLoader loader = new URLClassLoader(new URL[]{this.temp.resolve("classes").toUri().toURL()}, null)) {
      XsltDocumenter documenter = new XsltDocumenter(this.temp.resolve("xslt"), loader);
      XsltDocumentationException ex = assertThrows(XsltDocumentationException.class, () -> documenter.document(stub));
      assertEquals(Type.DENIED, ex.type(), ex.getMessage());
    }
  }

  // Filesystem ----------------------------------------------------------------------------------

  @Test
  void documentsRelativeFileImports() throws Exception {
    Path a = write("a.xsl", stylesheet("<xsl:import href='sub/b.xsl'/>"));
    write("sub/b.xsl", stylesheet("<xsl:include href='../c.xsl'/>"));
    write("c.xsl", stylesheet(""));
    List<String> paths = attributes(new XsltDocumenter(this.temp, LOADER).document(a), "path");
    assertEquals(List.of("xslt/a.xsl", "xslt/sub/b.xsl", "xslt/c.xsl"), paths);
  }

  @Test
  void documentsCyclesOnce() throws Exception {
    Path a = write("a.xsl", stylesheet("<xsl:import href='b.xsl'/>"));
    write("b.xsl", stylesheet("<xsl:import href='a.xsl'/>"));
    assertEquals(List.of("a.xsl", "b.xsl"), attributes(new XsltDocumenter(this.temp, LOADER).document(a), "name"));
  }

  @Test
  void failsOnMissingImport() throws Exception {
    Path a = write("a.xsl", stylesheet("<xsl:import href='missing.xsl'/>"));
    assertFailure(a, Type.NOT_FOUND, "'xslt/missing.xsl' was not found");
  }

  @Test
  void failsOnMalformedImport() throws Exception {
    Path a = write("a.xsl", stylesheet("<xsl:import href='broken.xsl'/>"));
    write("broken.xsl", "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n<oops>");
    assertFailure(a, Type.MALFORMED, "'xslt/broken.xsl' is not well-formed XML (line ");
  }

  @Test
  void failsOnMalformedStylesheet() throws Exception {
    Path a = write("a.xsl", "<xsl:stylesheet");
    assertFailure(a, Type.MALFORMED, "'xslt/a.xsl' is not well-formed XML");
  }

  @Test
  void deniesFilesOutsideTheFolder() throws Exception {
    Path folder = Files.createDirectories(this.temp.resolve("xslt"));
    Path outside = write("outside.xsl", stylesheet(""));
    Path relative = write("xslt/relative.xsl", stylesheet("<xsl:import href='../outside.xsl'/>"));
    Path absolute = write("xslt/absolute.xsl", stylesheet("<xsl:import href='" + outside.toUri() + "'/>"));
    XsltDocumenter documenter = new XsltDocumenter(folder, LOADER);
    for (Path stylesheet : new Path[]{relative, absolute}) {
      XsltDocumentationException ex = assertThrows(XsltDocumentationException.class, () -> documenter.document(stylesheet));
      assertEquals(Type.DENIED, ex.type(), ex.getMessage());
    }
    assertThrows(XsltDocumentationException.class, () -> documenter.document(outside));
  }

  @Test
  void deniesSymbolicLinkOutsideTheFolder() throws Exception {
    Path folder = Files.createDirectories(this.temp.resolve("xslt"));
    Path outside = write("outside.xsl", stylesheet(""));
    Files.createSymbolicLink(folder.resolve("link.xsl"), outside);
    Path a = write("xslt/a.xsl", stylesheet("<xsl:import href='link.xsl'/>"));
    XsltDocumentationException ex = assertThrows(XsltDocumentationException.class, () -> new XsltDocumenter(folder, LOADER).document(a));
    assertEquals(Type.DENIED, ex.type(), ex.getMessage());
  }

  @Test
  void deniesFilesOtherThanStylesheets() throws Exception {
    Path a = write("a.xsl", stylesheet("<xsl:import href='data.xml'/>"));
    write("data.xml", stylesheet(""));
    assertFailure(a, Type.DENIED, "'data.xml' is not a stylesheet");
  }

  // Declarations --------------------------------------------------------------------------------

  /**
   * The common XSLT 2.0 declarations are each documented, none reported as unsupported.
   */
  @Test
  void documentsCommonXslt2Declarations() throws Exception {
    Path a = write("a.xsl", "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'"
        + " xmlns:xs='http://www.w3.org/2001/XMLSchema' xmlns:x='urn:x'>"
        + "<xsl:import-schema namespace='urn:x'/>"
        + "<xsl:output method='html'/>"
        + "<xsl:strip-space elements='*'/><xsl:preserve-space elements='pre'/>"
        + "<xsl:decimal-format name='x:euro' decimal-separator=','/>"
        + "<xsl:namespace-alias stylesheet-prefix='x' result-prefix='#default'/>"
        + "<xsl:character-map name='x:map'><xsl:output-character character='a' string='b'/></xsl:character-map>"
        + "<xsl:attribute-set name='x:set'><xsl:attribute name='class'>c</xsl:attribute></xsl:attribute-set>"
        + "<xsl:key name='x:id' match='*' use='@id'/>"
        + "<!-- The input --><xsl:param name='p' as='xs:string' select=\"'v'\"/>"
        + "<xsl:variable name='v' select='1'/>"
        + "<xsl:template match='x:a[1]|x:b'><xsl:param name='t' tunnel='yes'/></xsl:template>"
        + "<xsl:template name='x:named'/>"
        + "<xsl:function name='x:f' as='xs:string'><xsl:param name='s'/><xsl:sequence select='$s'/></xsl:function>"
        + "</xsl:stylesheet>");
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(a));
    for (String name : new String[]{"import-schema", "output", "strip-space", "preserve-space", "decimal-format",
        "namespace-alias", "character-map", "attribute-set", "key", "parameter", "variable", "function"}) {
      assertEquals("1", xpath(doc, "count(/xsltdoc/stylesheet/" + name + ")"), name);
    }
    assertEquals("x:euro", xpath(doc, "/xsltdoc/stylesheet/decimal-format/@name"));
    assertEquals("b", xpath(doc, "/xsltdoc/stylesheet/character-map/output-character/@string"));
    assertEquals("The input", xpath(doc, "normalize-space(/xsltdoc/stylesheet/parameter/doc/description)"));
    assertEquals("0.5,0", xpath(doc, "/xsltdoc/stylesheet/template[@match]/@implicit-priority"));
    assertEquals("#default", xpath(doc, "/xsltdoc/stylesheet/template[@match]/@implicit-mode"));
    assertEquals("t", xpath(doc, "/xsltdoc/stylesheet/template[@match]/parameter/@name"));
    assertEquals("s", xpath(doc, "/xsltdoc/stylesheet/function/parameter/@name"));
    assertEquals("0", xpath(doc, "count(//unsupported)"));
    assertEquals("source", xpath(doc, "/xsltdoc/@scope"), "Never presented as a compiled model");
  }

  /**
   * A pattern is split into alternatives only on a `|` or `union` outside predicates and literals.
   */
  @Test
  void computesImplicitPriorityPerAlternative() throws Exception {
    Path a = write("a.xsl", "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:template match='a[b|c]'/>"
        + "<xsl:template match=\"a[@v='|']\"/>"
        + "<xsl:template match='a[b|c] | d'/>"
        + "<xsl:template match='a union x:*'/>"
        + "<xsl:template match='a/union'/>"
        + "</xsl:stylesheet>");
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(a));
    assertEquals("0.5", xpath(doc, "/xsltdoc/stylesheet/template[1]/@implicit-priority"));
    assertEquals("0.5", xpath(doc, "/xsltdoc/stylesheet/template[2]/@implicit-priority"));
    assertEquals("0.5,0", xpath(doc, "/xsltdoc/stylesheet/template[3]/@implicit-priority"));
    assertEquals("0,-0.25", xpath(doc, "/xsltdoc/stylesheet/template[4]/@implicit-priority"));
    assertEquals("0.5", xpath(doc, "/xsltdoc/stylesheet/template[5]/@implicit-priority"));
  }

  /**
   * A parameter or variable set by its content, not by `select`, is flagged; its value is not computed.
   */
  @Test
  void flagsParametersAndVariablesSetByTheirContent() throws Exception {
    Path a = write("a.xsl", "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:param name='p'>text</xsl:param>"
        + "<xsl:param name='q' select='1'/>"
        + "<xsl:variable name='v'><a/></xsl:variable>"
        + "<xsl:variable name='w'> <!-- empty --> </xsl:variable>"
        + "<xsl:template name='t'><xsl:param name='l'><xsl:value-of select='1'/></xsl:param></xsl:template>"
        + "</xsl:stylesheet>");
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(a));
    assertEquals("yes", xpath(doc, "/xsltdoc/stylesheet/parameter[@name='p']/@content"));
    assertEquals("0", xpath(doc, "count(/xsltdoc/stylesheet/parameter[@name='q']/@content)"));
    assertEquals("yes", xpath(doc, "/xsltdoc/stylesheet/variable[@name='v']/@content"));
    assertEquals("0", xpath(doc, "count(/xsltdoc/stylesheet/variable[@name='w']/@content)"));
    assertEquals("yes", xpath(doc, "/xsltdoc/stylesheet/template/parameter[@name='l']/@content"));
  }

  /**
   * XSLT 3.0 packages, modes, accumulators, visibility and static parameters, from the sources.
   */
  @Test
  void documentsXslt3Declarations() throws Exception {
    Path p = write("p.xsl", "<xsl:package name='urn:p' package-version='1.2' version='3.0' default-mode='m'"
        + " declared-modes='yes' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:use-package name='urn:lib' package-version='1.*'>"
        + "<xsl:override><xsl:template name='t'/><xsl:function name='f'/></xsl:override>"
        + "<xsl:accept component='function' names='*' visibility='private'/>"
        + "</xsl:use-package>"
        + "<xsl:expose component='template' names='*' visibility='public'/>"
        + "<xsl:global-context-item as='document-node()' use='required'/>"
        + "<!-- The main mode --><xsl:mode name='m' on-no-match='shallow-copy' visibility='public'/>"
        + "<xsl:accumulator name='count' initial-value='0' as='xs:integer'>"
        + "<xsl:accumulator-rule match='section' phase='start' select='$value + 1'/></xsl:accumulator>"
        + "<xsl:param name='debug' static='yes' select='false()'/>"
        + "<xsl:template match='section' visibility='public'/>"
        + "<xsl:function name='f:g' visibility='final' xmlns:f='urn:f'/>"
        + "<xsl:item-type name='t' as='xs:string'/>"
        + "</xsl:package>");
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(p));
    assertEquals("p.xsl", xpath(doc, "/xsltdoc/stylesheet/@name"), "The file name, not the package name");
    assertEquals("urn:p", xpath(doc, "/xsltdoc/stylesheet/@package"));
    assertEquals("1.2", xpath(doc, "/xsltdoc/stylesheet/@package-version"));
    assertEquals("no", xpath(doc, "/xsltdoc/stylesheet/use-package[@name='urn:lib']/@followed"), "Packages used are not loaded");
    assertEquals("template t, function f", xpath(doc, "string-join(//use-package/override/concat(@kind, ' ', @name), ', ')"));
    assertEquals("private", xpath(doc, "//use-package/accept/@visibility"));
    assertEquals("public", xpath(doc, "/xsltdoc/stylesheet/expose/@visibility"));
    assertEquals("required", xpath(doc, "/xsltdoc/stylesheet/global-context-item/@use"));
    assertEquals("shallow-copy", xpath(doc, "/xsltdoc/stylesheet/mode[@name='m']/@on-no-match"));
    assertEquals("The main mode", xpath(doc, "normalize-space(/xsltdoc/stylesheet/mode/doc/description)"));
    assertEquals("section start", xpath(doc, "/xsltdoc/stylesheet/accumulator[@name='count']/rule/concat(@match, ' ', @phase)"));
    assertEquals("yes", xpath(doc, "/xsltdoc/stylesheet/parameter[@name='debug']/@static"));
    assertEquals("m", xpath(doc, "/xsltdoc/stylesheet/template/@implicit-mode"), "Default mode of the package");
    assertEquals("public", xpath(doc, "/xsltdoc/stylesheet/template/@visibility"));
    assertEquals("final", xpath(doc, "/xsltdoc/stylesheet/function/@visibility"));
    assertEquals("item-type", xpath(doc, "/xsltdoc/stylesheet/unsupported/@name"), "Later declarations by name only");
    assertEquals("", xpath(doc, "normalize-space(/xsltdoc/stylesheet/unsupported)"), "Without their content");
  }

  /**
   * Static conditions and shadow attributes are not evaluated: every href is followed, and a
   * reference with only a shadow attribute is not.
   */
  @Test
  void followsHrefsWithoutEvaluatingStaticExpressions() throws Exception {
    Path a = write("a.xsl", "<xsl:stylesheet version='3.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
        + "<xsl:param name='base' static='yes' select=\"'b'\"/>"
        + "<xsl:import href='b.xsl' use-when='false()'/>"
        + "<xsl:include _href='{$base}.xsl'/>"
        + "</xsl:stylesheet>");
    write("b.xsl", stylesheet(""));
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(a));
    assertEquals("a.xsl b.xsl", xpath(doc, "string-join(/xsltdoc/stylesheet/@name, ' ')"));
    assertEquals("yes", xpath(doc, "//import/@followed"));
    assertEquals("false()", xpath(doc, "//import/@use-when"));
    assertEquals("no", xpath(doc, "//include/@followed"));
    assertEquals("{$base}.xsl", xpath(doc, "//include/@_href"));
  }

  @Test
  void documentsSimplifiedStylesheetByName() throws Exception {
    Path a = write("a.xsl", "<html xsl:version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'><xsl:value-of select='1'/></html>");
    Document doc = parse(new XsltDocumenter(this.temp, LOADER).document(a));
    assertEquals("a.xsl", xpath(doc, "/xsltdoc/stylesheet/@name"));
    assertEquals("yes", xpath(doc, "/xsltdoc/stylesheet/@simplified"));
    assertEquals("0", xpath(doc, "count(/xsltdoc/stylesheet/*)"));
  }

  /**
   * A stylesheet in two classpath locations: the first is documented, as Berlioz uses it.
   */
  @Test
  void documentsTheFirstOfDuplicateClasspathStylesheets() throws Exception {
    write("first/META-INF/berlioz/xslt/dup.xsl", stylesheet("<xsl:variable name='first'/>"));
    write("second/META-INF/berlioz/xslt/dup.xsl", stylesheet("<xsl:variable name='second'/>"));
    URL[] urls = {this.temp.resolve("first").toUri().toURL(), this.temp.resolve("second").toUri().toURL()};
    try (URLClassLoader loader = new URLClassLoader(urls, null)) {
      Document doc = parse(new XsltDocumenter(this.temp.resolve("none"), loader).documentResource("META-INF/berlioz/xslt/dup.xsl"));
      assertEquals("first", xpath(doc, "/xsltdoc/stylesheet/variable/@name"));
    }
  }

  // Network and parser --------------------------------------------------------------------------

  @Test
  void deniesNetworkReferencesWithoutConnecting() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      String url = "http://127.0.0.1:" + server.getLocalPort();
      Path a = write("a.xsl", stylesheet("<xsl:import href='" + url + "/x.xsl'/>"));
      assertFailure(a, Type.DENIED, "is not a local stylesheet reference");
      assertNoConnection(server);
    }
  }

  @Test
  void loadsNoExternalDtdOrEntity() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      String url = "http://127.0.0.1:" + server.getLocalPort();
      Path a = write("a.xsl", "<!DOCTYPE xsl:stylesheet SYSTEM '" + url + "/x.dtd' [\n"
          + "  <!ENTITY external SYSTEM '" + url + "/entity'>\n  <!ENTITY internal 'expanded'>\n]>\n"
          + stylesheet("<xsl:variable name='v' select=\"'&internal;'\">&external;</xsl:variable>"));
      String xml = new XsltDocumenter(this.temp, LOADER).document(a);
      assertTrue(xml.contains("'expanded'"), "Internal entities are expanded");
      assertNoConnection(server);
    }
  }

  // Limits and concurrency ----------------------------------------------------------------------

  @Test
  void failsOnTooManyStylesheets() throws Exception {
    int count = XsltDocumentationResolver.MAX_STYLESHEETS + 1;
    for (int i = 0; i < count; i++) {
      write("s" + i + ".xsl", stylesheet(i + 1 < count ? "<xsl:import href='s" + (i + 1) + ".xsl'/>" : ""));
    }
    assertFailure(this.temp.resolve("s0.xsl"), Type.LIMIT, "More than " + XsltDocumentationResolver.MAX_STYLESHEETS + " stylesheets");
  }

  @Test
  void documentsConcurrently() throws Exception {
    XsltDocumenter documenter = new XsltDocumenter(fixtureFolder(), LOADER);
    Path stylesheet = fixtureFolder().resolve("html/fixture.xsl");
    String expected = documenter.document(stylesheet);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int i = 0; i < 32; i++) results.add(executor.submit(() -> documenter.document(stylesheet)));
      for (Future<String> result : results) assertEquals(expected, result.get());
    } finally {
      executor.shutdownNow();
    }
  }

  // Helpers -------------------------------------------------------------------------------------

  private Path write(String path, String content) throws IOException {
    Path file = this.temp.resolve(path);
    Files.createDirectories(file.getParent());
    return Files.writeString(file, content);
  }

  private static String stylesheet(String declarations) {
    return "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" + declarations + "</xsl:stylesheet>";
  }

  private void assertFailure(Path stylesheet, Type type, String message) {
    XsltDocumentationException ex = assertThrows(XsltDocumentationException.class,
        () -> new XsltDocumenter(this.temp, LOADER).document(stylesheet));
    assertEquals(type, ex.type(), ex.getMessage());
    assertTrue(ex.getMessage().contains(message), ex.getMessage());
  }

  private static void assertNoConnection(ServerSocket server) throws IOException {
    server.setSoTimeout(200);
    assertThrows(SocketTimeoutException.class, server::accept, "No connection to " + server.getLocalPort());
  }

  private static Document parse(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private static String xpath(Document doc, String expression) throws Exception {
    return new XPathFactoryImpl().newXPath().evaluate(expression, doc);
  }

  /**
   * @return the values of an attribute of the documented stylesheets, in order.
   */
  private static List<String> attributes(String xml, String name) throws Exception {
    NodeList stylesheets = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new InputSource(new StringReader(xml))).getElementsByTagName("stylesheet");
    List<String> values = new ArrayList<>();
    for (int i = 0; i < stylesheets.getLength(); i++) {
      values.add(((Element) stylesheets.item(i)).getAttribute(name));
    }
    return values;
  }

}
