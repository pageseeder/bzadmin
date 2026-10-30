package org.pageseeder.bzadmin.system;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.pageseeder.berlioz.content.Generator;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.output.OutputWriter;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.bzadmin.access.AdminGuard;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * The live threads with a guess at their origin, what they are doing and what they use: replaces
 * Berlioz's {@code ListThreads} with the same {@code thread} elements and more
 * ({@link ThreadDetails}).
 *
 * <pre>{@code
 * <threads live="30" listed="30" cpu-time="true" allocated-bytes="true" deadlocks="0">
 *   <thread id="1" name="main" .../>
 * </threads>
 * }</pre>
 *
 * <p>The origin needs the whole stack of each thread ({@link ThreadOrigin}), so this reads every
 * stack, but writes none: one stack is {@link GetThreadInfo}. At most {@value #MAX_THREADS}
 * threads, by ID ({@code truncated="true"} beyond). Virtual threads are not listed. The
 * {@code threadtime} and {@code stacktraces} parameters of Berlioz's generator are ignored:
 * the CPU times are always included when available.
 *
 * <p>In JSON: {@code {"live": 30, ..., "threads": [{"id": 1, ...}]}}.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class ListThreads implements Generator {

  static final int MAX_THREADS = 2000;

  @Override
  public Response generate(Request req, OutputWriter out) {
    if (!AdminGuard.allows(req, AdminPermission.VIEW)) return AdminGuard.denied(out);
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
    List<Thread> threads = new ArrayList<>(stacks.keySet());
    threads.sort(Comparator.comparingLong(Thread::getId));
    boolean truncated = threads.size() > MAX_THREADS;
    if (truncated) threads = threads.subList(0, MAX_THREADS);
    long[] ids = threads.stream().mapToLong(Thread::getId).toArray();
    // Without stack (depth 0): state, lock and counters
    ThreadInfo[] infos = bean.getThreadInfo(ids, 0);
    ThreadMXBean cpu = ThreadDetails.cpu(bean);
    long[] allocated = ThreadDetails.allocatedBytes(ids);
    Set<Long> deadlocked = ThreadDetails.deadlocked(bean);

    out.startObject("threads");
    out.field("live", bean.getThreadCount());
    out.field("listed", Arrays.stream(infos).filter(Objects::nonNull).count());
    if (truncated) out.field("truncated", true);
    out.field("cpu-time", cpu != null);
    out.field("allocated-bytes", allocated != null);
    out.field("deadlocks", deadlocked.size());
    out.startArray("threads", ContextOption.JSON_ONLY);
    for (int i = 0; i < ids.length; i++) {
      ThreadInfo info = infos[i];
      // Ended since
      if (info == null) continue;
      Thread thread = threads.get(i);
      ThreadDetails.startThread(out, info, thread, deadlocked.contains(ids[i]), ThreadDetails.valueAt(allocated, i));
      ThreadDetails.writeChildren(out, info, ThreadDetails.origin(info, thread, stacks.get(thread)), cpu);
      out.endObject();
    }
    out.endArray();
    out.endObject();
    return Response.ok();
  }

}
