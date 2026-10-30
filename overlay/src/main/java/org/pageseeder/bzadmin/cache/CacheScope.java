package org.pageseeder.bzadmin.cache;

import java.util.Locale;

/**
 * Whether an operation on a cache affects this application only.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheScope {

  /** The cache is local to this application: a clear affects nothing else. */
  LOCAL,

  /** The cache is clustered or replicated: a clear can affect other nodes. */
  CLUSTERED,

  /** The provider cannot tell. */
  UNKNOWN;

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }

}
