package org.pageseeder.bzadmin.log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The logging framework of the host, detected once through SLF4J, and the lifecycle of what the
 * admin pages attach to it.
 *
 * <p>Logback is optional: the overlay is compiled against it but does not depend on it, and its
 * classes are only used when the SLF4J logger factory is logback's {@code LoggerContext}, visible
 * from the overlay. Otherwise, or if that logback is not compatible, the framework is
 * {@linkplain UnsupportedLoggingBackend unsupported}: the recent events are not available and the
 * log files are those the host registers.
 *
 * <p>The admin filter {@linkplain #start() starts} the capture of recent events when it is
 * initialised, and {@linkplain #stop() stops} it when it is destroyed, so that nothing of the web
 * application stays attached to a logging framework that outlives it (a logback shared by the
 * container). A generator used without the filter starts it when it first needs it.
 *
 * <p>The number of recent events held is the global property {@value #CAPACITY}. It is applied
 * when the global settings are loaded or reloaded, keeping the events already captured: the filter
 * usually starts the capture before Berlioz loads its settings.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class LoggingBackends {

  /**
   * The global property of the number of recent events held, from 1 to
   * {@value RecentEvents#MAX_CAPACITY} ({@value RecentEvents#DEFAULT_CAPACITY} by default).
   */
  public static final String CAPACITY = "berlioz.admin.logs.recent-capacity";

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingBackends.class);

  private static final String LOGBACK_CONTEXT = "ch.qos.logback.classic.LoggerContext";

  /** Whether the settings listener is registered (once: Berlioz cannot remove one listener). */
  private static final AtomicBoolean LISTENING = new AtomicBoolean();

  /** The invalid values of the capacity already reported. */
  private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

  // Volatile only publishes the reference: the backends are thread-safe
  @SuppressWarnings("java:S3077")
  private static volatile @Nullable LoggingBackend current;

  private LoggingBackends() {
  }

  /**
   * Detects the logging framework, and starts capturing recent events when it supports them.
   */
  public static void start() {
    current();
  }

  /**
   * Stops capturing recent events and detaches from the logging framework; the next use detects it
   * again.
   */
  public static synchronized void stop() {
    @Nullable LoggingBackend backend = current;
    current = null;
    if (backend != null) backend.stop();
  }

  /**
   * @return the name of the logging framework: {@code logback}, or the class of the SLF4J logger
   *   factory for one that is not supported.
   */
  public static String name() {
    return current().name();
  }

  /**
   * @return whether the admin pages capture the recent events of the logging framework.
   */
  public static boolean capturesRecentEvents() {
    return current().recentEvents() != null;
  }

  /**
   * @return the logging framework, detected and started on first use.
   */
  static LoggingBackend current() {
    @Nullable LoggingBackend backend = current;
    if (backend != null) return backend;
    synchronized (LoggingBackends.class) {
      backend = current;
      if (backend == null) {
        backend = detect(LoggerFactory.getILoggerFactory());
        current = backend;
        if (backend.recentEvents() != null) listen();
      }
      return backend;
    }
  }

  /**
   * Applies the capacity of the recent events when the global settings are loaded, and now if
   * they already are.
   */
  private static void listen() {
    if (LISTENING.compareAndSet(false, true)) GlobalSettings.registerListener(LoggingBackends::configure);
    // Before Berlioz is set up, reading a property would load the settings too early
    if (GlobalSettings.getWebInf() != null) configure();
  }

  /**
   * Applies the global property {@value #CAPACITY} to the recent events, if they are captured;
   * does not start the capture.
   */
  static void configure() {
    @Nullable LoggingBackend backend = current;
    @Nullable RecentEvents events = backend != null ? backend.recentEvents() : null;
    if (events == null) return;
    @Nullable String value;
    try {
      value = GlobalSettings.get(CAPACITY);
    } catch (IllegalStateException ex) {
      // Berlioz is not set up
      return;
    }
    events.resize(capacity(value));
  }

  /**
   * @param value The value of the global property {@value #CAPACITY} (may be {@code null}).
   * @return the number of recent events held: the default when not set or not a number, within
   *   the limits otherwise.
   */
  static int capacity(@Nullable String value) {
    if (value == null || value.isBlank()) return RecentEvents.DEFAULT_CAPACITY;
    String number = value.strip();
    if (!number.matches("[+-]?\\d+")) {
      report(value, "is not a number, using " + RecentEvents.DEFAULT_CAPACITY);
      return RecentEvents.DEFAULT_CAPACITY;
    }
    long capacity;
    try {
      capacity = Long.parseLong(number);
    } catch (NumberFormatException ex) {
      // Too many digits: still out of range
      capacity = number.startsWith("-") ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
    if (capacity < 1) {
      report(value, "is less than 1, using 1");
      return 1;
    }
    if (capacity > RecentEvents.MAX_CAPACITY) {
      report(value, "is more than " + RecentEvents.MAX_CAPACITY + ", using " + RecentEvents.MAX_CAPACITY);
      return RecentEvents.MAX_CAPACITY;
    }
    return (int) capacity;
  }

  private static void report(String value, String problem) {
    if (REPORTED.add(value)) LOGGER.warn("The number of recent log events held ({}={}) {}", CAPACITY, value, problem);
  }

  /**
   * @param factory The SLF4J logger factory.
   * @return the logging framework of this factory, started.
   */
  static LoggingBackend detect(@Nullable ILoggerFactory factory) {
    String name = factory != null ? factory.getClass().getName() : "none";
    @Nullable String version = factory != null ? version(factory.getClass()) : null;
    if (factory == null || !isLogback(factory)) return new UnsupportedLoggingBackend(name, version);
    try {
      return LogbackBackend.start(factory);
    } catch (LinkageError | RuntimeException ex) {
      // A version of logback without the API used
      return new UnsupportedLoggingBackend(name, version);
    }
  }

  /**
   * @param type A class of the logging framework.
   * @return the implementation version in the manifest of its JAR, or {@code null}.
   */
  static @Nullable String version(Class<?> type) {
    @Nullable Package p = type.getPackage();
    @Nullable String version = p != null ? p.getImplementationVersion() : null;
    return version == null || version.isBlank() ? null : version;
  }

  private static boolean isLogback(@Nullable ILoggerFactory factory) {
    if (factory == null || !LOGBACK_CONTEXT.equals(factory.getClass().getName())) return false;
    try {
      // The same class as the overlay sees, not one of another class loader
      return Class.forName(LOGBACK_CONTEXT, false, LoggingBackends.class.getClassLoader()).isInstance(factory);
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

}
