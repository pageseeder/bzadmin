package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.WarnStatus;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Issue;
import org.pageseeder.bzadmin.log.LoggingConfiguration.Verdict;

/**
 * The verdict, issues and content of the logging configuration, on a logger context of its own
 * ({@code docs/design/logging-configuration.md}).
 */
final class LogbackInspectionTest {

  @TempDir
  Path temp;

  private LoggerContext context;

  private LogbackBackend backend;

  @BeforeEach
  void start() {
    this.context = new LoggerContext();
    // Appenders that encode events need the MDC adapter that SLF4J would set
    this.context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
    this.context.start();
    root().setLevel(Level.INFO);
    this.backend = LogbackBackend.start(this.context);
  }

  @AfterEach
  void stop() {
    this.backend.stop();
    this.context.stop();
  }

  @Test
  void writesWithAFileAppender() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.WRITING, c.verdict);
    assertEquals(List.of(), types(c));
    assertEquals("logback", c.framework);
    LoggingConfiguration.Appender app = appender(c, "app");
    assertTrue(app.started);
    assertTrue(app.writes);
    assertEquals(this.temp.resolve("app.log").toString(), app.file);
    assertEquals(List.of("ROOT"), app.loggers);
    // The capture appender is listed, never writes
    LoggingConfiguration.Appender capture = appender(c, LogbackBackend.APPENDER);
    assertTrue(capture.capture);
    assertFalse(capture.writes);
    LoggingConfiguration.Logger root = c.loggers.get(0);
    assertEquals("ROOT", root.name);
    assertEquals(EventLevel.INFO, root.level);
    assertTrue(root.output);
  }

  @Test
  void writesNowhereAfterAReset() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    this.context.reset();
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.NOT_WRITING, c.verdict);
    // Not repeated as no-output for each logger
    assertFalse(types(c).contains("no-output"), types(c).toString());
    assertEquals(1, c.resets);
    assertNotNull(c.lastReset);
    assertEquals("reset", c.statusesSince);
  }

  @Test
  void reportsAFileAppenderThatCannotOpenItsFile() throws IOException {
    root().addAppender(file("ok", this.temp.resolve("ok.log")));
    // The parent of the file is a regular file
    Path blocker = Files.createFile(this.temp.resolve("blocker"));
    FileAppender<ILoggingEvent> broken = file("broken", blocker.resolve("app.log"));
    assertFalse(broken.isStarted());
    root().addAppender(broken);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.PROBLEMS, c.verdict);
    assertTrue(types(c).contains("stopped-appender"), types(c).toString());
    assertTrue(types(c).contains("error-status"), types(c).toString());
    Issue stopped = issue(c, "stopped-appender");
    assertEquals("broken", stopped.name);
    assertEquals(appender(c, "broken").id, stopped.appender);
    // Not started first
    assertEquals("broken", c.appenders.get(0).name);
    assertTrue(c.errors > 0);
    assertEquals("ERROR", c.statuses.get(0).level);
  }

  @Test
  void reportsAnAppenderStoppedAfterAWriteFailure() {
    root().addAppender(file("ok", this.temp.resolve("ok.log")));
    OutputStreamAppender<ILoggingEvent> failing = new OutputStreamAppender<>();
    failing.setContext(this.context);
    failing.setName("failing");
    failing.setEncoder(encoder());
    failing.setOutputStream(new java.io.OutputStream() {
      @Override
      public void write(int b) throws IOException {
        throw new IOException("disk full");
      }
    });
    failing.start();
    root().addAppender(failing);
    root().error("an event");
    assertFalse(failing.isStarted(), "Stops itself on an IO failure");
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.PROBLEMS, c.verdict);
    assertEquals("failing", issue(c, "stopped-appender").name);
    assertTrue(c.statuses.stream().anyMatch(s -> s.causes.stream().anyMatch(x -> "disk full".equals(x.message))));
  }

  @Test
  void reportsAnUnknownAppenderClass() throws JoranException {
    configure("<configuration><appender name='X' class='org.example.NoSuchAppender'/>"
        + "<root level='INFO'><appender-ref ref='X'/></root></configuration>");
    LoggingConfiguration c = this.backend.configuration();
    assertTrue(types(c).contains("error-status"), types(c).toString());
    assertTrue(c.verdict == Verdict.PROBLEMS || c.verdict == Verdict.NOT_WRITING, c.verdict.toString());
  }

  @Test
  void reportsALoggerWithoutOutput() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    Logger isolated = this.context.getLogger("org.example.Isolated");
    isolated.setAdditive(false);
    FileAppender<ILoggingEvent> stopped = file("isolated", this.temp.resolve("isolated.log"));
    stopped.stop();
    isolated.addAppender(stopped);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.PROBLEMS, c.verdict);
    assertEquals("org.example.Isolated", issue(c, "no-output").logger);
    LoggingConfiguration.Logger logger = logger(c, "org.example.Isolated");
    assertFalse(logger.additive);
    assertFalse(logger.output);
    // A child inherits the output of its additive parent, or not
    this.context.getLogger("org.example.Isolated.Child").setLevel(Level.DEBUG);
    this.context.getLogger("org.other").setLevel(Level.DEBUG);
    LoggingConfiguration again = this.backend.configuration();
    assertFalse(logger(again, "org.example.Isolated.Child").output);
    assertTrue(logger(again, "org.other").output);
  }

  @Test
  void followsAppendersHeldByAnAsyncAppender() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    Logger async = this.context.getLogger("org.example.Async");
    async.setAdditive(false);
    FileAppender<ILoggingEvent> nested = file("nested", this.temp.resolve("nested.log"));
    AsyncAppender holder = new AsyncAppender();
    holder.setContext(this.context);
    holder.setName("async");
    holder.addAppender(nested);
    holder.start();
    async.addAppender(holder);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.WRITING, c.verdict, types(c).toString());
    assertTrue(logger(c, "org.example.Async").output);
    assertEquals(appender(c, "async").id, appender(c, "nested").holder);

    nested.stop();
    LoggingConfiguration stopped = this.backend.configuration();
    assertFalse(logger(stopped, "org.example.Async").output);
    assertFalse(appender(stopped, "async").writes);
    assertEquals("nested", issue(stopped, "stopped-appender").name);
    assertEquals("org.example.Async", issue(stopped, "no-output").logger);
  }

  @Test
  void ignoresALoggerThatIsOff() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    Logger off = this.context.getLogger("org.example.Off");
    off.setLevel(Level.OFF);
    off.setAdditive(false);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.WRITING, c.verdict);
    assertFalse(logger(c, "org.example.Off").output);
    assertFalse(types(c).contains("no-output"));
  }

  @Test
  void warnsWhenOnlyTheConsoleWrites() {
    ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
    console.setContext(this.context);
    console.setName("console");
    console.setEncoder(encoder());
    console.start();
    root().addAppender(console);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(Verdict.WRITING, c.verdict);
    assertEquals(List.of("console-only"), types(c));
  }

  @Test
  void countsTheStatusesSinceTheLastReset() {
    // Timestamps well apart from the reset, rather than waiting for the clock to move
    long now = System.currentTimeMillis();
    this.context.getStatusManager().add(new ErrorStatus("before the reset", this) {
      @Override
      public long getTimestamp() {
        return now - 60_000;
      }
    });
    this.context.reset();
    root().addAppender(file("app", this.temp.resolve("app.log")));
    this.context.getStatusManager().add(new WarnStatus("after the reset", this) {
      @Override
      public long getTimestamp() {
        return now + 60_000;
      }
    });
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(0, c.errors);
    assertEquals(1, c.warnings);
    assertEquals(Verdict.WRITING, c.verdict);
    assertEquals(List.of("warning-status"), types(c));
    assertEquals(List.of("after the reset"), c.statuses.stream().map(s -> s.message).collect(Collectors.toList()));
    assertEquals(LogbackInspectionTest.class.getName(), c.statuses.get(0).origin);
  }

  @Test
  void boundsTheContent() {
    root().addAppender(file("app", this.temp.resolve("app.log")));
    for (int i = 0; i < LoggingConfiguration.MAX_LOGGERS + 100; i++) {
      this.context.getLogger("org.example.L" + i).setLevel(Level.DEBUG);
    }
    for (int i = 0; i < LoggingConfiguration.MAX_APPENDERS + 10; i++) {
      this.context.getLogger("org.example.L" + i).addAppender(file("f" + i, this.temp.resolve("f" + i + ".log")));
    }
    for (int i = 0; i < LoggingConfiguration.MAX_STATUSES + 10; i++) {
      this.context.getStatusManager().add(new WarnStatus("x".repeat(LoggingConfiguration.MAX_MESSAGE + 10), this));
    }
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(LoggingConfiguration.MAX_LOGGERS, c.loggers.size());
    assertEquals(LoggingConfiguration.MAX_LOGGERS + 101, c.loggerTotal);
    assertEquals("ROOT", c.loggers.get(0).name);
    assertEquals(LoggingConfiguration.MAX_APPENDERS, c.appenders.size());
    assertEquals(LoggingConfiguration.MAX_STATUSES, c.statuses.size());
    assertEquals(LoggingConfiguration.MAX_MESSAGE + 1, c.statuses.get(0).message.length());
    assertEquals(Verdict.WRITING, c.verdict, types(c).toString());
  }

  @Test
  void redactsStatusMessages() {
    this.context.getStatusManager().add(new ErrorStatus("Cannot connect to jdbc:postgresql://app:s3cret@db/app?password=other", this,
        new IllegalStateException("https://user:pass@host/x")));
    LoggingConfiguration c = this.backend.configuration();
    String message = c.statuses.get(0).message;
    assertFalse(message.contains("s3cret"), message);
    assertFalse(message.contains("other"), message);
    String cause = c.statuses.get(0).causes.get(0).message;
    assertFalse(cause.contains("pass@"), cause);
  }

  @Test
  void reportsTheTurboFiltersAndScan() {
    ch.qos.logback.classic.turbo.MarkerFilter filter = new ch.qos.logback.classic.turbo.MarkerFilter();
    filter.start();
    this.context.addTurboFilter(filter);
    LoggingConfiguration c = this.backend.configuration();
    assertEquals(List.of(filter.getClass().getName()), c.turboFilters);
    assertFalse(c.scan);
  }

  @Test
  void supportsLogback15AndLater() {
    assertTrue(LogbackInspection.supports("1.5.38"));
    assertTrue(LogbackInspection.supports("1.6.0"));
    assertTrue(LogbackInspection.supports(null));
    assertFalse(LogbackInspection.supports("1.4.14"));
    assertFalse(LogbackInspection.supports("1.3.0-alpha"));
    assertEquals(Verdict.UNAVAILABLE, LogbackInspection.inspect(this.context, new ConsoleAppender<>(), "1.4.14", 0, null).verdict);
  }

  @Test
  void findsTheParentOfALogger() {
    assertEquals("a.b", LogbackInspection.parent("a.b.c", "ROOT"));
    assertEquals("a.B", LogbackInspection.parent("a.B$Inner", "ROOT"));
    assertEquals("ROOT", LogbackInspection.parent("a", "ROOT"));
  }

  private Logger root() {
    return this.context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }

  private void configure(String xml) throws JoranException {
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(this.context);
    this.context.reset();
    configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  private PatternLayoutEncoder encoder() {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(this.context);
    encoder.setPattern("%msg%n");
    encoder.start();
    return encoder;
  }

  private FileAppender<ILoggingEvent> file(String name, Path path) {
    FileAppender<ILoggingEvent> file = new FileAppender<>();
    file.setContext(this.context);
    file.setName(name);
    file.setFile(path.toString());
    file.setEncoder(encoder());
    file.start();
    return file;
  }

  private static List<String> types(LoggingConfiguration c) {
    return c.issues.stream().map(i -> i.type).collect(Collectors.toList());
  }

  private static Issue issue(LoggingConfiguration c, String type) {
    return c.issues.stream().filter(i -> i.type.equals(type)).findFirst().orElseThrow(() -> new AssertionError(type + " in " + types(c)));
  }

  private static LoggingConfiguration.Appender appender(LoggingConfiguration c, String name) {
    return c.appenders.stream().filter(a -> name.equals(a.name)).findFirst().orElseThrow(() -> new AssertionError(name));
  }

  private static LoggingConfiguration.Logger logger(LoggingConfiguration c, String name) {
    LoggingConfiguration.Logger found = c.loggers.stream().filter(l -> l.name.equals(name)).findFirst().orElse(null);
    assertNotNull(found, name);
    return found;
  }

}
