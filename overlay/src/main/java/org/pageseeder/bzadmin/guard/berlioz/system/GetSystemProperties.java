package org.pageseeder.bzadmin.guard.berlioz.system;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.GuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.system.GetSystemProperties} with {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetSystemProperties extends GuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.system.GetSystemProperties}. */
  public GetSystemProperties() {
    super(new org.pageseeder.berlioz.system.GetSystemProperties(), AdminPermission.VIEW);
  }

}
