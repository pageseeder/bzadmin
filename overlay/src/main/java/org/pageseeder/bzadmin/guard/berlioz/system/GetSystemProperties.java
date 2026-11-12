package org.pageseeder.bzadmin.guard.berlioz.system;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.GuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.system.GetSystemProperties} with {@link AdminPermission#VIEW}.
 *
 * <p>Since Berlioz 0.14.6, it redacts by name and by value: the password of a URL, the values of
 * parameters with a sensitive name, and the program arguments of {@code sun.java.command}.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class GetSystemProperties extends GuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.system.GetSystemProperties}. */
  public GetSystemProperties() {
    super(new org.pageseeder.berlioz.system.GetSystemProperties(), AdminPermission.VIEW);
  }

}
