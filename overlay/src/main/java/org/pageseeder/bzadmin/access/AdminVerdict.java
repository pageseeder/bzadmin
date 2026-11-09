package org.pageseeder.bzadmin.access;

import javax.servlet.ServletContext;

import org.jspecify.annotations.Nullable;

/**
 * The access that {@link AdminFilter} allowed for a request, as the request attribute
 * {@link #ATTRIBUTE}.
 *
 * <p>Only the filter creates it, so its presence means that the filter processed the request.
 * It also holds the servlet context of the request, which Berlioz generators cannot reach, for
 * the generators that report the deployment ({@link AdminGuard#context}).
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class AdminVerdict {

  /** The name of the request attribute holding the verdict. */
  public static final String ATTRIBUTE = "org.pageseeder.bzadmin.access.AdminVerdict";

  private final AdminAccess access;

  private final @Nullable ServletContext context;

  AdminVerdict(AdminAccess access) {
    this(access, null);
  }

  AdminVerdict(AdminAccess access, @Nullable ServletContext context) {
    this.access = access;
    this.context = context;
  }

  /**
   * @return the servlet context of the request, if the filter provided it.
   */
  @Nullable ServletContext context() {
    return this.context;
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
