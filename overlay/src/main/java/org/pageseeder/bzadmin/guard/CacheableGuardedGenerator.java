package org.pageseeder.bzadmin.guard;

import org.pageseeder.berlioz.content.Cacheable;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.bzadmin.access.AdminPermission;

import org.jspecify.annotations.Nullable;

/**
 * A {@link GuardedGenerator} for a cacheable generator: denied, it has no ETag.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public abstract class CacheableGuardedGenerator extends GuardedGenerator implements Cacheable {

  /**
   * @param <T>        The type of the delegate.
   * @param delegate   The generator producing the content.
   * @param permission The permission it requires.
   */
  protected <T extends Generator & Cacheable> CacheableGuardedGenerator(T delegate, AdminPermission permission) {
    super(delegate, permission);
  }

  @Override
  public final @Nullable String getETag(Request req) {
    if (!allows(req)) return null;
    return ((Cacheable) delegate()).getETag(req);
  }

}
