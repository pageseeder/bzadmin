package org.pageseeder.bzadmin.access;

/**
 * The access that {@link AdminFilter} allowed for a request, as the request attribute
 * {@link #ATTRIBUTE}.
 *
 * <p>Only the filter creates it, so its presence means that the filter processed the request.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class AdminVerdict {

  /** The name of the request attribute holding the verdict. */
  public static final String ATTRIBUTE = "org.pageseeder.bzadmin.access.AdminVerdict";

  private final AdminAccess access;

  AdminVerdict(AdminAccess access) {
    this.access = access;
  }

  /**
   * @return the access allowed.
   */
  public AdminAccess access() {
    return this.access;
  }

  /**
   * @param permission The permission to check.
   * @return whether the filter allowed the request with that permission.
   */
  public boolean allows(AdminPermission permission) {
    return this.access.has(permission);
  }

}
