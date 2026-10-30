package org.pageseeder.bzadmin.guard.berlioz.system;

import org.pageseeder.berlioz.content.Request;
import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.GuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.system.GetGeneratorStatistics} with {@link AdminPermission#VIEW},
 * or {@link AdminPermission#OPERATE} when it resets the statistics ({@code reset=true}, as the
 * delegate checks it).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetGeneratorStatistics extends GuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.system.GetGeneratorStatistics}. */
  public GetGeneratorStatistics() {
    super(new org.pageseeder.berlioz.system.GetGeneratorStatistics(), AdminPermission.VIEW);
  }

  @Override
  protected AdminPermission permission(Request req) {
    return "true".equals(req.getParameter("reset", "false")) ? AdminPermission.OPERATE : AdminPermission.VIEW;
  }

}
