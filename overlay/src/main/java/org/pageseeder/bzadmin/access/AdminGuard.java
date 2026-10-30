package org.pageseeder.bzadmin.access;

import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;

/**
 * The check of the verdict of {@link AdminFilter} by the generators of the admin services, at the
 * start of their {@code generate} (and {@code getETag} for cacheable ones):
 *
 * <pre>{@code
 * if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
 * }</pre>
 *
 * <p>It holds no rule, so it cannot drift from the filter: when the filter is missing, mapped to
 * other URLs or ordered before the host's authentication, the requests are denied.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class AdminGuard {

  private AdminGuard() {
  }

  /**
   * @param req        The request.
   * @param permission The permission the generator requires.
   * @return whether the filter allowed the request with that permission.
   */
  public static boolean allows(Request req, AdminPermission permission) {
    Object verdict = req.getAttribute(AdminVerdict.ATTRIBUTE);
    return verdict instanceof AdminVerdict && ((AdminVerdict) verdict).allows(permission);
  }

  /**
   * @return the response of a denied XML generator, which writes nothing: 403.
   */
  public static Response denied() {
    return Response.status(ContentStatus.FORBIDDEN);
  }

  /**
   * Writes the output of a denied generator: nothing in XML, an empty object in JSON (Berlioz
   * fails on a generator that writes no JSON value).
   *
   * @param out The output of the generator.
   * @return the response of a denied generator: 403.
   */
  public static Response denied(OutputWriter out) {
    out.startObject("denied", ContextOption.JSON_ONLY);
    out.endObject();
    return denied();
  }

}
