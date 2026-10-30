package org.pageseeder.bzadmin.system;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * One thread, by ID (parameter {@code id}), with the same details as {@link ListThreads} and its
 * stack: replaces Berlioz's {@code GetThreadInfo}, with the locks each frame holds and at most
 * {@value #MAX_DEPTH} frames.
 *
 * <pre>{@code
 * <thread id="51" name="qtp1409442094-51" ... frames="7">
 *   <origin .../><activity .../><lock .../><times .../>
 *   <stacktrace>
 *     <element class="jdk.internal.misc.Unsafe" method="park" native="true"/>
 *     <element class="org.example.Cache" method="load" file="Cache.java" line="42">
 *       <locked class="org.example.Cache" identity="1b2c3d"/>
 *     </element>
 *   </stacktrace>
 *   <locked-synchronizers><locked class="java.util.concurrent.locks.ReentrantLock$NonfairSync" identity="4e5f6a"/></locked-synchronizers>
 * </thread>
 * }</pre>
 *
 * <p>{@code frames} is the depth of the whole stack; when it is more than the frames listed, the
 * thread has {@code stack-truncated="true"}. Without a valid ID: 400 {@code <no-thread reason="invalid-id"/>};
 * for a thread that has ended or does not exist: 404 {@code <no-thread id="…" reason="not-found"/>}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class GetThreadInfo implements Generator {

  static final int MAX_DEPTH = 256;

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    Long id = req.parameter("id").asLong().inRange(1L, Long.MAX_VALUE).optional();
    if (id == null) return noThread(out, null, "invalid-id", ContentStatus.BAD_REQUEST);
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] infos = bean.getThreadInfo(new long[]{id}, bean.isObjectMonitorUsageSupported(),
        bean.isSynchronizerUsageSupported(), MAX_DEPTH);
    ThreadInfo info = infos.length == 1 ? infos[0] : null;
    if (info == null) return noThread(out, id, "not-found", ContentStatus.NOT_FOUND);
    // The thread and its whole stack, for its origin
    Thread thread = null;
    StackTraceElement[] stack = null;
    for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
      if (entry.getKey().getId() == id) {
        thread = entry.getKey();
        stack = entry.getValue();
      }
    }
    long[] allocated = ThreadDetails.allocatedBytes(new long[]{id});
    boolean deadlocked = ThreadDetails.deadlocked(bean).contains(id);

    ThreadDetails.startThread(out, info, thread, deadlocked, ThreadDetails.valueAt(allocated, 0));
    StackTraceElement[] frames = info.getStackTrace();
    int depth = stack != null ? Math.max(stack.length, frames.length) : frames.length;
    out.field("frames", depth);
    if (depth > frames.length) out.field("stack-truncated", true);
    ThreadDetails.writeChildren(out, info, ThreadDetails.origin(info, thread, stack), ThreadDetails.cpu(bean));
    stack(info, frames, out);
    LockInfo[] synchronizers = info.getLockedSynchronizers();
    if (synchronizers.length > 0) {
      out.startArray("locked-synchronizers");
      for (LockInfo lock : synchronizers) locked(lock, out);
      out.endArray();
    }
    out.endObject();
    return Response.ok();
  }

  private static void stack(ThreadInfo info, StackTraceElement[] frames, OutputWriter out) {
    MonitorInfo[] monitors = info.getLockedMonitors();
    out.startArray("stacktrace");
    for (int i = 0; i < frames.length; i++) {
      StackTraceElement frame = frames[i];
      out.startObject("element");
      out.field("class", frame.getClassName());
      out.field("method", frame.getMethodName());
      out.optionalField("filename", frame.getFileName());
      if (frame.getLineNumber() >= 0) out.field("line", frame.getLineNumber());
      if (frame.isNativeMethod()) out.field("native", true);
      boolean any = false;
      for (MonitorInfo monitor : monitors) {
        if (monitor.getLockedStackDepth() != i) continue;
        if (!any) out.startArray("locks", ContextOption.JSON_ONLY);
        any = true;
        locked(monitor, out);
      }
      if (any) out.endArray();
      out.endObject();
    }
    out.endArray();
  }

  private static void locked(LockInfo lock, OutputWriter out) {
    out.startObject("locked");
    out.field("class", lock.getClassName());
    out.field("identity", Integer.toHexString(lock.getIdentityHashCode()));
    out.endObject();
  }

  private static Response noThread(OutputWriter out, @Nullable Long id, String reason, ContentStatus status) {
    out.startObject("no-thread");
    out.optionalField("id", id);
    out.field("reason", reason);
    out.endObject();
    return Response.status(status);
  }

}
