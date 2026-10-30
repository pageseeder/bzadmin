package org.pageseeder.bzadmin.log;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What the admin pages can use of the logging framework of the host: the directories of its log
 * files and the recent events. Each is optional, and none is needed to read the log files that the
 * host registers (see {@link LogFiles}).
 *
 * @see LoggingBackends
 */
interface LoggingBackend {

  /**
   * @return the name of the logging framework: {@code logback}, or the class of the SLF4J logger
   *   factory for one that is not supported.
   */
  String name();

  /**
   * @return the version of the logging framework, from the manifest of its JAR, or {@code null}
   *   when unknown.
   */
  default @Nullable String version() {
    return null;
  }

  /**
   * @return the directories of the log files of the framework, or {@code null} when it cannot list
   *   them.
   */
  @Nullable List<Path> logDirectories();

  /**
   * @return the recent events, or {@code null} when the framework does not support them.
   */
  @Nullable RecentEvents recentEvents();

  /**
   * @return the level of the root logger of the application, or {@code null} when unknown.
   */
  @Nullable EventLevel rootLevel();

  /**
   * Stops capturing events and releases what was attached to the logging framework.
   */
  void stop();

}
