package org.pageseeder.bzadmin.cache;

import org.jspecify.annotations.Nullable;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.cache.CacheRegistry.Lookup;

/**
 * Resets the statistics of a cache that supports it ({@link CacheCapability#STATISTICS_RESET}), so
 * that the counts start again from zero; the entries are not changed. An operation
 * ({@link AdminPermission#OPERATE}).
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache (all required)</li>
 * </ul>
 *
 * <pre>{@code <reset-cache-statistics provider="jcache" manager="main" name="PSML"/>}</pre>
 *
 * <p>Otherwise {@code <no-cache problem="..."/>} with its status ({@code unsupported} 501 for a cache
 * whose statistics cannot be reset, or are disabled).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ResetCacheStatistics implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    CacheRequest request = CacheRequest.of(req);
    Lookup<CacheAdminCache> found = request.lookup(CacheCapability.STATISTICS_RESET);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    try {
      cache.resetStatistics();
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    } catch (UnsupportedOperationException ex) {
      // Disabled since the lookup
      return request.problem(CacheProblem.UNSUPPORTED, out);
    }
    CacheOutput.identity("reset-cache-statistics", request.provider, request.manager, request.cache, out);
    return Response.ok();
  }

}
