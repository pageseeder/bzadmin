package org.pageseeder.bzadmin.log;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A logging framework that is not supported: nothing to list or capture.
 */
final class UnsupportedLoggingBackend implements LoggingBackend {

  private final String name;

  private final @Nullable String version;

  UnsupportedLoggingBackend(String name, @Nullable String version) {
    this.name = name;
    this.version = version;
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public @Nullable String version() {
    return this.version;
  }

  @Override
  public @Nullable List<Path> logDirectories() {
    // Not an empty list: the framework cannot list its directories
    return null;
  }

  @Override
  public @Nullable RecentEvents recentEvents() {
    return null;
  }

  @Override
  public @Nullable EventLevel rootLevel() {
    return null;
  }

  @Override
  public void stop() {
    // Nothing attached
  }
}
