package org.pageseeder.bzadmin.guard;

import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * A generator that delegates to another after the check of the verdict of the admin filter:
 * denied, it writes nothing and returns 403.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public abstract class GuardedGenerator implements Generator {

  private final Generator delegate;

  private final AdminPermission permission;

  /**
   * @param delegate   The generator producing the content.
   * @param permission The permission it requires.
   */
  protected GuardedGenerator(Generator delegate, AdminPermission permission) {
    this.delegate = delegate;
    this.permission = permission;
  }

  /**
   * @param req The request.
   * @return the permission required for that request.
   */
  protected AdminPermission permission(Request req) {
    return this.permission;
  }

  @Override
  public final Response generate(Request req, OutputWriter out) {
    if (!allows(req)) return AdminGuard.denied(out);
    return this.delegate.generate(req, out);
  }

  final boolean allows(Request req) {
    return AdminGuard.allows(req, permission(req));
  }

  final Generator delegate() {
    return this.delegate;
  }

}
