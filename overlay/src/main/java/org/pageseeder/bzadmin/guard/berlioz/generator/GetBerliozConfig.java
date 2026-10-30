package org.pageseeder.bzadmin.guard.berlioz.generator;

import org.pageseeder.bzadmin.access.AdminPermission;
import org.pageseeder.bzadmin.guard.CacheableGuardedGenerator;

/**
 * {@link org.pageseeder.berlioz.generator.GetBerliozConfig} with {@link AdminPermission#VIEW}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetBerliozConfig extends CacheableGuardedGenerator {

  /** Creates the authorization-guarded delegate for {@link org.pageseeder.berlioz.generator.GetBerliozConfig}. */
  public GetBerliozConfig() {
    super(new org.pageseeder.berlioz.generator.GetBerliozConfig(), AdminPermission.VIEW);
  }

}
