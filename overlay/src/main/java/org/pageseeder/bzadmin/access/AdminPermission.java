package org.pageseeder.bzadmin.access;

/**
 * The permissions of an administrator.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum AdminPermission {

  /** Every read: configuration, services, caches, files, stylesheets, logs, system information. */
  VIEW,

  /**
   * Every state change: cache clearing and element removal, statistics reset and toggle, log
   * threshold, Berlioz reload, services reload, XSLT cache clear. Implies {@link #VIEW}.
   */
  OPERATE

}
