package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;

/**
 * The level of a log event, and of the capture threshold of the {@linkplain RecentEvents recent events},
 * independent of the logging framework.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public enum EventLevel {

  /** The finest events. */
  TRACE,

  /** Debugging. */
  DEBUG,

  /** The default level. */
  INFO,

  /** Warnings. */
  WARN,

  /** Errors. */
  ERROR,

  /** As a threshold: nothing. */
  OFF;

  /**
   * @param threshold A threshold.
   * @return whether an event of this level passes the threshold.
   */
  boolean passes(EventLevel threshold) {
    return this != OFF && compareTo(threshold) >= 0;
  }

  /**
   * @param name The name of a level, in upper case.
   * @return the level with this name, or {@code null}.
   */
  static @Nullable EventLevel of(@Nullable String name) {
    for (EventLevel level : values()) {
      if (level.name().equals(name)) return level;
    }
    return null;
  }

}
