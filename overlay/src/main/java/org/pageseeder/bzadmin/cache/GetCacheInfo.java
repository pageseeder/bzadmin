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
 * Shows one cache, with its statistics and configuration, in place of Bastille's
 * {@code GetCacheInfo}.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache (all required)</li>
 * </ul>
 *
 * <pre>{@code
 * <cache-info provider="ehcache2" manager="__DEFAULT__">
 *   <cache name="PSML" scope="local" capabilities="...">
 *     <statistics>...</statistics>
 *     <configuration><property name="eternal" value="false"/></configuration>
 *   </cache>
 * </cache-info>
 * }</pre>
 *
 * <p>Otherwise {@code <no-cache problem="..."/>} with its status (see {@link CacheProblem}).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetCacheInfo implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    CacheRequest request = CacheRequest.of(req);
    Lookup<CacheAdminCache> found = request.lookup(null);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    CacheOutput.Snapshot snapshot;
    try {
      snapshot = new CacheOutput.Snapshot(cache, true);
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    }
    CacheOutput.cache(request.provider, request.manager, snapshot, out);
    return Response.ok();
  }

}
