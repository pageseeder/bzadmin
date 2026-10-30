package org.pageseeder.bzadmin.guard.berlioz.system;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.GuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.system.ListLibraries} with {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListLibraries extends GuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.system.ListLibraries}. */
  public ListLibraries() {
    super(new org.pageseeder.berlioz.system.ListLibraries(), AdminPermission.VIEW);
  }

}
