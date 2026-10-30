package org.pageseeder.bzadmin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.XmlOutputAdapter;
import org.pageseeder.bzadmin.system.ThreadOrigin.Loader;

/**
 * What the threads list and the thread details write about a thread, from the live JVM.
 */
final class ThreadDetailsTest {

  @Test
  void writesTheCurrentThread() {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    Thread current = Thread.currentThread();
    ThreadInfo info = bean.getThreadInfo(current.getId(), 0);
    StringWriter text = new StringWriter();
    XmlOutputAdapter out = new XmlOutputAdapter(text);
    ThreadDetails.startThread(out, info, current, false, 1024L);
    ThreadDetails.writeChildren(out, info, ThreadDetails.origin(info, current, current.getStackTrace()), ThreadDetails.cpu(bean));
    out.endObject();
    out.close();
    String xml = text.toString();
    assertTrue(xml.matches("(?s)<thread id=\"" + current.getId() + "\" name=\"[^\"]+\" priority=\"\\d+\" state=\"RUNNABLE\" alive=\"true\""
        + " daemon=\"(true|false)\" group=\"[^\"]+\" current=\"true\" blocked-count=\"\\d+\" waited-count=\"\\d+\" allocated-bytes=\"1024\">"
        + "\\s*<origin component=\"[^\"]+\" role=\"running\" basis=\"stack\" loader=\"[a-z]+\"/>"
        + "\\s*<activity class=\"org.pageseeder.bzadmin.system.ThreadDetailsTest\" method=\"writesTheCurrentThread\" file=\"ThreadDetailsTest.java\" line=\"\\d+\"/>"
        // HotSpot measures the CPU time of threads
        + "\\s*<times cpu=\"\\d+\" user=\"\\d+\" system=\"\\d+\"/>\\s*</thread>"), xml);
  }

  @Test
  void findsDeadlocksAndTheirLocks() throws Exception {
    ReentrantLock a = new ReentrantLock();
    ReentrantLock b = new ReentrantLock();
    CountDownLatch locked = new CountDownLatch(2);
    try (Deadlock deadlock = new Deadlock(deadlocking("fixture-deadlock-1", a, b, locked),
        deadlocking("fixture-deadlock-2", b, a, locked))) {
      Thread first = deadlock.first;
      Thread second = deadlock.second;
      assertTrue(locked.await(5, TimeUnit.SECONDS));
      // Each thread holds one lock and queues for the other
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!(b.hasQueuedThread(first) && a.hasQueuedThread(second)) && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      ThreadMXBean bean = ManagementFactory.getThreadMXBean();
      Set<Long> deadlocked = ThreadDetails.deadlocked(bean);
      assertEquals(Set.of(first.getId(), second.getId()), deadlocked);
      ThreadInfo info = bean.getThreadInfo(first.getId(), 0);
      StringWriter text = new StringWriter();
      XmlOutputAdapter out = new XmlOutputAdapter(text);
      ThreadDetails.startThread(out, info, first, true, null);
      ThreadDetails.writeChildren(out, info, ThreadDetails.origin(info, first, first.getStackTrace()), null);
      out.endObject();
      out.close();
      String xml = text.toString();
      assertTrue(xml.contains(" state=\"WAITING\""), xml);
      assertTrue(xml.contains(" deadlocked=\"true\""), xml);
      assertFalse(xml.contains("allocated-bytes"), xml);
      assertFalse(xml.contains("<times"), xml);
      assertTrue(xml.matches("(?s).*<lock name=\"java.util.concurrent.locks.ReentrantLock\\$NonfairSync@[0-9a-f]+\" owner-id=\""
          + second.getId() + "\" owner-name=\"fixture-deadlock-2\"/>.*"), xml);
      assertTrue(xml.contains("<origin component=\"Berlioz admin\" role=\"running\" basis=\"stack\""), xml);
    }
  }

  @Test
  void tellsTheClassLoaders() {
    ClassLoader application = new URLClassLoader(new URL[0], getClass().getClassLoader());
    ClassLoader child = new URLClassLoader(new URL[0], application);
    Thread thread = new Thread(() -> {});
    thread.setContextClassLoader(child);
    assertEquals(Loader.APPLICATION, ThreadDetails.loader(thread, application));
    thread.setContextClassLoader(application);
    assertEquals(Loader.APPLICATION, ThreadDetails.loader(thread, application));
    thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
    assertEquals(Loader.SYSTEM, ThreadDetails.loader(thread, application));
    thread.setContextClassLoader(new URLClassLoader(new URL[0], null));
    assertEquals(Loader.OTHER, ThreadDetails.loader(thread, application));
    thread.setContextClassLoader(null);
    assertEquals(Loader.NONE, ThreadDetails.loader(thread, application));
  }

  @Test
  void tellsTheThreadGroupsOfTheJvm() {
    ThreadGroup main = Thread.currentThread().getThreadGroup();
    while (!"main".equals(main.getName())) main = main.getParent();
    ThreadGroup system = main.getParent();
    assertTrue(ThreadDetails.isJvmGroup(system));
    assertTrue(ThreadDetails.isJvmGroup(new ThreadGroup(system, "jvm-child")));
    assertFalse(ThreadDetails.isJvmGroup(main));
    assertFalse(ThreadDetails.isJvmGroup(new ThreadGroup(main, "application")));
    assertFalse(ThreadDetails.isJvmGroup(null));
  }

  @Test
  void readsTheAllocatedMemoryWithoutFailing() {
    long[] ids = {Thread.currentThread().getId(), Long.MAX_VALUE};
    long[] bytes = ThreadDetails.allocatedBytes(ids);
    // HotSpot counts the memory each thread allocates; -1 for a thread that does not exist
    assertEquals(2, bytes.length);
    assertTrue(bytes[0] > 0);
    assertEquals(null, ThreadDetails.valueAt(bytes, 1));
    assertEquals(null, ThreadDetails.valueAt(null, 0));
  }

  private static Thread deadlocking(String name, ReentrantLock first, ReentrantLock second, CountDownLatch locked) {
    Thread thread = new Thread(() -> {
      try {
        first.lockInterruptibly();
        try {
          locked.countDown();
          locked.await(5, TimeUnit.SECONDS);
          second.lockInterruptibly();
          second.unlock();
        } finally {
          first.unlock();
        }
      } catch (InterruptedException ex) {
        // End of the test
      }
    }, name);
    thread.setDaemon(true);
    thread.start();
    return thread;
  }

  /**
   * The two deadlocked threads, interrupted and joined on close.
   */
  private static final class Deadlock implements AutoCloseable {

    private final Thread first;
    private final Thread second;

    Deadlock(Thread first, Thread second) {
      this.first = first;
      this.second = second;
    }

    @Override
    public void close() throws InterruptedException {
      this.first.interrupt();
      this.second.interrupt();
      this.first.join(1000);
      this.second.join(1000);
    }
  }

}
