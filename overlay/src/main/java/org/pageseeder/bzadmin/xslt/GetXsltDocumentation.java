package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.xml.transform.TransformerException;

import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.content.XmlGenerator;
import org.pageseeder.berlioz.xml.XmlWriter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Returns the documentation of a stylesheet of the application, following its imports and includes
 * into the XSLT folder and the classpath.
 *
 * <p>As Berlioz does for group stylesheets, a path without a file in the XSLT folder refers to the
 * stylesheet of a JAR overlay: <code>xslt/html/bzadmin.xsl</code> is
 * <code>META-INF/berlioz/xslt/html/bzadmin.xsl</code> on the classpath.
 *
 * <p>Replaces Bastille's {@code GetCodeDocumentation}, which uses Cobble and cannot follow
 * relative references in stylesheets imported by classpath URI. The XML is the same
 * {@code xsltdoc} format.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li><code>path</code>: the path of the stylesheet in the private folder of the application,
 *     starting with "xslt/" (as listed by {@link ListXsltFiles}).</li>
 * </ul>
 *
 * <h3>Responses</h3>
 * <ul>
 *   <li>200: the <code>xsltdoc</code> element;</li>
 *   <li>400: the path is not a stylesheet in the XSLT folder or on the classpath;</li>
 *   <li>422: the stylesheet cannot be documented, for example because an imported stylesheet is
 *     missing or not well-formed: an <code>xsltdoc-error</code> element with the path, the type of
 *     error and a message;</li>
 *   <li>500: an unexpected error, for example no XSLT 2.0 processor.</li>
 * </ul>
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetXsltDocumentation implements XmlGenerator {

  @Override
  public Response generate(Request req, XmlWriter xml) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied();
    String path = req.parameter("path").asString().required();
    XsltStylesheet stylesheet = XsltStylesheet.locate(req.getEnvironment(), path);
    XsltDocumenter documenter = new XsltDocumenter(stylesheet.folder, stylesheet.loader);
    try {
      String resource = stylesheet.resource;
      xml.xml(resource != null ? documenter.documentResource(resource) : documenter.document(stylesheet.file));
      return Response.ok();
    } catch (XsltDocumentationException ex) {
      xml.openElement("xsltdoc-error");
      xml.attribute("path", path);
      xml.attribute("type", ex.type().code());
      xml.text(ex.getMessage());
      xml.closeElement();
      return Response.status(ContentStatus.UNPROCESSABLE_ENTITY);
    } catch (TransformerException ex) {
      throw new IllegalStateException("Unable to document " + path, ex);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

}
