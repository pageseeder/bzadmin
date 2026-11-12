package org.pageseeder.bzadmin.log;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.joran.spi.ConfigurationWatchList;
import ch.qos.logback.core.joran.util.ConfigurationWatchListUtil;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.status.Status;
import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.util.Redaction;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Issue;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Severity;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Verdict;

/**
 * Inspects a logback logger context with its public API only, for {@link LogbackBackend#configuration()}.
 *
 * <p>An appender <i>writes</i> when it is started and, if it holds other appenders (such as
 * {@code AsyncAppender}), at least one of them writes, to {@value #MAX_NESTING} levels. The capture
 * appender of the recent events never writes. A logger <i>has output</i> when an appender that writes is
 * attached to it or, while additivity holds, to one of its ancestors. No property of an appender is read
 * other than its name, class, state and file.
 */
final class LogbackInspection {

  /** How many appenders that hold other appenders are looked into, as for the log directories. */
  static final int MAX_NESTING = 4;

  /** The statuses that logback keeps: the first 150 and the last 150. */
  static final int STATUSES_KEPT = 300;

  private final LoggerContext context;

  private final Appender<ILoggingEvent> capture;

  /** The appenders by identity, in the order found. */
  private final Map<Appender<ILoggingEvent>, LoggingConfiguration.Appender> appenders = new IdentityHashMap<>();

  private final List<LoggingConfiguration.Appender> order = new ArrayList<>();

  /** Appenders found past the limit: counted for the verdict, not listed. */
  private final Map<Appender<ILoggingEvent>, Boolean> unlisted = new IdentityHashMap<>();

  private LogbackInspection(LoggerContext context, Appender<ILoggingEvent> capture) {
    this.context = context;
    this.capture = capture;
  }

  /**
   * @param context   The logger context.
   * @param capture   The capture appender of the recent events.
   * @param version   The version of logback, if known.
   * @param resets    The number of resets observed.
   * @param lastReset The time of the last reset observed, or {@code null}.
   * @return the configuration of the context.
   */
  static LoggingConfiguration inspect(LoggerContext context, Appender<ILoggingEvent> capture, @Nullable String version,
      int resets, @Nullable Instant lastReset) {
    if (!supports(version)) return LoggingConfiguration.without("logback", version, Verdict.UNAVAILABLE);
    try {
      return new LogbackInspection(context, capture).inspect(version, resets, lastReset);
    } catch (LinkageError ex) {
      return LoggingConfiguration.without("logback", version, Verdict.UNAVAILABLE);
    }
  }

  /**
   * @param version The version of logback, if known.
   * @return whether it is 1.5 or later; an unknown version is tried.
   */
  static boolean supports(@Nullable String version) {
    if (version == null) return true;
    String[] parts = version.split("[.-]");
    try {
      int major = Integer.parseInt(parts[0]);
      int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
      return major > 1 || (major == 1 && minor >= 5);
    } catch (NumberFormatException ex) {
      return true;
    }
  }

  private LoggingConfiguration inspect(@Nullable String version, int resets, @Nullable Instant lastReset) {
    LoggingConfiguration.Builder b = new LoggingConfiguration.Builder("logback", version);
    b.resets = resets;
    b.lastReset = lastReset;
    List<Logger> all = new ArrayList<>(this.context.getLoggerList());
    Logger root = this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    // The appenders of each logger, and whether each logger has output
    Map<String, Boolean> output = new HashMap<>();
    List<Logger> listed = new ArrayList<>();
    for (Logger logger : all) {
      boolean own = false;
      boolean hasAppenders = false;
      for (Iterator<Appender<ILoggingEvent>> i = logger.iteratorForAppenders(); i.hasNext();) {
        Appender<ILoggingEvent> appender = i.next();
        hasAppenders = true;
        LoggingConfiguration.@Nullable Appender found = visit(appender, null, 0);
        if (found != null) found.loggers.add(logger.getName());
        if (writes(appender)) own = true;
      }
      output.put(logger.getName(), own);
      if (logger == root || logger.getLevel() != null || (hasAppenders && !onlyCapture(logger))) listed.add(logger);
    }
    // Loggers are created with their ancestors, so a parent is known before its children when sorted by name
    all.sort(Comparator.comparing(Logger::getName, LogbackInspection::byDepth));
    for (Logger logger : all) {
      if (logger == root) continue;
      boolean own = output.getOrDefault(logger.getName(), false);
      boolean inherited = logger.isAdditive() && output.getOrDefault(parent(logger.getName(), root.getName()), false);
      output.put(logger.getName(), own || inherited);
    }

    // Appenders, not started first
    List<LoggingConfiguration.Appender> sorted = new ArrayList<>(this.order);
    sorted.sort(Comparator.comparing((LoggingConfiguration.Appender a) -> a.started));
    b.appenders.addAll(sorted.subList(0, Math.min(sorted.size(), LoggingConfiguration.MAX_APPENDERS)));

    // Loggers, root first then by name
    listed.sort(Comparator.comparing((Logger l) -> l != root).thenComparing(Logger::getName));
    b.loggerTotal = listed.size();
    for (Logger logger : listed.subList(0, Math.min(listed.size(), LoggingConfiguration.MAX_LOGGERS))) {
      List<String> ids = new ArrayList<>();
      for (Iterator<Appender<ILoggingEvent>> i = logger.iteratorForAppenders(); i.hasNext();) {
        LoggingConfiguration.@Nullable Appender a = this.appenders.get(i.next());
        if (a != null) ids.add(a.id);
      }
      b.loggers.add(new LoggingConfiguration.Logger(logger.getName(), LogbackBackend.level(logger.getLevel()),
          LogbackBackend.level(logger.getEffectiveLevel()), logger.isAdditive(), ids,
          output.getOrDefault(logger.getName(), false)));
    }

    statuses(b, lastReset);
    for (TurboFilter filter : this.context.getTurboFilterList()) b.turboFilters.add(filter.getClass().getName());
    watch(b);
    issues(b, listed, output);
    return b.build();
  }

