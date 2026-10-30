package org.pageseeder.bzadmin.access;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The access granted by the host to an administrator, for one request.
 *
 * <p>The host sets it as the request attribute {@link #ATTRIBUTE} after authenticating the user and
 * checking that they are an administrator, before {@link AdminFilter}:
 *
 * <pre>{@code
 * req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of(user, VIEW, OPERATE));
 * }</pre>
 *
 * <p>{@link AdminPermission#OPERATE} implies {@link AdminPermission#VIEW}. An access without any
 * permission grants nothing.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class AdminAccess {

  /** The name of the request attribute holding the access. */
  public static final String ATTRIBUTE = "org.pageseeder.bzadmin.access.AdminAccess";

  private final String name;

  private final Set<AdminPermission> permissions;

  private AdminAccess(String name, Set<AdminPermission> permissions) {
    this.name = name;
    this.permissions = permissions;
  }

  /**
   * @param name        The name of the principal, for example the user name.
   * @param permissions The permissions granted.
   * @return the access of that principal.
   */
  public static AdminAccess of(String name, AdminPermission... permissions) {
    Objects.requireNonNull(name, "name");
    Set<AdminPermission> granted = EnumSet.noneOf(AdminPermission.class);
    for (AdminPermission permission : permissions) {
      granted.add(Objects.requireNonNull(permission, "permission"));
    }
    if (granted.contains(AdminPermission.OPERATE)) granted.add(AdminPermission.VIEW);
    return new AdminAccess(name, Collections.unmodifiableSet(granted));
  }

  /**
   * @return the name of the principal.
   */
  public String name() {
    return this.name;
  }

  /**
   * @return the permissions granted, including those implied.
   */
  public Set<AdminPermission> permissions() {
    return this.permissions;
  }

  /**
   * @param permission The permission to check.
   * @return whether the permission is granted.
   */
  public boolean has(AdminPermission permission) {
    return this.permissions.contains(permission);
  }

  @Override
  public String toString() {
    return "AdminAccess[" + this.name + ", " + this.permissions + "]";
  }

}
