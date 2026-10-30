package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.xslt.StylesheetLocation;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Lists the stylesheets that can be documented: the files of the XSLT folder of the application,
 * and the stylesheets of JAR overlays, at the path Berlioz maps them to.
 *
 * <p>Replaces Bastille's {@code ListCodeFiles}, which only lists the XSLT folder, with the same
 * tree of <code>file</code> elements. Each stylesheet has a <code>source</code> attribute:
 * "file", "classpath", or "override" for a file that hides a classpath stylesheet. A classpath
 * stylesheet provided by several JARs or classpath directories has a <code>copies</code> attribute
 * with their number: Berlioz uses the first one on the classpath.
 *
 * <p>In JSON, the children of a folder are in its {@code files} array.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListXsltFiles implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    Path folder = req.getEnvironment().getPrivateFile("xslt").toPath().toAbsolutePath().normalize();
    Folder root = new Folder("xslt");
    ClassLoader loader = StylesheetLocation.resolveApplicationClassLoader();
    try {
      for (Map.Entry<String, XsltFiles.Source> file : XsltFiles.list(folder, loader).entrySet()) {
        int copies = file.getValue() == XsltFiles.Source.FILE ? 0 : StylesheetLocation.countClasspathCopies(file.getKey());
        root.add(file.getKey(), new Stylesheet(file.getValue(), copies));
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    root.write("xslt", out);
    return Response.ok();
  }

  /**
   * A stylesheet of the tree: its source and the number of classpath resources at its path.
   */
  private static final class Stylesheet {

    final XsltFiles.Source source;

    final int copies;

    Stylesheet(XsltFiles.Source source, int copies) {
      this.source = source;
      this.copies = copies;
    }
  }

  /**
   * A folder of the tree, with its subfolders and stylesheets by name.
   */
  private static final class Folder {

    private final String name;

    private final Map<String, Folder> folders = new TreeMap<>();

    private final Map<String, Stylesheet> files = new TreeMap<>();

    Folder(String name) {
      this.name = name;
    }

    /**
     * @param path The path of the stylesheet, starting with the name of this folder.
     */
    void add(String path, Stylesheet source) {
      String relative = path.substring(path.indexOf('/') + 1);
      int slash = relative.indexOf('/');
      if (slash < 0) {
        this.files.put(relative, source);
      } else {
        this.folders.computeIfAbsent(relative.substring(0, slash), Folder::new).add(relative, source);
      }
    }

    void write(String path, OutputWriter out) {
      out.startObject("file");
      out.field("name", this.name);
      out.field("path", path);
      out.field("type", "folder");
      out.startArray("files", ContextOption.JSON_ONLY);
      for (Folder folder : this.folders.values()) {
        folder.write(path + '/' + folder.name, out);
      }
      for (Map.Entry<String, Stylesheet> file : this.files.entrySet()) {
        out.startObject("file");
        out.field("name", file.getKey());
        out.field("path", path + '/' + file.getKey());
        out.field("type", "file");
        out.field("source", file.getValue().source.code());
        if (file.getValue().copies > 1) out.field("copies", file.getValue().copies);
        out.endObject();
      }
      out.endArray();
      out.endObject();
    }
  }

}
