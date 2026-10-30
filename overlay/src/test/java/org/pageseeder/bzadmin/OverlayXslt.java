package org.pageseeder.bzadmin;

import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.TransformerFactoryImpl;

/**
 * Transforms Berlioz XML with the overlay stylesheet loaded from the overlay JAR, as Berlioz does
 * for the 'bzadmin' group, and fails on any XSLT warning or error.
 */
final class OverlayXslt {

  /** The overlay's group stylesheet, which Berlioz loads from the classpath. */
  static final String STYLESHEET = "META-INF/berlioz/xslt/html/bzadmin.xsl";

  private static Templates templates;

  private OverlayXslt() {
  }

  /**
   * @return the URL of the overlay stylesheet on the test classpath.
   */
  static URL stylesheet() {
    URL url = OverlayXslt.class.getClassLoader().getResource(STYLESHEET);
    if (url == null) throw new IllegalStateException("Overlay stylesheet not on the classpath: " + STYLESHEET);
    return url;
  }

  /**
   * Transforms the specified XML resource (relative to this class) into HTML.
   *
   * @param resource The path to the Berlioz XML, relative to this class.
   * @return the HTML output
   * @throws TransformerException if the stylesheet reports any warning or error.
   */
  static String transformResource(String resource) throws TransformerException {
    URL xml = OverlayXslt.class.getResource(resource);
    if (xml == null) throw new IllegalArgumentException("No such fixture: " + resource);
    return transform(new StreamSource(xml.toString()));
  }

  /**
   * Transforms the Berlioz XML into HTML.
   *
   * @param xml The Berlioz XML (a {@code root} element with a header and content).
   * @return the HTML output
   * @throws TransformerException if the stylesheet reports any warning or error.
   */
  static String transform(Source xml) throws TransformerException {
    Collector errors = new Collector();
    Transformer transformer = templates().newTransformer();
    transformer.setErrorListener(errors);
    StringWriter html = new StringWriter();
    transformer.transform(xml, new StreamResult(html));
    errors.check();
    assertMeterLabels(html.toString());
    assertSemantics(html.toString());
    return html.toString();
  }

  /** Every rendered meter must reference exactly one visible, text-only metric label. */
  private static void assertMeterLabels(String html) {
    Matcher meters = Pattern.compile("<meter\\b([^>]*)>").matcher(html);
    while (meters.find()) {
      Matcher reference = Pattern.compile("aria-labelledby=\"([^\"]+)\"").matcher(meters.group(1));
      assertTrue(reference.find(), "Meter without an accessible name: " + meters.group());
      String id = reference.group(1);
      Matcher labels = Pattern.compile("<span id=\"" + Pattern.quote(id) + "\">([^<]+)</span>").matcher(html);
      assertTrue(labels.find(), "Missing visible label for meter: " + id);
      Matcher ids = Pattern.compile("\\bid=\"" + Pattern.quote(id) + "\"").matcher(html);
      assertEquals(1, ids.results().count(), "Duplicate meter label ID: " + id);
    }
  }

  /** Check names on navigation/dialogs and avoid turning every table into a landmark. */
  private static void assertSemantics(String html) {
    java.util.Set<String> navigationNames = new java.util.HashSet<>();
    Matcher navs = Pattern.compile("<nav\\b([^>]*)>").matcher(html);
    while (navs.find()) {
      Matcher label = Pattern.compile("aria-label=\"([^\"]+)\"").matcher(navs.group(1));
      assertTrue(label.find(), "Navigation without a name: " + navs.group());
      assertTrue(navigationNames.add(label.group(1)), "Repeated navigation name: " + label.group(1));
    }
    Matcher headings = Pattern.compile("<h([1-6])\\b[^>]*>(.*?)</h\\1>", Pattern.DOTALL).matcher(html);
    while (headings.find()) {
      assertTrue(!headings.group(2).contains("<p "), "Flow content inside heading: " + headings.group());
    }
    Matcher dialogs = Pattern.compile("<dialog\\b([^>]*)>").matcher(html);
    while (dialogs.find()) {
      Matcher label = Pattern.compile("aria-labelledby=\"([^\"]+)\"").matcher(dialogs.group(1));
      assertTrue(label.find(), "Dialog without a heading reference: " + dialogs.group());
      assertTrue(html.contains("id=\"" + label.group(1) + "\""), "Missing dialog heading");
    }
    assertTrue(!Pattern.compile("<time\\b[^>]*aria-label").matcher(html).find(), "Do not name the time role");
    assertTrue(!Pattern.compile("class=\"table-scroll\"[^>]*role=\"region\"").matcher(html).find(),
        "Scroll wrappers must not add landmarks");
  }

  private static synchronized Templates templates() throws TransformerException {
    if (templates == null) {
      TransformerFactoryImpl factory = new TransformerFactoryImpl();
      Collector errors = new Collector();
      factory.setErrorListener(errors);
      templates = factory.newTemplates(new StreamSource(stylesheet().toString()));
      errors.check();
    }
    return templates;
  }

  /**
   * Collects warnings and errors so that the test fails on any of them.
   */
  private static final class Collector implements ErrorListener {

    private final List<TransformerException> errors = new ArrayList<>();

    @Override
    public void warning(TransformerException ex) {
      this.errors.add(ex);
    }

    @Override
    public void error(TransformerException ex) {
      this.errors.add(ex);
    }

    @Override
    public void fatalError(TransformerException ex) throws TransformerException {
      throw ex;
    }

    void check() throws TransformerException {
      if (!this.errors.isEmpty()) {
        TransformerException first = this.errors.get(0);
        throw new TransformerException(this.errors.size() + " XSLT warning(s)/error(s), first: "
            + first.getMessageAndLocation(), first);
      }
    }
  }

}
