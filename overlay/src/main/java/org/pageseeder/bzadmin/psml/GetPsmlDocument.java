package org.pageseeder.bzadmin.psml;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.FieldOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Returns the metadata and the source of a PSML document, with the bounds of {@link PsmlDocument}
 * and {@link PsmlRoot#source(String)}.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code root}: the name of the root, {@value PsmlRepository#DEFAULT_ROOT} by default</li>
 *   <li>{@code path}: the path of the document (required)</li>
 * </ul>
 *
 * <pre>{@code
 * <psml-document root="psml" path="/content/home.psml" name="home.psml" size="1234"
 *                modified="2026-10-23T01:02:03Z" status="ok" element="document" type="default"
 *                level="portable" docid="home" title="Home" sections="2" fragments="3">
 *   <source size="1234" truncated="false">&lt;document ...</source>
 * </psml-document>
 * }</pre>
 *
 * <p>The attributes found by the parse are omitted when absent; {@code message}, {@code line} and
 * {@code column} are added when the status is {@code malformed}. The source is text, decoded as
 * UTF-8, never markup. An unknown root returns {@code <no-psml-root name="..."/>} with a 404, and a
 * path that is invalid, not found or unreadable
 * {@code <no-psml-document root="..." path="..." problem="..."/>} with a 400, 404 or 403.
 *
 * <p>In JSON: {@code {"root": "psml", ..., "source": {"size": 1234, "truncated": false, "text": "..."}}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetPsmlDocument implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    String name = PsmlResponses.rootName(req);
    String path = req.parameter("path").asString().required();
    PsmlRoot root = PsmlResponses.root(req);
    if (root == null) return PsmlResponses.noRoot(name, out);
    PsmlDocument document;
    PsmlSource source;
    try {
      document = root.document(path);
      source = root.source(path);
    } catch (PsmlException ex) {
      return PsmlResponses.failure("no-psml-document", name, ex, out);
    }
    out.startObject("psml-document");
    out.field("root", document.root());
    out.field("path", document.path());
    out.field("name", document.name());
    out.field("size", document.size());
    out.field("modified", PsmlResponses.toDateTime(document.modified()));
    out.field("status", document.status().code());
    if (document.status() == PsmlDocument.Status.MALFORMED) {
      out.optionalField("message", document.message());
      if (document.line() > 0) out.field("line", document.line());
      if (document.column() > 0) out.field("column", document.column());
    }
    out.optionalField("element", document.element());
    out.optionalField("type", document.type());
    out.optionalField("level", document.level());
    out.optionalField("docid", document.docid());
    out.optionalField("title", document.title());
    out.field("sections", document.sections());
    out.field("fragments", document.fragments());
    out.startObject("source");
    out.field("size", source.size());
    out.field("truncated", source.truncated());
    out.field("text", source.text(), FieldOption.XML_TEXT);
    out.endObject();
    out.endObject();
    return Response.ok();
  }

}
