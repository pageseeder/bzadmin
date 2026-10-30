package org.pageseeder.bzadmin.cache;

import java.util.Locale;

import org.pageseeder.berlioz.content.ContentStatus;

/**
 * Why a request does not reach a cache: an expected condition, with its status.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum CacheProblem {

  /** The provider, manager or cache parameter is missing. */
  MISSING_PARAMETER(ContentStatus.BAD_REQUEST),

  /** The key of an entry is missing, of an unsupported type, or not a key of the cache's type. */
  INVALID_KEY(ContentStatus.BAD_REQUEST),

  /** No provider has this identifier. */
  UNKNOWN_PROVIDER(ContentStatus.NOT_FOUND),

  /** The provider has no manager with this identifier. */
  UNKNOWN_MANAGER(ContentStatus.NOT_FOUND),

  /** The provider lists several managers with this identifier: none is chosen. */
  AMBIGUOUS_MANAGER(ContentStatus.CONFLICT),

  /** The manager or the cache is closed. */
  CLOSED(ContentStatus.CONFLICT),

  /** The manager has no cache with this name. */
  UNKNOWN_CACHE(ContentStatus.NOT_FOUND),

  /** The cache does not have the capability of the operation. */
  UNSUPPORTED(ContentStatus.NOT_IMPLEMENTED);

  private final ContentStatus status;

  CacheProblem(ContentStatus status) {
    this.status = status;
  }

  /**
   * @return the status of a response with this problem.
   */
  public ContentStatus status() {
    return this.status;
  }

  /**
   * @return the name in the output: lower case, with {@code -} ({@code unknown-cache}).
   */
  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

}
