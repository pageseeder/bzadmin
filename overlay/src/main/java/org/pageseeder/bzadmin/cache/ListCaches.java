package org.pageseeder.bzadmin.cache;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Lists the cache providers, their managers and caches with their statistics, in place of Bastille's
 * {@code GetCachingOverview}: see {@link CacheOutput} for the output. At most
 * {@value CacheOutput#MAX_CACHES} caches per manager; no entry is read.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListCaches implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    CacheOutput.providers(CacheRegistry.global(), out);
    return Response.ok();
  }

}
