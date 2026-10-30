package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
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
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class LoggingBackends {

  private static final String LOGBACK_CONTEXT = "ch.qos.logback.classic.LoggerContext";

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
      }
      return backend;
    }
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
