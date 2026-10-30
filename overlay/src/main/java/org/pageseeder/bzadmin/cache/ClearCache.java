package org.pageseeder.bzadmin.cache;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.cache.CacheRegistry.Lookup;

/**
 * Clears one cache, or every cache without parameters: an operation ({@link AdminPermission#OPERATE}).
 * A clear is the provider's bulk removal, without loading or writing through
 * ({@link CacheAdminCache#clear()}).
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache; none of them for every cache
 *     of every active manager that can be named (at most {@value CacheOutput#MAX_CACHES} per manager)</li>
 * </ul>
 *
 * <pre>{@code
 * <clear-cache>
 *   <cache provider="ehcache2" manager="__DEFAULT__" name="PSML"/>
 * </clear-cache>
 * }</pre>
 *
 * <p>In JSON: {@code {"caches": [{"provider": ..., "manager": ..., "name": "PSML"}]}}. Otherwise
 * {@code <no-cache problem="..."/>} with its status (see {@link CacheProblem}). A cache closed during
 * a clear of every cache is left out.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ClearCache implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    CacheRequest request = CacheRequest.of(req);
    if (request.isEmpty()) return clearAll(out);
    Lookup<CacheAdminCache> found = request.lookup(CacheCapability.CLEAR);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    try {
      cache.clear();
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    }
    out.startObject("clear-cache");
    out.startArray("caches", ContextOption.JSON_ONLY);
    CacheOutput.identity("cache", request.provider, request.manager, request.cache, out);
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  private static Response clearAll(OutputWriter out) {
    out.startObject("clear-cache");
    out.startArray("caches", ContextOption.JSON_ONLY);
    for (CacheAdminProvider provider : CacheRegistry.global().providers()) {
      List<CacheAdminManager> managers = provider.managers();
      for (CacheAdminManager manager : managers) {
        if (!manager.isClosed() && CacheOutput.isAddressable(manager, managers)) clearAll(provider, manager, out);
      }
    }
    out.endArray();
    out.endObject();
    return Response.ok();
  }

  private static void clearAll(CacheAdminProvider provider, CacheAdminManager manager, OutputWriter out) {
    List<String> names = CacheOutput.names(manager);
    for (String name : names.subList(0, Math.min(names.size(), CacheOutput.MAX_CACHES))) {
      if (clear(manager.cache(name))) CacheOutput.identity("cache", provider.id(), manager.id(), name, out);
    }
  }

  /**
   * @param cache The cache, {@code null} when removed since listed.
   * @return {@code true} if the cache was cleared; {@code false} when removed, closed, or it cannot be cleared.
   */
  private static boolean clear(@Nullable CacheAdminCache cache) {
    if (cache == null || !cache.capabilities().contains(CacheCapability.CLEAR)) return false;
    try {
      cache.clear();
      return true;
    } catch (IllegalStateException ex) {
      // Closed since listed
      return false;
    }
  }

}
