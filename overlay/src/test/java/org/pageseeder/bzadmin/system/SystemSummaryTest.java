package org.pageseeder.bzadmin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.XmlOutputAdapter;

/**
 * The JVM summary: values that the JVM or the operating system does not provide are absent, never
 * zero, and the summary links to no JDK-specific or newer API.
 */
final class SystemSummaryTest {

  @Test
  void readsOptionalFractionsInOrder() {
    OptionalMetrics metrics = new OptionalMetrics(Map.<String, Object>of("SystemCpuLoad", 0.25, "ProcessCpuLoad", 0.5)::get);
    assertEquals(0.25, metrics.fraction("CpuLoad", "SystemCpuLoad"), "Java 11 name when the Java 14 one is missing");
    assertEquals(0.5, metrics.fraction("ProcessCpuLoad"));
  }

  @Test
  void leavesUnavailableMetricsAbsent() {
    // The JDK returns a negative value when a metric is not available
    OptionalMetrics metrics = new OptionalMetrics(Map.<String, Object>of("ProcessCpuLoad", -1.0, "CpuLoad", Double.NaN,
        "SystemCpuLoad", 1.5, "ProcessCpuTime", -1L, "Name", "text")::get);
    assertNull(metrics.fraction("ProcessCpuLoad"));
    assertNull(metrics.fraction("CpuLoad", "SystemCpuLoad"));
    assertNull(metrics.fraction("Missing"));
    assertNull(metrics.fraction("Name"));
    assertNull(metrics.count("ProcessCpuTime"));
    assertNull(metrics.count("Missing"));
  }

  @Test
  void omitsTheCpuWithoutOptionalMetrics() {
    String xml = xml(out -> GetSystemSummary.cpu(new OptionalMetrics(name -> null), out));
    assertEquals("", xml);
    String some = xml(out -> GetSystemSummary.cpu(new OptionalMetrics(Map.<String, Object>of("ProcessCpuTime", 2_500_000_000L)::get), out));
    assertEquals("<cpu process-time-ms=\"2500\"/>", some);
  }

  @Test
  void readsThePlatformMetricsWithoutFailing() {
    OptionalMetrics platform = OptionalMetrics.platform();
    Double load = platform.fraction("ProcessCpuLoad");
    assertTrue(load == null || load >= 0 && load <= 1);
    assertNull(platform.fraction("NoSuchAttribute"));
  }

  @Test
  void omitsUndefinedMemoryValues() {
    MemoryPoolMXBean pool = proxy(MemoryPoolMXBean.class, Map.of(
        "isValid", true, "getName", "Metaspace", "getType", MemoryType.NON_HEAP,
        "getUsage", new MemoryUsage(-1, 10, 20, -1)));
    MemoryPoolMXBean invalid = proxy(MemoryPoolMXBean.class, Map.of("isValid", false));
    MemoryMXBean memory = proxy(MemoryMXBean.class, Map.of(
        "getHeapMemoryUsage", new MemoryUsage(1, 2, 3, 4), "getNonHeapMemoryUsage", new MemoryUsage(-1, 5, 6, -1)));
    String xml = xml(out -> GetSystemSummary.memory(memory, List.of(pool, invalid), out));
    assertEquals("<memory-area type=\"heap\" init=\"1\" used=\"2\" committed=\"3\" max=\"4\"/>"
        + "<memory-area type=\"non-heap\" used=\"5\" committed=\"6\"/>"
        + "<memory-pool name=\"Metaspace\" type=\"non-heap\" used=\"10\" committed=\"20\"/>", xml);
  }

  @Test
  void omitsUndefinedCollectorValues() {
    GarbageCollectorMXBean collector = proxy(GarbageCollectorMXBean.class, Map.of(
        "isValid", true, "getName", "Epsilon", "getCollectionCount", -1L, "getCollectionTime", -1L,
        "getMemoryPoolNames", new String[]{"Heap"}));
    String xml = xml(out -> GetSystemSummary.collectors(List.of(collector), out));
    assertEquals("<garbage-collector name=\"Epsilon\" pools=\"Heap\"/>", xml);
  }

  @Test
  void countsThreadsByState() {
    String xml = xml(out -> GetSystemSummary.threads(ManagementFactory.getThreadMXBean(), out));
    assertTrue(xml.matches("<threads live=\"\\d+\" daemon=\"\\d+\" peak=\"\\d+\" started=\"\\d+\" new=\"\\d+\""
        + " runnable=\"[1-9]\\d*\" blocked=\"\\d+\" waiting=\"\\d+\" timed-waiting=\"\\d+\" terminated=\"\\d+\" deadlocked=\"0\"/>"), xml);
  }

  @Test
  void linksToNoJdkSpecificApi() throws Exception {
    for (Class<?> type : new Class<?>[]{GetSystemSummary.class, OptionalMetrics.class,
        ListThreads.class, GetThreadInfo.class, ThreadDetails.class, ThreadOrigin.class}) {
      String constants = classFile(type);
      assertFalse(constants.contains("com/sun/management"), type.getName());
      assertFalse(constants.contains("jdk/management"), type.getName());
    }
  }

  private static String classFile(Class<?> type) throws Exception {
    try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
      byte[] bytes = in.readAllBytes();
      // Major version 55: Java 11
      assertEquals(55, ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff), type.getName());
      return new String(bytes, StandardCharsets.ISO_8859_1);
    }
  }

  private static String xml(Consumer<OutputWriter> write) {
    StringWriter text = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(text);
    write.accept(out);
    out.close();
    return text.toString().replaceAll(">\\s+<", "><").trim();
  }

  private static <T> T proxy(Class<T> type, Map<String, Object> values) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
        (proxy, method, args) -> values.get(method.getName())));
  }

}