  /**
   * Records an appender and the appenders it holds.
   *
   * @return the appender recorded, or {@code null} past the limit.
   */
  private LoggingConfiguration.@Nullable Appender visit(Appender<ILoggingEvent> appender,
      LoggingConfiguration.@Nullable Appender holder, int nesting) {
    LoggingConfiguration.@Nullable Appender known = this.appenders.get(appender);
    if (known != null || this.unlisted.containsKey(appender)) return known;
    if (this.order.size() >= LoggingConfiguration.MAX_APPENDERS) {
      this.unlisted.put(appender, writes(appender));
      return null;
    }
    @Nullable String file = appender instanceof FileAppender ? ((FileAppender<ILoggingEvent>) appender).getFile() : null;
    LoggingConfiguration.Appender recorded = new LoggingConfiguration.Appender("a" + (this.order.size() + 1),
        appender.getName(), appender.getClass().getName(), appender.isStarted(), writes(appender),
        appender == this.capture, file, holder != null ? holder.id : null);
    this.appenders.put(appender, recorded);
    this.order.add(recorded);
    if (appender instanceof AppenderAttachable && nesting < MAX_NESTING) {
      @SuppressWarnings("unchecked")
      AppenderAttachable<ILoggingEvent> attachable = (AppenderAttachable<ILoggingEvent>) appender;
      for (Iterator<Appender<ILoggingEvent>> i = attachable.iteratorForAppenders(); i.hasNext();) {
        visit(i.next(), recorded, nesting + 1);
      }
    }
    return recorded;
  }

  private boolean writes(Appender<ILoggingEvent> appender) {
    return writes(appender, 0);
  }

  private boolean writes(Appender<ILoggingEvent> appender, int nesting) {
    if (appender == this.capture || !appender.isStarted()) return false;
    if (!(appender instanceof AppenderAttachable)) return true;
    // Beyond the limit, assume that it writes rather than report a false problem
    if (nesting >= MAX_NESTING) return true;
    @SuppressWarnings("unchecked")
    AppenderAttachable<ILoggingEvent> attachable = (AppenderAttachable<ILoggingEvent>) appender;
    for (Iterator<Appender<ILoggingEvent>> i = attachable.iteratorForAppenders(); i.hasNext();) {
      if (writes(i.next(), nesting + 1)) return true;
    }
    return false;
  }

  private boolean onlyCapture(Logger logger) {
    for (Iterator<Appender<ILoggingEvent>> i = logger.iteratorForAppenders(); i.hasNext();) {
      if (i.next() != this.capture) return false;
    }
    return true;
  }

  /**
   * @return the name of the parent logger: logback separates the parts of a name with {@code .} or {@code $}.
   */
  static String parent(String name, String root) {
    int i = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
    return i > 0 ? name.substring(0, i) : root;
  }

  /** Orders names so that a parent comes before its children. */
  private static int byDepth(String a, String b) {
    int c = Integer.compare(depth(a), depth(b));
    return c != 0 ? c : a.compareTo(b);
  }

