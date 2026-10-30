package org.pageseeder.bzadmin.system;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Set;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.bzadmin.system.ThreadOrigin.Loader;

/**
 * What {@link ListThreads} and {@link GetThreadInfo} write about a thread.
 *
 * <pre>{@code
 * <thread id="49" name="qtp1409442094-49" priority="5" state="RUNNABLE" alive="true" daemon="false" group="main"
 *         in-native="true" blocked-count="0" waited-count="12" allocated-bytes="1048576">
 *   <origin component="Jetty" role="selector" basis="stack" loader="other"/>
 *   <activity class="org.eclipse.jetty.io.ManagedSelector" method="nioSelect" file="ManagedSelector.java" line="180"/>
 *   <lock name="java.lang.Object@1b2c3d" owner-id="51" owner-name="qtp1409442094-51"/>
 *   <times cpu="70000" user="60000" system="10000"/>
 * </thread>
 * }</pre>
 *
 * <p>Times are in nanoseconds. A value that the JVM does not provide is absent: {@code times}
 * without thread CPU time, {@code allocated-bytes} without the JDK-specific allocation counter
 * (read through the platform MBean server by operation name, never {@code com.sun.management}),
 * {@code lock} when the thread waits for nothing, {@code group} when the thread has ended.
 */
final class ThreadDetails {

  private static final String THREADING = "java.lang:type=Threading";

  private ThreadDetails() {
  }

  /**
   * Writes the attributes of a thread, the same as Berlioz's {@code ListThreads} and more; the
   * caller writes its children and ends the object.
   */
  static void startThread(OutputWriter out, ThreadInfo info, @Nullable Thread thread, boolean deadlocked,
      @Nullable Long allocated) {
    out.startObject("thread");
    out.field("id", info.getThreadId());
    out.field("name", info.getThreadName());
    out.field("priority", info.getPriority());
    out.field("state", info.getThreadState().name());
    out.field("alive", true);
    out.field("daemon", info.isDaemon());
    out.optionalField("group", group(thread));
    if (info.getThreadId() == Thread.currentThread().getId()) out.field("current", "true");
    if (info.isInNative()) out.field("in-native", true);
    if (deadlocked) out.field("deadlocked", true);
    out.field("blocked-count", info.getBlockedCount());
    out.field("waited-count", info.getWaitedCount());
    out.optionalField("allocated-bytes", allocated);
  }

  /**
   * Writes the origin, the activity, the lock and the CPU times of a thread.
   *
   * @param cpu The bean to read the CPU time with, or {@code null} if not available.
   */
  static void writeChildren(OutputWriter out, ThreadInfo info, ThreadOrigin origin, @Nullable ThreadMXBean cpu) {
    out.startObject("origin");
    out.field("component", origin.component);
    out.field("role", origin.role.value());
    out.field("basis", origin.basis.value());
    out.field("loader", origin.loader.value());
    out.endObject();
    if (origin.activity != null) writeFrame(out, "activity", origin.activity);
    if (info.getLockName() != null) {
      out.startObject("lock");
      out.field("name", info.getLockName());
      if (info.getLockOwnerId() >= 0) {
        out.field("owner-id", info.getLockOwnerId());
        out.optionalField("owner-name", info.getLockOwnerName());
      }
      out.endObject();
    }
    if (cpu != null) {
      // -1 when the thread has ended
      long total = cpu.getThreadCpuTime(info.getThreadId());
      long user = cpu.getThreadUserTime(info.getThreadId());
      if (total >= 0 && user >= 0) {
        out.startObject("times");
        out.field("cpu", total);
        out.field("user", user);
        out.field("system", Math.max(0, total - user));
        out.endObject();
      }
    }
  }

  static void writeFrame(OutputWriter out, String element, StackTraceElement frame) {
    out.startObject(element);
    out.field("class", frame.getClassName());
    out.field("method", frame.getMethodName());
    out.optionalField("file", frame.getFileName());
    if (frame.getLineNumber() >= 0) out.field("line", frame.getLineNumber());
    if (frame.isNativeMethod()) out.field("native", true);
    out.endObject();
  }

