package org.pageseeder.bzadmin.psml;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Lists one folder of a PSML root: its folders, then its documents, by name ignoring case, one
 * range of entries at a time (see {@link PsmlRoot#list(String, int, int)} for the bounds).
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code root}: the name of the root, {@value PsmlRepository#DEFAULT_ROOT} by default</li>
 *   <li>{@code path}: the path of the folder, "/" by default</li>
 *   <li>{@code offset}: the index of the first entry, from 0</li>
 *   <li>{@code limit}: the number of entries, {@value PsmlRoot#MAX_ENTRIES} by default and at most</li>
 *   <li>{@code folders}: {@code true} to list the folders only (for the tree)</li>
 * </ul>
 *
 * <pre>{@code
 * <psml-folder root="psml" path="/content" offset="0" limit="1000" total="2" folders="1" truncated="false">
 *   <entry type="folder" name="news" path="/content/news" modified="2026-10-23T01:02:03Z"/>
 *   <entry type="document" name="home.psml" path="/content/home.psml" size="1234" modified="..."/>
 * </psml-folder>
 * }</pre>
 *
 * <p>{@code total} counts the entries of the folder, or its folders with {@code folders=true}; when
 * {@code truncated}, only those of the first {@value PsmlRoot#MAX_SCAN} directory entries read.
 * The root folder of a root whose directory does not exist is empty, with {@code exists="false"}.
 * Otherwise an unknown root returns {@code <no-psml-root name="..."/>} with a 404, and a path that
 * is invalid, not found or unreadable {@code <no-psml-folder root="..." path="..." problem="..."/>}
 * with a 400, 404 or 403.
 *
 * <p>In JSON: {@code {"root": "psml", "path": "/content", ..., "entries": [{"type": "folder", ...}]}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListPsmlFolder implements Generator {

  private static final String OFFSET = "offset";

  private static final String LIMIT = "limit";

  private static final String FOLDERS = "folders";

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    String name = PsmlResponses.rootName(req);
    String path = req.parameter("path").asString().defaultValue("/");
    int offset = req.parameter(OFFSET).asInt().clamp(0, Integer.MAX_VALUE).defaultValue(0);
    int limit = req.parameter(LIMIT).asInt().clamp(1, PsmlRoot.MAX_ENTRIES).defaultValue(PsmlRoot.MAX_ENTRIES);
    boolean foldersOnly = req.parameter(FOLDERS).asBoolean().defaultValue(false);
    PsmlRoot root = PsmlResponses.root(req);
    if (root == null) return PsmlResponses.noRoot(name, out);
    PsmlListing listing;
    try {
      listing = root.list(path, offset, limit);
    } catch (PsmlException ex) {
      if (ex.problem() == PsmlException.Problem.NOT_FOUND && isRoot(path) && !root.exists()) {
        return missing(root, limit, out);
      }
      return PsmlResponses.failure("no-psml-folder", name, ex, out);
    }
    out.startObject("psml-folder");
    out.field("root", listing.root());
    out.field("path", listing.path());
    out.field(OFFSET, listing.offset());
    out.field(LIMIT, limit);
    out.field("total", foldersOnly ? listing.folders() : listing.total());
    out.field(FOLDERS, listing.folders());
    out.field("truncated", listing.truncated());
    out.startArray("entries", ContextOption.JSON_ONLY);
    for (PsmlListing.Entry entry : listing.entries()) {
      if (foldersOnly && !entry.isFolder()) break;
      out.startObject("entry");
      out.field("type", entry.isFolder() ? "folder" : "document");
      out.field("name", entry.name());
      out.field("path", entry.path());
      if (!entry.isFolder()) out.field("size", entry.size());
      out.field("modified", PsmlResponses.toDateTime(entry.modified()));
      out.endObject();
    }
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  /**
   * The root folder of a root whose directory does not exist (a host without PSML): empty.
   */
  private static Response missing(PsmlRoot root, int limit, OutputWriter out) {
    out.startObject("psml-folder");
    out.field("root", root.name());
    out.field("path", "/");
    out.field("exists", false);
    out.field(OFFSET, 0);
    out.field(LIMIT, limit);
    out.field("total", 0);
    out.field(FOLDERS, 0);
    out.field("truncated", false);
    out.startArray("entries", ContextOption.JSON_ONLY);
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  private static boolean isRoot(String path) {
    return path.isEmpty() || path.equals("/");
  }

}
