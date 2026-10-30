package org.pageseeder.bzadmin.cache;

import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.cache.CacheRegistry.Lookup;

import org.jspecify.annotations.Nullable;

/**
 * The cache that a request names with the parameters {@code provider}, {@code manager} and
 * {@code cache}, resolved in the {@linkplain CacheRegistry#global() registry}.
 */
final class CacheRequest {

  final String provider;

  final String manager;

  final String cache;

  private CacheRequest(String provider, String manager, String cache) {
    this.provider = provider;
    this.manager = manager;
    this.cache = cache;
  }

  /**
   * @param req The request.
   * @return the identity in its parameters (empty when absent).
   */
  static CacheRequest of(Request req) {
    return new CacheRequest(parameter(req, "provider"), parameter(req, "manager"), parameter(req, "cache"));
  }

  /**
   * @return {@code true} if the request names no part of a cache.
   */
  boolean isEmpty() {
    return this.provider.isEmpty() && this.manager.isEmpty() && this.cache.isEmpty();
  }

  /**
   * @param capability The capability of the operation, {@code null} for none.
   * @return the cache or the problem.
   */
  Lookup<CacheAdminCache> lookup(@Nullable CacheCapability capability) {
    CacheRegistry registry = CacheRegistry.global();
    return capability != null ? registry.cache(this.provider, this.manager, this.cache, capability)
        : registry.cache(this.provider, this.manager, this.cache);
  }

  /**
   * Writes the problem of this request and returns its status.
   */
  Response problem(CacheProblem problem, OutputWriter out) {
    CacheOutput.problem(problem, emptyToNull(this.provider), emptyToNull(this.manager), emptyToNull(this.cache), out);
    return Response.status(problem.status());
  }

  private static String parameter(Request req, String name) {
    return req.parameter(name).asString().defaultValue("");
  }

  private static @Nullable String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }

}
