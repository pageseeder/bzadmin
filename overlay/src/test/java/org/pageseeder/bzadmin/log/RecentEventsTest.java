package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.slf4j.LoggerFactory;

/**
 * The recent events without a logging backend: the ring (overflow, threshold, concurrent capture),
 * the bounds of an event, the output of the generators, and a host without logback (the unit tests
 * run without it; the logback adapter has its own tests, {@code logbackTest}).
 */
final class RecentEventsTest {

  @Test
  void keepsTheMostRecentEventsWhenFull() {
    RecentEvents events = new RecentEvents(4, EventLevel.INFO);
    for (int i = 0; i < 10; i++) events.add(event(EventLevel.INFO, "event " + i));
    RecentEvents.Snapshot snapshot = events.snapshot();
    assertEquals(10, snapshot.captured());
    assertEquals(List.of("event 6", "event 7", "event 8", "event 9"), messages(snapshot));
    assertEquals(4, events.capacity());
  }

  @Test
  void startsEmpty() {
    RecentEvents events = new RecentEvents(RecentEvents.DEFAULT_CAPACITY, RecentEvents.DEFAULT_THRESHOLD);
    assertEquals(0, events.snapshot().captured());
    assertTrue(events.snapshot().events().isEmpty());
    assertEquals(256, events.capacity());
    assertEquals(EventLevel.INFO, events.threshold());
    assertThrows(IllegalArgumentException.class, () -> new RecentEvents(0, EventLevel.INFO));
  }

  @Test
  void capturesAtOrAboveTheThreshold() {
    RecentEvents events = new RecentEvents(8, EventLevel.WARN);
    assertFalse(events.accepts(EventLevel.TRACE));
    assertFalse(events.accepts(EventLevel.INFO));
    assertTrue(events.accepts(EventLevel.WARN));
    assertTrue(events.accepts(EventLevel.ERROR));
    events.setThreshold(EventLevel.OFF);
    for (EventLevel level : EventLevel.values()) assertFalse(events.accepts(level), level.name());
    events.setThreshold(EventLevel.TRACE);
    for (EventLevel level : EventLevel.values()) assertEquals(level != EventLevel.OFF, events.accepts(level), level.name());
  }

  /**
   * Threads adding events while others read: no thread waits, every event is counted, and each
   * snapshot holds at most the capacity, in the order each thread added them.
   */
  @Test
  void capturesConcurrentlyWithoutLocking() throws Exception {
    int threads = 8;
    int each = 20_000;
    RecentEvents events = new RecentEvents(64, EventLevel.TRACE);
    ExecutorService executor = Executors.newFixedThreadPool(threads + 2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicBoolean writing = new AtomicBoolean(true);
    try {
      List<Future<?>> writers = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        String thread = "t" + t;
        writers.add(executor.submit(() -> {
          start.await();
          for (int i = 0; i < each; i++) events.add(new RecentEvent(i, EventLevel.INFO, "logger", thread, String.valueOf(i), null, false));
          return null;
        }));
      }
      List<Future<Integer>> readers = new ArrayList<>();
      for (int r = 0; r < 2; r++) {
        readers.add(executor.submit(() -> {
          start.await();
          int snapshots = 0;
          while (writing.get()) {
            RecentEvents.Snapshot snapshot = events.snapshot();
            assertTrue(snapshot.events().size() <= 64);
            Map<String, Long> last = new HashMap<>();
            for (RecentEvent event : snapshot.events()) {
              Long previous = last.put(event.thread(), event.timestamp());
              assertTrue(previous == null || previous < event.timestamp(), "In order");
            }
            snapshots++;
          }
          return snapshots;
        }));
      }
      start.countDown();
      for (Future<?> writer : writers) writer.get(30, TimeUnit.SECONDS);
      writing.set(false);
      for (Future<Integer> reader : readers) assertTrue(reader.get(30, TimeUnit.SECONDS) > 0);
    } finally {
      executor.shutdownNow();
    }
    RecentEvents.Snapshot snapshot = events.snapshot();
    assertEquals((long) threads * each, snapshot.captured());
    assertEquals(64, snapshot.events().size());
  }

  @Test
  void boundsTheTextOfAnEvent() {
    RecentEvent.Budget budget = new RecentEvent.Budget(10);
    assertEquals("12345", budget.take("12345"));
    assertNull(budget.take(null));
    assertFalse(budget.truncated());
    assertEquals("abcde…", budget.take("abcdefgh"));
    assertTrue(budget.exhausted());
    assertTrue(budget.truncated());
    assertEquals("…", budget.take("more"));
    // A surrogate pair is not split
    assertEquals("a…", RecentEvent.Budget.cut("a😀b", 2));
    assertEquals("short", RecentEvent.Budget.cut("short", 5));
    String name = "x".repeat(1000);
    RecentEvent event = new RecentEvent(0, EventLevel.INFO, name, name, "m", null, false);
    assertEquals(RecentEvent.MAX_NAME + 1, event.logger().length());
    assertEquals(RecentEvent.MAX_NAME + 1, event.thread().length());
  }

  @Test
  void parsesLevels() {
    assertEquals(EventLevel.WARN, EventLevel.of("WARN"));
    assertNull(EventLevel.of("warn"));
    assertNull(EventLevel.of("BOGUS"));
    assertNull(EventLevel.of(null));
  }

