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
 * Removes one entry of a cache that supports it ({@link CacheCapability#REMOVE_ENTRY}) by a typed
 * key, in place of Bastille's {@code RemoveCacheElement}: an operation
 * ({@link AdminPermission#OPERATE}). The key is parsed from its type and text, never from the text
 * of an object, and is never deserialized.
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code provider}, {@code manager}, {@code cache}: the cache (all required)</li>
 *   <li>{@code key}: the key as text (required, at most {@value CacheKey#MAX_LENGTH} characters)</li>
 *   <li>{@code key-type}: {@code string} (default), {@code integer} or {@code long}</li>
 * </ul>
 *
 * <pre>{@code <remove-cache-entry provider="ehcache2" manager="__DEFAULT__" name="PSML" key="/index.psml" key-type="string" removed="true"/>}</pre>
 *
 * <p>{@code removed} is {@code false} when the cache had no entry with this key. Otherwise
 * {@code <no-cache problem="..."/>} with its status ({@code invalid-key} 400 for a missing key, an
 * unsupported type, or a key that is not of the type of the cache's keys).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class RemoveCacheEntry implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.OPERATE)) return AdminGuard.denied(out);
    CacheRequest request = CacheRequest.of(req);
    @Nullable String text = req.parameter("key").asString().optional();
    String type = req.parameter("key-type").asString().defaultValue(CacheKey.Type.STRING.toString());
    Lookup<CacheAdminCache> found = request.lookup(CacheCapability.REMOVE_ENTRY);
    @Nullable CacheProblem problem = found.problem();
    if (problem != null) return request.problem(problem, out);
    CacheAdminCache cache = found.requireValue();
    if (text == null) return request.problem(CacheProblem.INVALID_KEY, out);
    CacheKey key;
    boolean removed;
    try {
      key = CacheKey.parse(type, text);
      removed = cache.remove(key);
    } catch (IllegalArgumentException ex) {
      return request.problem(CacheProblem.INVALID_KEY, out);
    } catch (IllegalStateException ex) {
      return request.problem(CacheProblem.CLOSED, out);
    }
    out.startObject("remove-cache-entry");
    out.field("provider", request.provider);
    out.field("manager", request.manager);
    out.field("name", request.cache);
    out.field("key", key.text());
    out.field("key-type", key.type().toString());
    out.field("removed", removed);
    out.endObject();
    return Response.ok();
  }

}
