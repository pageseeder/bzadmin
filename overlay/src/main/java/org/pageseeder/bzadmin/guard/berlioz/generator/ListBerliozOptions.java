package org.pageseeder.bzadmin.guard.berlioz.generator;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.CacheableGuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.generator.ListBerliozOptions} with {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListBerliozOptions extends CacheableGuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.generator.ListBerliozOptions}. */
  public ListBerliozOptions() {
    super(new org.pageseeder.berlioz.generator.ListBerliozOptions(), AdminPermission.VIEW);
  }

}
