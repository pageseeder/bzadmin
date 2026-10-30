package org.pageseeder.bzadmin.log;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import org.jspecify.annotations.Nullable;

/**
 * The logback adapter: the directories of logback's file appenders, and the recent events, captured
 * by an appender of the root logger.
 *
 * <p>Only {@link LoggingBackends} loads this class, when logback is the SLF4J logger factory.
 *
 * <p>The appender receives the events that the application logs, after the levels of its loggers
 * and its turbo filters, and keeps those at or above the capture threshold. Events of a logger that
 * is not additive (with {@code additivity="false"}) do not reach it. It does not lock (logback's
 * {@code UnsynchronizedAppenderBase} and {@link RecentEvents}) and makes the text of the event in
 * the logging thread, within the bounds of {@link RecentEvent}.
 *
 * <p>The appender is attached again when the logger context is reset (reconfiguration), detached
 * when the context stops, and detached with its listener on {@link #stop()}.
 */
final class LogbackBackend implements LoggingBackend {

  /** The name of the appender of the recent events. */
  static final String APPENDER = "bzadmin-recent-events";

  /** How many appenders that hold other appenders (such as AsyncAppender) are looked into. */
  private static final int MAX_NESTING = 4;

  private final LoggerContext context;

  private final RecentEvents events = new RecentEvents(RecentEvents.DEFAULT_CAPACITY, RecentEvents.DEFAULT_THRESHOLD);

  private final Capture capture = new Capture(this.events);

  private final Listener listener = new Listener();

  /** Whether stopped: the appender is no longer attached again. */
  private volatile boolean stopped;

  private LogbackBackend(LoggerContext context) {
    this.context = context;
    this.capture.setContext(context);
    this.capture.setName(APPENDER);
  }

  /**
   * @param factory The SLF4J logger factory: logback's {@code LoggerContext}.
   * @return the adapter, capturing events.
   */
  static LogbackBackend start(Object factory) {
    LogbackBackend backend = new LogbackBackend((LoggerContext) factory);
    backend.context.addListener(backend.listener);
    backend.attach();
    return backend;
  }

  @Override
  public String name() {
    return "logback";
  }

  @Override
  public @Nullable String version() {
    return LoggingBackends.version(LoggerContext.class);
  }

  @Override
  public List<Path> logDirectories() {
    Set<Path> directories = new LinkedHashSet<>();
    for (Logger logger : this.context.getLoggerList()) {
      collect(logger, directories, 0);
    }
    return new ArrayList<>(directories);
  }

  @Override
  public RecentEvents recentEvents() {
    return this.events;
  }

  @Override
  public @Nullable EventLevel rootLevel() {
    return level(root().getLevel());
  }

  @Override
  public void stop() {
    this.stopped = true;
    this.context.removeListener(this.listener);
    detach();
  }

  /**
   * @return whether the appender is attached to the root logger and started.
   */
  boolean isCapturing() {
    return root().isAttached(this.capture) && this.capture.isStarted();
  }

  private Logger root() {
    return this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }

  private void attach() {
    this.capture.start();
    root().addAppender(this.capture);
  }

  private void detach() {
    root().detachAppender(this.capture);
    this.capture.stop();
  }

  private static void collect(AppenderAttachable<ILoggingEvent> attachable, Set<Path> directories, int nesting) {
    for (Iterator<Appender<ILoggingEvent>> i = attachable.iteratorForAppenders(); i.hasNext();) {
      Appender<ILoggingEvent> appender = i.next();
      if (appender instanceof FileAppender) {
        @Nullable String file = ((FileAppender<ILoggingEvent>) appender).getFile();
        if (file == null) continue;
        try {
          @Nullable Path directory = Path.of(file).toAbsolutePath().getParent();
          if (directory != null) directories.add(directory);
        } catch (InvalidPathException ex) {
          // Not a path on this file system
        }
      } else if (appender instanceof AppenderAttachable && nesting < MAX_NESTING) {
        @SuppressWarnings("unchecked")
        AppenderAttachable<ILoggingEvent> nested = (AppenderAttachable<ILoggingEvent>) appender;
        collect(nested, directories, nesting + 1);
      }
    }
  }