  private static int depth(String name) {
    int depth = 0;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '.' || c == '$') depth++;
    }
    return depth;
  }

  /**
   * The WARN and ERROR statuses since the last reset observed, or since logback started, most recent first.
   */
  private void statuses(LoggingConfiguration.Builder b, @Nullable Instant lastReset) {
    List<Status> all = this.context.getStatusManager().getCopyOfStatusList();
    long since = lastReset != null ? lastReset.toEpochMilli() : Long.MIN_VALUE;
    b.statusesSince = lastReset != null ? "reset" : "start";
    List<Status> shown = new ArrayList<>();
    for (Status status : all) {
      if (status.getTimestamp() < since) continue;
      int level = status.getLevel();
      if (level == Status.ERROR) b.errors++;
      else if (level == Status.WARN) b.warnings++;
      else b.infos++;
      if (level >= Status.WARN) shown.add(status);
    }
    if (this.context.getStatusManager().getCount() > all.size()) {
      b.issues.add(new Issue("statuses-dropped", Severity.WARNING).count(this.context.getStatusManager().getCount()));
    }
    for (int i = shown.size() - 1; i >= 0 && b.statuses.size() < LoggingConfiguration.MAX_STATUSES; i--) {
      b.statuses.add(status(shown.get(i)));
    }
  }

  private static LoggingConfiguration.Status status(Status status) {
    List<LoggingConfiguration.Cause> causes = new ArrayList<>();
    for (@Nullable Throwable t = status.getThrowable(); t != null && causes.size() < LoggingConfiguration.MAX_CAUSES; t = t.getCause()) {
      @Nullable String message = t.getMessage();
      causes.add(new LoggingConfiguration.Cause(t.getClass().getName(), message != null ? safe(message) : null));
      if (t.getCause() == t) break;
    }
    @Nullable Object origin = status.getOrigin();
    @Nullable String message = status.getMessage();
    return new LoggingConfiguration.Status(status.getLevel() == Status.ERROR ? "ERROR" : "WARN", status.getTimestamp(),
        origin != null ? origin.getClass().getName() : null, safe(message != null ? message : ""), causes);
  }

  /**
   * @return the message redacted by value and bounded.
   */
  static String safe(String message) {
    String bounded = message.length() > LoggingConfiguration.MAX_MESSAGE
        ? message.substring(0, LoggingConfiguration.MAX_MESSAGE) + "…" : message;
    return Redaction.redactValue(bounded);
  }

  /**
   * Whether logback watches files for changes ({@code scan}) and their names: since logback 1.5.27 the
   * watch list holds files only when {@code scan} is on.
   */
  private void watch(LoggingConfiguration.Builder b) {
    @Nullable ConfigurationWatchList list = ConfigurationWatchListUtil.getConfigurationWatchList(this.context);
    if (list == null || list.emptyWatchLists()) return;
    b.scan = true;
    for (File file : list.getCopyOfFileWatchList()) b.watched.add(file.getName());
  }

  private void issues(LoggingConfiguration.Builder b, List<Logger> listed, Map<String, Boolean> output) {
    List<Issue> issues = new ArrayList<>();
    boolean anyWrites = this.unlisted.containsValue(Boolean.TRUE);
    boolean consoleOnly = true;
    for (Map.Entry<Appender<ILoggingEvent>, LoggingConfiguration.Appender> e : this.appenders.entrySet()) {
      Appender<ILoggingEvent> appender = e.getKey();
      LoggingConfiguration.Appender a = e.getValue();
      if (a.capture) continue;
      if (a.writes) {
        anyWrites = true;
        if (!(appender instanceof AppenderAttachable) && !(appender instanceof ConsoleAppender)) consoleOnly = false;
      }
    }
    if (this.unlisted.containsValue(Boolean.TRUE)) consoleOnly = false;
    for (LoggingConfiguration.Appender a : this.order) {
      if (!a.capture && !a.started) issues.add(new Issue("stopped-appender", Severity.ERROR).appender(a.id, a.name));
    }
    if (anyWrites) {
      for (Logger logger : listed) {
        boolean off = logger.getEffectiveLevel() != null && LogbackBackend.level(logger.getEffectiveLevel()) == EventLevel.OFF;
        if (!off && !output.getOrDefault(logger.getName(), false)) {
          issues.add(new Issue("no-output", Severity.ERROR).logger(logger.getName()));
        }
      }
    }
    if (b.errors > 0) issues.add(new Issue("error-status", Severity.ERROR).count(b.errors));
    if (b.warnings > 0) issues.add(new Issue("warning-status", Severity.WARNING).count(b.warnings));
    if (anyWrites && consoleOnly) issues.add(new Issue("console-only", Severity.WARNING));
    // The issues found with the statuses go after
    issues.addAll(b.issues);
    b.issues.clear();
    b.issues.addAll(issues);
    if (!anyWrites) b.verdict = Verdict.NOT_WRITING;
    else if (issues.stream().anyMatch(i -> i.severity == Severity.ERROR)) b.verdict = Verdict.PROBLEMS;
    else b.verdict = Verdict.WRITING;
  }

}
