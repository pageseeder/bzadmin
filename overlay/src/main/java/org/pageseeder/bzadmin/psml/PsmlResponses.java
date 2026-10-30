package org.pageseeder.bzadmin.psml;

import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;

/**
 * What the PSML generators share: the root of a request, the expected failures and the dates.
 */
final class PsmlResponses {

  private PsmlResponses() {
  }

  /**
   * @return the root named by the {@code root} parameter ({@value PsmlRepository#DEFAULT_ROOT} by
   *         default), or {@code null} if the repository has none.
   */
  static @Nullable PsmlRoot root(Request req) {
    return PsmlRepository.fromSettings().root(rootName(req));
  }

  static String rootName(Request req) {
    return req.parameter("root").asString().defaultValue(PsmlRepository.DEFAULT_ROOT);
  }

  /**
   * Writes {@code <no-psml-root name="..."/>}.
   *
   * @return 404
   */
  static Response noRoot(String name, OutputWriter out) {
    out.startObject("no-psml-root");
    out.field("name", name);
    out.endObject();
    return Response.status(ContentStatus.NOT_FOUND);
  }

  /**
   * Writes an expected failure as {@code <[element] root="..." path="..." problem="..."/>}.
   *
   * @return its status: 400 for an invalid path, 404 when not found, 403 when unreadable.
   */
  static Response failure(String element, String root, PsmlException ex, OutputWriter out) {
    out.startObject(element);
    out.field("root", root);
    out.field("path", ex.path());
    out.field("problem", ex.problem().code());
    out.endObject();
    switch (ex.problem()) {
      case INVALID_PATH: return Response.status(ContentStatus.BAD_REQUEST);
      case UNREADABLE: return Response.status(ContentStatus.FORBIDDEN);
      default: return Response.status(ContentStatus.NOT_FOUND);
    }
  }

  /**
   * @return the time in ISO 8601 format, in UTC.
   */
  static String toDateTime(long millis) {
    return Instant.ofEpochMilli(millis).toString();
  }

}
