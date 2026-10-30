package org.pageseeder.bzadmin.log;

import java.time.Instant;
import java.util.List;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.log.LogFiles.LogFile;
import org.pageseeder.bzadmin.log.LogFiles.LogSource;

/**
 * Lists the log files that can be read (see {@link LogFiles}), with the XML of Bastille's
 * {@code ListAvailableLogFiles} and the ID of each file.
 *
 * <pre>{@code
 * <log-files origin="detected">
 *   <log-directory name="logs" path="/var/log/app" total="2" truncated="false">
 *     <log-file id="0123456789abcdef" name="app.log" size="1234" datetime="2026-10-23T01:02:03Z"/>
 *   </log-directory>
 *   <log-file id="..." name="server.out" size="..." datetime="..."/>
 * </log-files>
 * }</pre>
 *
 * <p>{@code origin} is {@code configured} (the global property {@value LogFiles#PROPERTY}),
 * {@code detected} (the file appenders of logback) or {@code none}. A registered file is a
 * {@code log-file} without a directory; a directory or file that does not exist is left out.
 *
 * <p>In JSON: {@code {"origin": "detected", "directories": [{"name": ..., "files": [...]}], "files": [...]}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListLogFiles implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    LogFiles logs = LogFiles.fromSettings();
    List<LogSource> sources = logs.list();
    out.startObject("log-files");
    out.field("origin", logs.origin().toString());
    out.startArray("directories", ContextOption.JSON_ONLY);
    for (LogSource source : sources) {
      if (!source.isDirectory()) continue;
      out.startObject("log-directory");
      out.field("name", source.name());
      out.field("path", source.path().toString());
      out.field("total", source.total());
      out.field("truncated", source.truncated());
      out.startArray("files", ContextOption.JSON_ONLY);
      for (LogFile file : source.files()) toOutput(file, out);
      out.endArray();
      out.endObject();
    }
    out.endArray();
    out.startArray("files", ContextOption.JSON_ONLY);
    for (LogSource source : sources) {
      if (!source.isDirectory()) for (LogFile file : source.files()) toOutput(file, out);
    }
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  private static void toOutput(LogFile file, OutputWriter out) {
    out.startObject("log-file");
    out.field("id", file.id());
    out.field("name", file.name());
    out.field("size", file.size());
    out.field("datetime", Instant.ofEpochMilli(file.modified()).toString());
    out.endObject();
  }

}
