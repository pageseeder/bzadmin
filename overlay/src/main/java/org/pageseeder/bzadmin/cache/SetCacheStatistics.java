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
 * Enables or disables the collection of statistics of a cache that supports it
 * ({@link CacheCapability#STATISTICS_TOGGLE}), in place of Bastille's
 * {@code ToggleCacheStatistics}: an operation ({@link AdminPermission#OPERATE}).
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache (all required)</li>
 *   <li>{@code enable}: {@code true} or {@code false} (required)</li>
 * </ul>
 *
 * <pre>{@code <cache-statistics provider="jcache" manager="main" name="PSML" enabled="true"/>}</pre>
 *
 * <p>Otherwise {@code <no-cache problem="..."/>} with its status ({@code unsupported} 501 for a cache
 * whose statistics cannot be toggled).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class SetCacheStatistics implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    boolean enable = req.parameter("enable").asBoolean().required();
    CacheRequest request = CacheRequest.of(req);
    Lookup<CacheAdminCache> found = request.lookup(CacheCapability.STATISTICS_TOGGLE);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    try {
      cache.setStatisticsEnabled(enable);
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    }
    out.startObject("cache-statistics");
    out.field("provider", request.provider);
    out.field("manager", request.manager);
    out.field("name", request.cache);
    out.field("enabled", enable);
    out.endObject();
    return Response.ok();
  }

}
