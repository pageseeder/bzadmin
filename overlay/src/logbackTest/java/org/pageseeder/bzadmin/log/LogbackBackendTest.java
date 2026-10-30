package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * The logback adapter, on a logger context of its own: capture and its threshold, distinct from the
 * levels of the loggers, bounds, overflow, concurrent capture, and the lifecycle (reset, stop of the
 * context, stop of the adapter).
 */
final class LogbackBackendTest {

  private LoggerContext context;

  private LogbackBackend backend;

  @BeforeEach
  void start() {
    this.context = new LoggerContext();
    this.context.start();
    this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
    this.backend = LogbackBackend.start(this.context);
  }

  @AfterEach
  void stop() {
    this.backend.stop();
    this.context.stop();
  }

  @Test
  void detectsLogback() {
    LoggingBackend detected = LoggingBackends.detect(this.context);
    try {
      assertInstanceOf(LogbackBackend.class, detected);
      assertEquals("logback", detected.name());
      // From the manifest of logback-classic
      assertEquals(LoggerContext.class.getPackage().getImplementationVersion(), detected.version());
      assertTrue(detected.version() != null && !detected.version().isBlank(), "A version");
    } finally {
      detected.stop();
    }
    // The logger factory of SLF4J on this classpath
    LoggingBackends.start();
    try {
      assertEquals("logback", LoggingBackends.current().name());
    } finally {
      LoggingBackends.stop();
    }
  }

  @Test
  void capturesTheEventsThatTheApplicationLogs() {
    Logger logger = this.context.getLogger("org.example.App");
    logger.debug("not logged");
    logger.info("Hello {}", "world");
    logger.warn("Careful");
    List<RecentEvent> events = events();
    assertEquals(List.of("Hello world", "Careful"), messages(events));
    RecentEvent hello = events.get(0);
    assertEquals(EventLevel.INFO, hello.level());
    assertEquals("org.example.App", hello.logger());
    assertEquals(Thread.currentThread().getName(), hello.thread());
    assertTrue(Math.abs(System.currentTimeMillis() - hello.timestamp()) < 60_000);
    assertNull(hello.throwable());
    assertFalse(hello.truncated());
    assertTrue(this.backend.isCapturing());
  }

