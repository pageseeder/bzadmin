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
 * Lists a range of the keys of a cache that can list them ({@link CacheCapability#ENTRIES}), in
 * place of Bastille's {@code GetCacheElements}: keys of supported types as typed text, the others
 * counted by class; no value is read, at most {@value CacheEntries#MAX_SCAN} keys are read.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache (all required)</li>
 *   <li>{@code offset}: the position of the first key (default 0)</li>
 *   <li>{@code limit}: the number of positions, 1 to {@value CacheEntries#MAX_LIMIT} (default 100)</li>
 * </ul>
 *
 * <pre>{@code
 * <cache-entries provider="ehcache2" manager="__DEFAULT__">
 *   <cache name="PSML" .../>
 *   <entries offset="0" limit="100" scanned="2" more="false" max-scan="10000">
 *     <entry key="/index.psml" key-type="string"/>
 *     <unsupported-keys class="com.example.Key" count="1"/>
 *   </entries>
 * </cache-entries>
 * }</pre>
 *
 * <p>Otherwise {@code <no-cache problem="..."/>} with its status ({@code unsupported} 501 for a cache
 * whose keys cannot be listed).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListCacheEntries implements Generator {

  /** The number of positions by default. */
  static final int DEFAULT_LIMIT = 100;

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    CacheRequest request = CacheRequest.of(req);
    int offset = req.parameter("offset").asInt().clamp(0, CacheEntries.MAX_SCAN).defaultValue(0);
    int limit = req.parameter("limit").asInt().clamp(1, CacheEntries.MAX_LIMIT).defaultValue(DEFAULT_LIMIT);
    Lookup<CacheAdminCache> found = request.lookup(CacheCapability.ENTRIES);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    CacheOutput.Snapshot snapshot;
    CacheEntries entries;
    try {
      snapshot = new CacheOutput.Snapshot(cache, false);
      entries = cache.entries(offset, limit);
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    }
    CacheOutput.entries(request.provider, request.manager, snapshot, entries, limit, out);
    return Response.ok();
  }

}
