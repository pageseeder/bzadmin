package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.jspecify.annotations.Nullable;
import org.pageseeder.bzadmin.xslt.XsltDocumentationException.Type;
import org.xml.sax.SAXParseException;

/**
 * Generates the documentation of a stylesheet in the XSLT folder of the application or on the
 * classpath, including every stylesheet it imports or includes, from the XSLT folder or the classpath.
 *
 * <p>The stylesheets are only read as XML by {@code xsltdoc.xsl}, never compiled or executed.
 *
 * <p>Thread-safe: the documentation stylesheet is compiled once, and each documentation uses its
 * own transformer and resolver.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class XsltDocumenter {

  /** The documentation stylesheet (taken over from Cobble). */
  private static final String STYLESHEET = "org/pageseeder/bzadmin/xslt/xsltdoc.xsl";

  // Volatile only publishes the reference: Templates are thread-safe (JAXP)
  @SuppressWarnings("java:S3077")
  private static volatile @Nullable Templates templates;

  private final Path folder;

  private final ClassLoader loader;

  /**
   * @param folder The XSLT folder of the application (it may not exist).
   * @param loader The classloader of the application, for classpath references.
   */
  public XsltDocumenter(Path folder, ClassLoader loader) {
    this.folder = folder;
    this.loader = loader;
  }

  /**
   * Indicates whether the specified file can be documented.
   *
   * @param name The name of the file.
   * @return <code>true</code> for ".xsl" or ".xslt" files.
   */
  public static boolean isSupported(String name) {
    return name.endsWith(".xsl") || name.endsWith(".xslt");
  }

  /**
   * Generates the documentation of a stylesheet.
   *
   * @param stylesheet The stylesheet to document, in the XSLT folder.
   *
   * @return the documentation as XML (an <code>xsltdoc</code> element)
   *
   * @throws XsltDocumentationException If the stylesheet cannot be documented for an expected reason.
   * @throws TransformerException       If the documentation stylesheet fails otherwise.
   * @throws IOException                If the XSLT folder cannot be accessed.
   */
  public String document(Path stylesheet) throws TransformerException, IOException {
    XsltDocumentationResolver resolver = new XsltDocumentationResolver(this.folder, this.loader);
    return document(resolver.source(stylesheet), resolver);
  }

  /**
   * Generates the documentation of a stylesheet on the classpath.
   *
   * @param resource The resource name of the stylesheet to document.
   *
   * @return the documentation as XML (an <code>xsltdoc</code> element)
   *
   * @throws XsltDocumentationException If the stylesheet cannot be documented for an expected reason.
   * @throws TransformerException       If the documentation stylesheet fails otherwise.
   * @throws IOException                If the XSLT folder cannot be accessed.
   */
  public String documentResource(String resource) throws TransformerException, IOException {
    XsltDocumentationResolver resolver = new XsltDocumentationResolver(this.folder, this.loader);
    return document(resolver.resolve("classpath:" + resource, null), resolver);
  }

  private static String document(Source source, XsltDocumentationResolver resolver) throws TransformerException {
    Transformer transformer = templates().newTransformer();
    transformer.setURIResolver(resolver);
    transformer.setErrorListener(new Quiet());
    transformer.setParameter("xslt-folder", resolver.folderUri());
    StringWriter xml = new StringWriter();
    try {
      transformer.transform(source, new StreamResult(xml));
    } catch (TransformerException ex) {
      throw expected(ex, resolver);
    }
    return xml.toString();
  }

  /**
   * Returns the expected exception that caused the transformer exception, or the exception itself.
   */
  private static TransformerException expected(TransformerException ex, XsltDocumentationResolver resolver) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof XsltDocumentationException) return (XsltDocumentationException) cause;
      if (cause instanceof SAXParseException) {
        SAXParseException parse = (SAXParseException) cause;
        String path = parse.getSystemId();
        try {
          path = resolver.path(new URL(path));
        } catch (IOException | RuntimeException ignored) {
          // Keep the system ID
        }
        return new XsltDocumentationException(Type.MALFORMED, "'" + path + "' is not well-formed XML (line "
            + parse.getLineNumber() + ", column " + parse.getColumnNumber() + "): " + parse.getMessage(), parse);
      }
    }
    return ex;
  }

  private static Templates templates() throws TransformerException {
    Templates compiled = templates;
    if (compiled == null) {
      synchronized (XsltDocumenter.class) {
        compiled = templates;
        if (compiled == null) {
          URL url = XsltDocumenter.class.getClassLoader().getResource(STYLESHEET);
          if (url == null) throw new TransformerException("Missing documentation stylesheet " + STYLESHEET);
          TransformerFactory factory = TransformerFactory.newInstance();
          factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
          compiled = factory.newTemplates(new StreamSource(url.toString()));
          templates = compiled;
        }
      }
    }
    return compiled;
  }

  /**
   * Leaves errors to the exception thrown by the transformer instead of reporting them on the
   * console.
   */
  private static final class Quiet implements ErrorListener {

    @Override
    public void warning(TransformerException ex) {
      // Ignored
    }

    @Override
    public void error(TransformerException ex) {
      // Reported by the exception
    }

    @Override
    public void fatalError(TransformerException ex) throws TransformerException {
      throw ex;
    }
  }

}