  /**
   * The capture threshold selects among the events logged; it neither logs more nor changes the
   * levels of the loggers.
   */
  @Test
  void distinguishesTheThresholdFromTheLevelsOfTheLoggers() {
    RecentEvents events = this.backend.recentEvents();
    assertEquals(EventLevel.INFO, events.threshold());
    assertEquals(EventLevel.INFO, this.backend.rootLevel());
    events.setThreshold(EventLevel.DEBUG);
    Logger app = this.context.getLogger("org.example.App");
    app.debug("below the level of the logger");
    Logger verbose = this.context.getLogger("org.example.Verbose");
    verbose.setLevel(Level.DEBUG);
    verbose.debug("logged at debug");
    verbose.trace("below both");
    assertEquals(List.of("logged at debug"), messages(events()));
    assertEquals(EventLevel.INFO, this.backend.rootLevel());
    assertNull(app.getLevel());

    events.setThreshold(EventLevel.WARN);
    app.info("below the threshold");
    app.error("an error");
    assertEquals(List.of("logged at debug", "an error"), messages(events()));

    events.setThreshold(EventLevel.OFF);
    app.error("off");
    assertEquals(2, events().size());
    assertEquals(Level.INFO, this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).getLevel());
  }

  @Test
  void keepsTextNotTheArguments() {
    StringBuilder mutable = new StringBuilder("before");
    this.context.getLogger("app").info("Value {}", mutable);
    mutable.replace(0, mutable.length(), "after");
    assertEquals(List.of("Value before"), messages(events()));
  }

  @Test
  void capturesEventsWithMissingMessageAndLogger() {
    ch.qos.logback.classic.spi.LoggingEvent raw = new ch.qos.logback.classic.spi.LoggingEvent();
    RecentEvent event = LogbackBackend.event(raw, EventLevel.INFO);
    assertNull(event.message());
    assertNull(event.logger());
    assertNull(event.throwable());
    assertFalse(event.truncated());
  }

  @Test
  void writesThrowablesAsText() {
    Exception cause = new IllegalArgumentException("the cause");
    Exception exception = new IllegalStateException("failed", cause);
    this.context.getLogger("app").error("Oops", exception);
    RecentEvent event = events().get(0);
    RecentEvent.Trace trace = event.throwable();
    assertEquals("java.lang.IllegalStateException", trace.className());
    assertEquals("failed", trace.message());
    assertTrue(trace.steps().get(0).startsWith(LogbackBackendTest.class.getName() + ".writesThrowablesAsText("), trace.steps().get(0));
    RecentEvent.Trace nested = trace.cause();
    assertEquals("java.lang.IllegalArgumentException", nested.className());
    assertEquals("the cause", nested.message());
    // The frames in common with the enclosing throwable are left out
    assertTrue(nested.omitted() > 0);
    assertTrue(nested.steps().size() < cause.getStackTrace().length);
    assertNull(nested.cause());
    // The stack of a JUnit test is deeper than the bound
    assertEquals(RecentEvent.MAX_STEPS, trace.steps().size());
    assertEquals(exception.getStackTrace().length - RecentEvent.MAX_STEPS, trace.omitted());
  }

  @Test
  void boundsTheTextOfAnEvent() {
    Logger logger = this.context.getLogger("app");
    String text = "x".repeat(100_000);
    logger.info(text);
    RecentEvent big = last();
    assertEquals(RecentEvent.MAX_TEXT + 1, big.message().length());
    assertTrue(big.message().endsWith("…"));
    assertTrue(big.truncated());

    // A chain of causes longer than the bound
    Throwable chain = new RuntimeException("0");
    for (int i = 1; i < 20; i++) chain = new RuntimeException(String.valueOf(i), chain);
    logger.error("chain", chain);
    RecentEvent event = last();
    int depth = 0;
    for (RecentEvent.Trace t = event.throwable(); t != null; t = t.cause()) depth++;
    assertEquals(RecentEvent.MAX_CAUSES, depth);
    assertTrue(event.truncated());

    // A long message and a throwable: the message first, then what fits of the throwable
    String message = "y".repeat(RecentEvent.MAX_TEXT - 10);
    logger.error(message, new IllegalStateException("message of the throwable"));
    RecentEvent full = last();
    assertEquals(RecentEvent.MAX_TEXT - 10, full.message().length());
    assertEquals("java.lang.…", full.throwable().className());
    assertTrue(full.throwable().steps().isEmpty());
    assertTrue(full.throwable().omitted() > 0);
    assertTrue(full.truncated());
  }

  @Test
  void keepsTheMostRecentEventsOnOverflow() {
    Logger logger = this.context.getLogger("app");
    for (int i = 0; i < 300; i++) logger.info("event {}", i);
    RecentEvents.Snapshot snapshot = this.backend.recentEvents().snapshot();
    assertEquals(300, snapshot.captured());
    assertEquals(RecentEvents.DEFAULT_CAPACITY, snapshot.events().size());
    assertEquals("event 44", snapshot.events().get(0).message());
    assertEquals("event 299", snapshot.events().get(RecentEvents.DEFAULT_CAPACITY - 1).message());
  }

  @Test
  void capturesFromConcurrentThreads() throws InterruptedException, ExecutionException, TimeoutException {
    int threads = 8;
    int each = 5_000;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        Logger logger = this.context.getLogger("app." + t);
        futures.add(executor.submit(() -> {
          start.await();
          for (int i = 0; i < each; i++) logger.info("event {}", i);
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) future.get(60, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }
    RecentEvents.Snapshot snapshot = this.backend.recentEvents().snapshot();
    assertEquals((long) threads * each, snapshot.captured());
    assertEquals(RecentEvents.DEFAULT_CAPACITY, snapshot.events().size());
  }

  @Test
  void doesNotReceiveEventsOfLoggersThatAreNotAdditive() {
    Logger isolated = this.context.getLogger("org.example.Isolated");
    isolated.setAdditive(false);
    isolated.error("not captured");
    assertTrue(events().isEmpty());
  }

  @Test
  void attachesAgainAfterAReset() {
    this.context.reset();
    assertTrue(this.backend.isCapturing());
    this.context.getLogger("app").info("after reset");
    assertEquals(List.of("after reset"), messages(events()));
    assertFalse(this.context.getCopyOfListenerList().isEmpty());
  }

  @Test
  void detachesWhenTheContextStops() {
    this.context.stop();
    assertFalse(this.backend.isCapturing());
  }

  @Test
  void detachesWhenStopped() {
    this.backend.stop();
    assertFalse(this.backend.isCapturing());
    assertTrue(this.context.getCopyOfListenerList().isEmpty());
    Logger root = this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    assertNull(root.getAppender(LogbackBackend.APPENDER));
    this.context.getLogger("app").error("after stop");
    assertTrue(events().isEmpty());
    // Not attached again by a reset
    this.context.reset();
    assertFalse(this.backend.isCapturing());
  }

  @Test
  void listsTheDirectoriesOfFileAppenders(@TempDir Path temp) {
    assertTrue(this.backend.logDirectories().isEmpty());
    FileAppender<ILoggingEvent> file = new FileAppender<>();
    file.setContext(this.context);
    file.setFile(temp.resolve("a/app.log").toString());
    this.context.getLogger("org.example").addAppender(file);
    // Inside an asynchronous appender
    FileAppender<ILoggingEvent> nested = new FileAppender<>();
    nested.setContext(this.context);
    nested.setFile(temp.resolve("b/other.log").toString());
    AsyncAppender async = new AsyncAppender();
    async.setContext(this.context);
    async.addAppender(nested);
    this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(async);
    // Without a file
    FileAppender<ILoggingEvent> none = new FileAppender<>();
    none.setContext(this.context);
    this.context.getLogger("org.other").addAppender(none);
    List<Path> directories = this.backend.logDirectories();
    assertEquals(2, directories.size(), directories.toString());
    assertTrue(directories.contains(temp.resolve("a")), directories.toString());
    assertTrue(directories.contains(temp.resolve("b")), directories.toString());
  }

  @Test
  void mapsLevels() {
    assertEquals(EventLevel.TRACE, LogbackBackend.level(Level.TRACE));
    assertEquals(EventLevel.TRACE, LogbackBackend.level(Level.ALL));
    assertEquals(EventLevel.DEBUG, LogbackBackend.level(Level.DEBUG));
    assertEquals(EventLevel.INFO, LogbackBackend.level(Level.INFO));
    assertEquals(EventLevel.WARN, LogbackBackend.level(Level.WARN));
    assertEquals(EventLevel.ERROR, LogbackBackend.level(Level.ERROR));
    assertEquals(EventLevel.OFF, LogbackBackend.level(Level.OFF));
    assertNull(LogbackBackend.level(null));
  }

  @Test
  void usesTheLoggerFactoryOfSlf4j() {
    assertTrue(LoggerFactory.getILoggerFactory() instanceof LoggerContext);
  }

  private List<RecentEvent> events() {
    return this.backend.recentEvents().snapshot().events();
  }

  private RecentEvent last() {
    List<RecentEvent> events = events();
    return events.get(events.size() - 1);
  }

  private static List<String> messages(List<RecentEvent> events) {
    return events.stream().map(RecentEvent::message).collect(Collectors.toList());
  }

}
