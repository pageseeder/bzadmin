package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.FieldOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.io.Utf8;

/**
 * Returns the source of a stylesheet of the application, from the XSLT folder or, when the folder
 * has no file at this path, from a JAR overlay (as {@link GetXsltDocumentation} resolves it).
 *
 * <p>At most {@value #MAX_SOURCE_BYTES} bytes are read, decoded as UTF-8 with malformed input
 * replaced. The source is text, never markup.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li><code>path</code>: the path of the stylesheet, starting with "xslt/" (as listed by
 *     {@link ListXsltFiles}).</li>
 * </ul>
 *
 * <pre>{@code
 * <xslt-source path="xslt/html/bzadmin.xsl" name="bzadmin.xsl" location="classpath"
 *              size="1234" truncated="false">&lt;xsl:stylesheet ...</xslt-source>
 * }</pre>
 *
 * <p>{@code location} is {@code file} or {@code classpath}; {@code size} is omitted when the
 * classpath does not report it. A path that is not a stylesheet in the XSLT folder or on the
 * classpath is a 400.
 *
 * <p>In JSON: {@code {"path": "xslt/html/bzadmin.xsl", ..., "truncated": false, "text": "..."}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetXsltSource implements Generator {

  /** The maximum number of bytes of source read. */
  public static final int MAX_SOURCE_BYTES = 1024 * 1024;

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    String path = req.parameter("path").asString().required();
    XsltStylesheet stylesheet = XsltStylesheet.locate(req.getEnvironment(), path);
    long size;
    byte[] bytes;
    try {
      Path file = stylesheet.file;
      if (file != null) {
        size = Files.size(file);
        try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
          bytes = in.readNBytes(MAX_SOURCE_BYTES + 1);
        }
      } else {
        URL url = stylesheet.loader.getResource(stylesheet.resource);
        if (url == null) throw new IOException("No resource " + stylesheet.resource);
        URLConnection connection = url.openConnection();
        // Do not keep the JAR file open
        connection.setUseCaches(false);
        size = connection.getContentLengthLong();
        try (InputStream in = connection.getInputStream()) {
          bytes = in.readNBytes(MAX_SOURCE_BYTES + 1);
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Unable to read " + path, ex);
    }
    boolean truncated = bytes.length > MAX_SOURCE_BYTES;
    int length = Math.min(bytes.length, MAX_SOURCE_BYTES);
    out.startObject("xslt-source");
    out.field("path", path);
    out.field("name", path.substring(path.lastIndexOf('/') + 1));
    out.field("location", stylesheet.file != null ? XsltFiles.Source.FILE.code() : XsltFiles.Source.CLASSPATH.code());
    if (size >= 0) out.field("size", size);
    out.field("truncated", truncated);
    out.field("text", Utf8.decode(ByteBuffer.wrap(bytes, 0, length), truncated), FieldOption.XML_TEXT);
    out.endObject();
    return Response.ok();
  }

}