  @SuppressWarnings("java:S3014") // Only read, to tell the JVM's threads apart
  static ThreadOrigin origin(ThreadInfo info, @Nullable Thread thread, StackTraceElement @Nullable [] stack) {
    ThreadGroup group = thread != null ? thread.getThreadGroup() : null;
    Loader loader = thread != null ? loader(thread, ThreadDetails.class.getClassLoader()) : Loader.UNKNOWN;
    return ThreadOrigin.guess(info.getThreadName(), isJvmGroup(group), loader,
        stack != null ? stack : new StackTraceElement[0]);
  }

  /**
   * @return the bean to read the CPU time of threads, or {@code null} if it is not supported or enabled.
   */
  static @Nullable ThreadMXBean cpu(ThreadMXBean bean) {
    // Enabling it would change the JVM: only read it when it is on (the default on HotSpot)
    return bean.isThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled() ? bean : null;
  }

  /**
   * @return the IDs of the deadlocked threads (monitors and ownable synchronizers when supported).
   */
  static Set<Long> deadlocked(ThreadMXBean bean) {
    long[] ids = bean.isSynchronizerUsageSupported() ? bean.findDeadlockedThreads() : bean.findMonitorDeadlockedThreads();
    Set<Long> deadlocked = new HashSet<>();
    if (ids != null) for (long id : ids) deadlocked.add(id);
    return deadlocked;
  }

  /**
   * The memory allocated by each thread since it started, from the JDK-specific operation
   * {@code getThreadAllocatedBytes(long[])} of the platform threading MBean, when it is enabled.
   *
   * @param ids The IDs of the threads.
   * @return the bytes in the same order, -1 for a thread that ended, or {@code null} if not available.
   */
  static long @Nullable [] allocatedBytes(long[] ids) {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    try {
      ObjectName name = new ObjectName(THREADING);
      if (!Boolean.TRUE.equals(server.getAttribute(name, "ThreadAllocatedMemoryEnabled"))) return null;
      Object bytes = server.invoke(name, "getThreadAllocatedBytes", new Object[]{ids}, new String[]{long[].class.getName()});
      return bytes instanceof long[] && ((long[]) bytes).length == ids.length ? (long[]) bytes : null;
    } catch (JMException | RuntimeException ex) {
      return null;
    }
  }

  /**
   * @return the value at an index if not negative, else {@code null}.
   */
  static @Nullable Long valueAt(long @Nullable [] values, int index) {
    return values != null && values[index] >= 0 ? values[index] : null;
  }

  /**
   * @return the context class loader of a thread, relative to the class loader of the application.
   */
  static Loader loader(Thread thread, @Nullable ClassLoader application) {
    try {
      ClassLoader context = thread.getContextClassLoader();
      if (context == null) return Loader.NONE;
      for (ClassLoader loader = context; loader != null; loader = loader.getParent()) {
        if (loader == application) return Loader.APPLICATION;
      }
      if (context == ClassLoader.getSystemClassLoader() || context == ClassLoader.getPlatformClassLoader()) return Loader.SYSTEM;
      return Loader.OTHER;
    } catch (SecurityException ex) {
      return Loader.UNKNOWN;
    }
  }

  /**
   * @return whether a thread group belongs to the JVM: {@code system} and its groups other than {@code main}.
   */
  @SuppressWarnings("java:S3014") // Only read, to tell the JVM's threads apart
  static boolean isJvmGroup(@Nullable ThreadGroup group) {
    try {
      for (ThreadGroup g = group; g != null; g = g.getParent()) {
        if ("main".equals(g.getName())) return false;
        if (g.getParent() == null) return true;
      }
    } catch (SecurityException ex) {
      // Unknown
    }
    return false;
  }

  @SuppressWarnings("java:S3014") // Only read, to report the name of the group
  private static @Nullable String group(@Nullable Thread thread) {
    ThreadGroup group = thread != null ? thread.getThreadGroup() : null;
    return group != null ? group.getName() : null;
  }

}