  /**
   * The unit tests run as a host without logback: the overlay still loads, with no recent events
   * and no detected log directories.
   */
  @Test
  void worksWithoutLogback() {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("ch.qos.logback.classic.LoggerContext"));
    LoggingBackend backend = LoggingBackends.detect(LoggerFactory.getILoggerFactory());
    assertTrue(backend instanceof UnsupportedLoggingBackend, backend.getClass().getName());
    assertEquals(LoggerFactory.getILoggerFactory().getClass().getName(), backend.name());
    assertNull(backend.recentEvents());
    assertNull(backend.logDirectories());
    assertNull(backend.rootLevel());
    backend.stop();
    LoggingBackends.start();
    assertNull(LoggingBackends.current().recentEvents());
    LoggingBackends.stop();
    LoggingBackends.stop();
    assertNull(LoggingBackends.detect(null).recentEvents());
    assertEquals("none", LoggingBackends.detect(null).name());
  }

  @Test
  void reportsUnsupportedRecentEvents() {
    LoggingBackend unsupported = new UnsupportedLoggingBackend("org.slf4j.helpers.NOPLoggerFactory", null);
    assertEquals("<no-recent-logs backend=\"org.slf4j.helpers.NOPLoggerFactory\"/>", xml(out -> GetRecentEvents.write(unsupported, out)));
    StringWriter xml = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(xml);
    Response response = SetRecentEventsThreshold.set(unsupported, EventLevel.WARN, out);
    out.flush();
    assertEquals(501, response.statusCode());
    assertEquals("<no-recent-logs backend=\"org.slf4j.helpers.NOPLoggerFactory\"/>", xml.toString());
  }

  @Test
  void omitsUnavailableEventMetadata() {
    Fake backend = new Fake();
    backend.root = null;
    RecentEvent.Trace trace = new RecentEvent.Trace("java.lang.Exception", null, List.of(), 0, null);
    backend.events.add(new RecentEvent(0, EventLevel.INFO, null, null, null, trace, false));
    String output = xml(out -> GetRecentEvents.write(backend, out));
    assertFalse(output.contains("root-level="), output);
    assertFalse(output.contains("version="), output);
    assertFalse(output.contains("logger="), output);
    assertFalse(output.contains("thread="), output);
    assertFalse(output.contains("message="), output);
    assertFalse(output.contains("<cause"), output);
    assertTrue(output.contains("<throwable class=\"java.lang.Exception\"/>"), output);
  }

  @Test
  void writesTheRecentEvents() {
    Fake backend = new Fake();
    RecentEvent.Trace cause = new RecentEvent.Trace("java.io.IOException", null, List.of("b.C.d(C.java:2)"), 3, null);
    RecentEvent.Trace trace = new RecentEvent.Trace("java.lang.IllegalStateException", "state <&>", List.of("a.B.c(B.java:1)"), 0, cause);
    backend.events.add(new RecentEvent(1792710123456L, EventLevel.WARN, "org.example.Service", "main", "Hello <b>&amp;", trace, true));
    backend.events.add(new RecentEvent(1792710123457L, EventLevel.ERROR, "org.example.Service", "main", null, null, false));
    String xml = xml(out -> GetRecentEvents.write(backend, out));
    assertTrue(xml.startsWith("<recent-logs backend=\"fake\" threshold=\"INFO\" root-level=\"WARN\" capacity=\"4\" captured=\"2\">"), xml);
    assertTrue(xml.contains("<event level=\"WARN\" logger=\"org.example.Service\" thread=\"main\" timestamp=\"1792710123456\""
        + " datetime=\"2026-10-22T23:02:03.456Z\" message=\"Hello &lt;b>&amp;amp;\" truncated=\"true\">"), xml);
    assertTrue(xml.contains("<throwable class=\"java.lang.IllegalStateException\" message=\"state &lt;&amp;>\"><step>a.B.c(B.java:1)</step>"
        + "<cause class=\"java.io.IOException\" omitted=\"3\"><step>b.C.d(C.java:2)</step></cause></throwable>"), xml);
    assertTrue(xml.contains("<event level=\"ERROR\" logger=\"org.example.Service\" thread=\"main\" timestamp=\"1792710123457\" datetime=\"2026-10-22T23:02:03.457Z\"/>"), xml);

    StringWriter threshold = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(threshold);
    assertEquals(200, SetRecentEventsThreshold.set(backend, EventLevel.ERROR, out).statusCode());
    out.flush();
    assertEquals("<recent-logs-threshold level=\"ERROR\" was=\"INFO\"/>", threshold.toString());
    assertEquals(EventLevel.ERROR, backend.events.threshold());
  }

  private static RecentEvent event(EventLevel level, String message) {
    return new RecentEvent(System.currentTimeMillis(), level, "logger", "thread", message, null, false);
  }

  private static List<String> messages(RecentEvents.Snapshot snapshot) {
    List<String> messages = new ArrayList<>();
    for (RecentEvent event : snapshot.events()) messages.add(event.message());
    return messages;
  }

  private static String xml(java.util.function.Consumer<XmlOutputAdapter> write) {
    StringWriter xml = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(xml);
    write.accept(out);
    out.flush();
    return xml.toString();
  }

  private static final class Fake implements LoggingBackend {

    private final RecentEvents events = new RecentEvents(4, EventLevel.INFO);

    private @Nullable EventLevel root = EventLevel.WARN;

    @Override
    public String name() {
      return "fake";
    }

    @Override
    public List<Path> logDirectories() {
      return List.of();
    }

    @Override
    public RecentEvents recentEvents() {
      return this.events;
    }

    @Override
    public @Nullable EventLevel rootLevel() {
      return this.root;
    }

    @Override
    public void stop() {
      // Nothing attached
    }
  }

}
