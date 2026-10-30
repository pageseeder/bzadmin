package org.pageseeder.bzadmin.guard.berlioz.system;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.GuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.system.GetRuntimeInfo} with {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetRuntimeInfo extends GuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.system.GetRuntimeInfo}. */
  public GetRuntimeInfo() {
    super(new org.pageseeder.berlioz.system.GetRuntimeInfo(), AdminPermission.VIEW);
  }

}
