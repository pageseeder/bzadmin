package org.pageseeder.bzadmin.log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * A bounded snapshot of the configuration of the logging framework, without its classes: whether
 * logging writes events, the issues found, the appenders and loggers, the status messages, the
 * resets observed and what logback watches ({@code docs/design/logging-configuration.md}).
 *
 * <p>Made by {@link LogbackBackend#configuration()} on each request, in one walk of the loggers and
 * appenders and one scan of the status messages.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class LoggingConfiguration {

  /** The most appenders listed. */
  static final int MAX_APPENDERS = 200;

  /** The most loggers listed. */
  static final int MAX_LOGGERS = 500;

  /** The most status messages listed. */
  static final int MAX_STATUSES = 50;

  /** The most characters of a status message or of the message of a cause. */
  static final int MAX_MESSAGE = 8192;

  /** The most causes of a status message. */
  static final int MAX_CAUSES = 8;

  /** Whether logging writes events. */
  enum Verdict {

    /** No appender of the context writes. */
    NOT_WRITING,

    /** At least one {@code error} issue. */
    PROBLEMS,

    /** No {@code error} issue. */
    WRITING,

    /** The logging framework is not logback. */
    UNSUPPORTED,

    /** A logback without the API used. */
    UNAVAILABLE;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
  }

  /** The severity of an issue. */
  enum Severity {
    ERROR, WARNING;

    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * An issue: its type and severity, and what it names (all optional).
   */
  static final class Issue {

    final String type;

    final Severity severity;

    @Nullable String appender;

    @Nullable String name;

    @Nullable String logger;

    long count = -1;

    @Nullable String location;

    @Nullable String path;

    Issue(String type, Severity severity) {
      this.type = type;
      this.severity = severity;
    }

    Issue withAppender(String id, @Nullable String appenderName) {
      this.appender = id;
      this.name = appenderName;
      return this;
    }

    Issue withLogger(String loggerName) {
      this.logger = loggerName;
      return this;
    }

    Issue withCount(long n) {
      this.count = n;
      return this;
    }

    Issue withFile(@Nullable String fileLocation, @Nullable String filePath) {
      this.location = fileLocation;
      this.path = filePath;
      return this;
    }
  }

  /**
   * An appender, once by identity.
   */
  static final class Appender {

    final String id;

    final @Nullable String name;

    final String className;

    final boolean started;

    final boolean writes;

    final boolean capture;

    @Nullable String file;

    @Nullable String holder;

    final List<String> loggers = new ArrayList<>();

    Appender(String id, @Nullable String name, String className, boolean started, boolean writes, boolean capture) {
      this.id = id;
      this.name = name;
      this.className = className;
      this.started = started;
      this.writes = writes;
      this.capture = capture;
    }

    /**
     * Sets the file the appender writes to and the ID of the appender that holds it.
     */
    Appender at(@Nullable String filePath, @Nullable String holderId) {
      this.file = filePath;
      this.holder = holderId;
      return this;
    }
  }

  /**
   * A logger with a configured level or appenders of its own, or the root.
   */
  static final class Logger {

    final String name;

    final @Nullable EventLevel level;

    final @Nullable EventLevel effectiveLevel;

    final boolean additive;

    final List<String> appenders;

    final boolean output;

    Logger(String name, @Nullable EventLevel level, @Nullable EventLevel effectiveLevel, boolean additive,
        List<String> appenders, boolean output) {
      this.name = name;
      this.level = level;
      this.effectiveLevel = effectiveLevel;
      this.additive = additive;
      this.appenders = appenders;
      this.output = output;
    }
  }

  /**
   * A WARN or ERROR status message, redacted and bounded.
   */
  static final class Status {

    final String level;

    final long time;

    final @Nullable String origin;

    final String message;

    final List<Cause> causes;

    Status(String level, long time, @Nullable String origin, String message, List<Cause> causes) {
      this.level = level;
      this.time = time;
      this.origin = origin;
      this.message = message;
      this.causes = causes;
    }
  }

  /**
   * A cause of a status message.
   */
  static final class Cause {

    final String className;

    final @Nullable String message;

    Cause(String className, @Nullable String message) {
      this.className = className;
      this.message = message;
    }
  }

  final String framework;

  final @Nullable String version;

  final Verdict verdict;

  final List<Issue> issues;

  final List<Appender> appenders;

  final List<Logger> loggers;

  final int loggerTotal;

  final List<Status> statuses;

  /** {@code reset} or {@code start}: since when the statuses are counted. */
  final String statusesSince;

  final int errors;

  final int warnings;

  final int infos;

  final int resets;

  final @Nullable Instant lastReset;

  final List<String> turboFilters;

  final boolean scan;

  final List<String> watched;

  private LoggingConfiguration(Builder b) {
    this.framework = b.framework;
    this.version = b.version;
    this.verdict = b.verdict;
    this.issues = Collections.unmodifiableList(b.issues);
    this.appenders = Collections.unmodifiableList(b.appenders);
    this.loggers = Collections.unmodifiableList(b.loggers);
    this.loggerTotal = b.loggerTotal;
    this.statuses = Collections.unmodifiableList(b.statuses);
    this.statusesSince = b.statusesSince;
    this.errors = b.errors;
    this.warnings = b.warnings;
    this.infos = b.infos;
    this.resets = b.resets;
    this.lastReset = b.lastReset;
    this.turboFilters = Collections.unmodifiableList(b.turboFilters);
    this.scan = b.scan;
    this.watched = Collections.unmodifiableList(b.watched);
  }

  /**
   * @param framework The name of the logging framework.
   * @param version   Its version, if known.
   * @param verdict   {@link Verdict#UNSUPPORTED} or {@link Verdict#UNAVAILABLE}.
   * @return a configuration with its verdict only.
   */
  static LoggingConfiguration without(String framework, @Nullable String version, Verdict verdict) {
    Builder b = new Builder(framework, version);
    b.verdict = verdict;
    return b.build();
  }

  /**
   * @return whether the framework could be inspected, so that there are appenders, loggers and statuses.
   */
  boolean inspected() {
    return this.verdict != Verdict.UNSUPPORTED && this.verdict != Verdict.UNAVAILABLE;
  }

  /**
   * Collects the parts of a configuration.
   */
  static final class Builder {

    final String framework;

    final @Nullable String version;

    Verdict verdict = Verdict.WRITING;

    final List<Issue> issues = new ArrayList<>();

    final List<Appender> appenders = new ArrayList<>();

    final List<Logger> loggers = new ArrayList<>();

    int loggerTotal;

    final List<Status> statuses = new ArrayList<>();

    String statusesSince = "start";

    int errors;

    int warnings;

    int infos;

    int resets;

    @Nullable Instant lastReset;

    final List<String> turboFilters = new ArrayList<>();

    boolean scan;

    final List<String> watched = new ArrayList<>();

    Builder(String framework, @Nullable String version) {
      this.framework = framework;
      this.version = version;
    }

    LoggingConfiguration build() {
      return new LoggingConfiguration(this);
    }
  }

}
