/**
 * The generators of Berlioz used by the admin services, behind the check of the
 * verdict of {@link org.pageseeder.bzadmin.access.AdminFilter}.
 *
 * <p>Each one delegates to the generator of the same name in {@code org.pageseeder.<path>} after
 * the check: {@code org.pageseeder.bzadmin.guard.berlioz.generator.GetGlobalConfig} delegates to
 * {@code org.pageseeder.berlioz.generator.GetGlobalConfig}. The admin pages show the class of the
 * delegate.
 */
@org.jspecify.annotations.NullMarked
package org.pageseeder.bzadmin.guard;
