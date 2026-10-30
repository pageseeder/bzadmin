package org.pageseeder.bzadmin.system;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * A bounded summary of the JVM from the Java 11 management API: uptime, CPU, memory areas and
 * pools, garbage collectors, and thread counts by state.
 *
 * <pre>{@code
 * <system-summary processors="8">
 *   <runtime start="2026-10-23T01:02:03Z" uptime-ms="123456" load-average="1.25"/>
 *   <cpu process-load="0.02" system-load="0.15" process-time-ms="5230"/>
 *   <memory-area type="heap" init="268435456" used="52428800" committed="268435456" max="4294967296"/>
 *   <memory-area type="non-heap" init="7667712" used="61865984" committed="66650112"/>
 *   <memory-pool name="G1 Eden Space" type="heap" used="23068672" committed="155189248" peak-used="23068672"/>
 *   <garbage-collector name="G1 Young Generation" collections="3" time-ms="12" pools="G1 Eden Space,G1 Survivor Space"/>
 *   <threads live="24" daemon="20" peak="26" started="40" new="0" runnable="6" blocked="0" waiting="10" timed-waiting="8" terminated="0" deadlocked="0"/>
 * </system-summary>
 * }</pre>
 *
 * <p>Units: memory in bytes, times in milliseconds, loads as a fraction of the capacity (0 to 1:
 * {@code process-load} and {@code system-load} over all processors), {@code load-average} as the
 * operating system reports it (the number of runnable entities averaged over a minute). A value
 * that the JVM or the operating system does not provide is absent, never zero: {@code max} of an
 * undefined maximum, {@code load-average} on Windows, the {@code cpu} element without the
 * JDK-specific operating system attributes ({@link OptionalMetrics}).
 *
 * <p>The thread counts by state come from the thread information without stack traces, and
 * {@code deadlocked} counts the threads in a deadlock (without naming them); thread
 * names, stack traces and CPU time per thread are not part of the summary ({@code ListThreads} and
 * {@code GetThreadInfo}, on demand).
 *
 * <p>In JSON: {@code {"processors": 8, "runtime": {...}, "cpu": {...}, "memoryAreas": [...],
 * "memoryPools": [...], "garbageCollectors": [...], "threads": {...}}} (names in camel case).
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetSystemSummary implements Generator {

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    out.startObject("system-summary");
    out.field("processors", os.getAvailableProcessors());
    runtime(ManagementFactory.getRuntimeMXBean(), os, out);
    cpu(OptionalMetrics.platform(), out);
    memory(ManagementFactory.getMemoryMXBean(), ManagementFactory.getMemoryPoolMXBeans(), out);
    collectors(ManagementFactory.getGarbageCollectorMXBeans(), out);
    threads(ManagementFactory.getThreadMXBean(), out);
    out.endObject();
    return Response.ok();
  }

  static void runtime(RuntimeMXBean runtime, OperatingSystemMXBean os, OutputWriter out) {
    out.startObject("runtime");
    out.field("start", Instant.ofEpochMilli(runtime.getStartTime()).toString());
    out.field("uptime-ms", runtime.getUptime());
    double load = os.getSystemLoadAverage();
    if (load >= 0) out.field("load-average", round(load));
    out.endObject();
  }

  static void cpu(OptionalMetrics metrics, OutputWriter out) {
    Double process = metrics.fraction("ProcessCpuLoad");
    // CpuLoad from Java 14, SystemCpuLoad before (deprecated since)
    Double system = metrics.fraction("CpuLoad", "SystemCpuLoad");
    Long time = metrics.count("ProcessCpuTime");
    if (process == null && system == null && time == null) return;
    out.startObject("cpu");
    if (process != null) out.field("process-load", round(process));
    if (system != null) out.field("system-load", round(system));
    if (time != null) out.field("process-time-ms", time / 1_000_000L);
    out.endObject();
  }

  static void memory(MemoryMXBean memory, List<MemoryPoolMXBean> pools, OutputWriter out) {
    out.startArray("memory-areas", ContextOption.JSON_ONLY);
    area("heap", memory.getHeapMemoryUsage(), out);
    area("non-heap", memory.getNonHeapMemoryUsage(), out);
    out.endArray();
    out.startArray("memory-pools", ContextOption.JSON_ONLY);
    for (MemoryPoolMXBean pool : pools) {
      if (pool.isValid()) pool(pool, out);
    }
    out.endArray();
  }

  private static void pool(MemoryPoolMXBean pool, OutputWriter out) {
    MemoryUsage usage = pool.getUsage();
    // Null when no longer valid
    if (usage == null) return;
    out.startObject("memory-pool");
    out.field("name", pool.getName());
    out.field("type", pool.getType() == MemoryType.HEAP ? "heap" : "non-heap");
    usage(usage, out);
    MemoryUsage peak = pool.getPeakUsage();
    if (peak != null) out.field("peak-used", peak.getUsed());
    out.endObject();
  }

  private static void area(String type, MemoryUsage usage, OutputWriter out) {
    out.startObject("memory-area");
    out.field("type", type);
    usage(usage, out);
    out.endObject();
  }

  private static void usage(MemoryUsage usage, OutputWriter out) {
    // -1 when undefined
    if (usage.getInit() >= 0) out.field("init", usage.getInit());
    out.field("used", usage.getUsed());
    out.field("committed", usage.getCommitted());
    if (usage.getMax() >= 0) out.field("max", usage.getMax());
  }

  static void collectors(List<GarbageCollectorMXBean> collectors, OutputWriter out) {
    out.startArray("garbage-collectors", ContextOption.JSON_ONLY);
    for (GarbageCollectorMXBean collector : collectors) {
      if (!collector.isValid()) continue;
      out.startObject("garbage-collector");
      out.field("name", collector.getName());
      // -1 when undefined
      long count = collector.getCollectionCount();
      long time = collector.getCollectionTime();
      if (count >= 0) out.field("collections", count);
      if (time >= 0) out.field("time-ms", time);
      out.field("pools", String.join(",", collector.getMemoryPoolNames()));
      out.endObject();
    }
    out.endArray();
  }

  static void threads(ThreadMXBean threads, OutputWriter out) {
    Map<Thread.State, Integer> states = new EnumMap<>(Thread.State.class);
    for (Thread.State state : Thread.State.values()) states.put(state, 0);
    // No stack trace (depth 0); a thread that ended since has no information
    for (ThreadInfo info : threads.getThreadInfo(threads.getAllThreadIds(), 0)) {
      if (info != null) states.merge(info.getThreadState(), 1, Integer::sum);
    }
    out.startObject("threads");
    out.field("live", threads.getThreadCount());
    out.field("daemon", threads.getDaemonThreadCount());
    out.field("peak", threads.getPeakThreadCount());
    out.field("started", threads.getTotalStartedThreadCount());
    for (Map.Entry<Thread.State, Integer> state : states.entrySet()) {
      out.field(state.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-'), state.getValue());
    }
    // Monitors and ownable synchronizers when supported: only reads the state of the locks
    out.field("deadlocked", ThreadDetails.deadlocked(threads).size());
    out.endObject();
  }

  private static double round(double value) {
    return Math.round(value * 1000) / 1000.0;
  }

}