  /**
   * @param level A logback level (may be {@code null}).
   * @return the corresponding level, or {@code null}.
   */
  static @Nullable EventLevel level(@Nullable Level level) {
    if (level == null) return null;
    int value = level.toInt();
    if (value >= Level.OFF_INT) return EventLevel.OFF;
    if (value >= Level.ERROR_INT) return EventLevel.ERROR;
    if (value >= Level.WARN_INT) return EventLevel.WARN;
    if (value >= Level.INFO_INT) return EventLevel.INFO;
    if (value >= Level.DEBUG_INT) return EventLevel.DEBUG;
    return EventLevel.TRACE;
  }

  /**
   * @return the event as text, within the bounds of {@link RecentEvent}.
   */
  static RecentEvent event(ILoggingEvent event, EventLevel level) {
    RecentEvent.Budget budget = new RecentEvent.Budget(RecentEvent.MAX_TEXT);
    @Nullable String message = budget.take(event.getFormattedMessage());
    RecentEvent.@Nullable Trace trace = trace(event.getThrowableProxy(), budget, 0);
    return new RecentEvent(event.getTimeStamp(), level, event.getLoggerName(), event.getThreadName(), message, trace, budget.truncated());
  }

  private static RecentEvent.@Nullable Trace trace(@Nullable IThrowableProxy proxy, RecentEvent.Budget budget, int depth) {
    if (proxy == null) return null;
    if (depth >= RecentEvent.MAX_CAUSES) {
      budget.omit();
      return null;
    }
    String className = budget.takeNonNull(proxy.getClassName());
    @Nullable String message = budget.take(proxy.getMessage());
    StackTraceElementProxy @Nullable [] elements = proxy.getStackTraceElementProxyArray();
    if (elements == null) elements = new StackTraceElementProxy[0];
    int length = elements.length;
    // The frames in common with the enclosing throwable are left out, as Java prints "... n more"
    int own = Math.max(0, length - Math.max(0, proxy.getCommonFrames()));
    List<String> steps = new ArrayList<>();
    int i = 0;
    while (i < own && i < RecentEvent.MAX_STEPS && !budget.exhausted()) {
      String step = String.valueOf(elements[i++].getStackTraceElement());
      steps.add(budget.takeNonNull(step));
      // Not past the servlet API: the frames of the container
      if (step.startsWith("javax.servlet.") || step.startsWith("jakarta.servlet.")) break;
    }
    if (i < own) budget.omit();
    RecentEvent.@Nullable Trace cause = trace(proxy.getCause(), budget, depth + 1);
    return new RecentEvent.Trace(className, message, steps, length - i, cause);
  }

  /**
   * The appender of the recent events.
   */
  static final class Capture extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final RecentEvents events;

    Capture(RecentEvents events) {
      this.events = events;
    }

    @Override
    protected void append(ILoggingEvent event) {
      @Nullable EventLevel level = level(event.getLevel());
      if (level == null || !this.events.accepts(level)) return;
      this.events.add(event(event, level));
    }
  }

  /**
   * Attaches the appender again after a reset (reconfiguration), and detaches it when the context
   * stops.
   */
  private final class Listener implements LoggerContextListener {

    @Override
    public boolean isResetResistant() {
      return true;
    }

    @Override
    public void onStart(LoggerContext context) {
      // Attached when the adapter starts
    }

    @Override
    public void onReset(LoggerContext context) {
      // A reset detaches and stops every appender of the root logger (when the context stops too,
      // before onStop)
      if (!LogbackBackend.this.stopped) attach();
    }

    @Override
    public void onStop(LoggerContext context) {
      detach();
    }

    @Override
    public void onLevelChange(Logger logger, @Nullable Level level) {
      // The capture threshold does not follow the levels of the loggers
    }
  }

}
