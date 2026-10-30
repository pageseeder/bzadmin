package org.pageseeder.bzadmin.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.pageseeder.bzadmin.system.ThreadOrigin.Basis;
import org.pageseeder.bzadmin.system.ThreadOrigin.Loader;
import org.pageseeder.bzadmin.system.ThreadOrigin.Role;

/**
 * The origin of threads, from stacks captured on Jetty 10 under Gretty (Java 11), and on Tomcat.
 */
final class ThreadOriginTest {

  private static final String[] JETTY_POOL = {
      "org.eclipse.jetty.util.thread.QueuedThreadPool.runJob",
      "org.eclipse.jetty.util.thread.QueuedThreadPool$Runner.doRunJob",
      "org.eclipse.jetty.util.thread.QueuedThreadPool$Runner.run",
      "java.lang.Thread.run"};

  private static final String[] EXECUTOR_IDLE = {
      "jdk.internal.misc.Unsafe.park",
      "java.util.concurrent.locks.LockSupport.parkNanos",
      "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos",
      "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take",
      "java.util.concurrent.ThreadPoolExecutor.getTask",
      "java.util.concurrent.ThreadPoolExecutor.runWorker",
      "java.util.concurrent.ThreadPoolExecutor$Worker.run",
      "java.lang.Thread.run"};

  @Test
  void findsTheJettySelectors() {
    ThreadOrigin origin = guess("qtp1409442094-49", Loader.OTHER, concat(new String[]{
        "sun.nio.ch.KQueue.poll",
        "sun.nio.ch.KQueueSelectorImpl.doSelect",
        "sun.nio.ch.SelectorImpl.lockAndDoSelect",
        "sun.nio.ch.SelectorImpl.select",
        "org.eclipse.jetty.io.ManagedSelector.nioSelect",
        "org.eclipse.jetty.io.ManagedSelector.select"}, JETTY_POOL));
    assertOrigin(origin, "Jetty", Role.SELECTOR, Basis.STACK);
    assertEquals("org.eclipse.jetty.io.ManagedSelector.nioSelect", text(origin.activity));
  }

  @Test
  void findsTheJettyAcceptors() {
    ThreadOrigin origin = guess("qtp1409442094-56-acceptor-0@6ee96deb-ServerConnector@43445fc6{HTTP/1.1, (http/1.1)}{0.0.0.0:8999}",
        Loader.OTHER, concat(new String[]{
        "sun.nio.ch.ServerSocketChannelImpl.accept0",
        "sun.nio.ch.ServerSocketChannelImpl.accept",
        "org.eclipse.jetty.server.ServerConnector.accept",
        "org.eclipse.jetty.server.AbstractConnector$Acceptor.run"}, JETTY_POOL));
    assertOrigin(origin, "Jetty", Role.ACCEPTOR, Basis.STACK);
  }

  @Test
  void findsTheIdleJettyThreads() {
    ThreadOrigin polling = guess("qtp1409442094-51", Loader.OTHER, frames(
        "jdk.internal.misc.Unsafe.park",
        "java.util.concurrent.locks.LockSupport.parkNanos",
        "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos",
        "org.eclipse.jetty.util.BlockingArrayQueue.poll",
        "org.eclipse.jetty.util.thread.QueuedThreadPool$Runner.idleJobPoll",
        "org.eclipse.jetty.util.thread.QueuedThreadPool$Runner.run",
        "java.lang.Thread.run"));
    assertOrigin(polling, "Jetty", Role.IDLE, Basis.STACK);
    ThreadOrigin reserved = guess("qtp1409442094-50", Loader.OTHER, concat(new String[]{
        "jdk.internal.misc.Unsafe.park",
        "java.util.concurrent.locks.LockSupport.parkNanos",
        "java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill",
        "java.util.concurrent.SynchronousQueue$TransferStack.transfer",
        "java.util.concurrent.SynchronousQueue.poll",
        "org.eclipse.jetty.util.thread.ReservedThreadExecutor$ReservedThread.reservedWait",
        "org.eclipse.jetty.util.thread.ReservedThreadExecutor$ReservedThread.run"}, JETTY_POOL));
    assertOrigin(reserved, "Jetty", Role.IDLE, Basis.STACK);
  }

  @Test
  void findsTheRequestsAndWhatTheyDo() {
    ThreadOrigin origin = guess("qtp1409442094-70", Loader.APPLICATION, concat(new String[]{
        "java.net.SocketInputStream.socketRead0",
        "org.postgresql.core.PGStream.receiveChar",
        "org.example.app.Repository.load",
        "org.pageseeder.berlioz.servlet.BerliozServlet.doGet",
        "javax.servlet.http.HttpServlet.service",
        "org.eclipse.jetty.servlet.ServletHolder.handle",
        "org.eclipse.jetty.server.HttpChannel.handle"}, JETTY_POOL));
    assertOrigin(origin, "Jetty", Role.REQUEST, Basis.STACK);
    assertEquals("org.postgresql.core.PGStream.receiveChar", text(origin.activity));
    // Through a filter only, with no servlet frame
    ThreadOrigin filter = guess("qtp1409442094-71", Loader.APPLICATION, concat(new String[]{
        "java.io.FileInputStream.readBytes",
        "org.example.app.StaticFilter.doFilter",
        "org.eclipse.jetty.servlet.FilterHolder.doFilter",
        "org.eclipse.jetty.server.HttpChannel.handle"}, JETTY_POOL));
    assertEquals(Role.REQUEST, filter.role);
  }

  @Test
  void findsTheTomcatThreads() {
    ThreadOrigin idle = guess("http-nio-8080-exec-1", Loader.OTHER, frames(
        "jdk.internal.misc.Unsafe.park",
        "java.util.concurrent.locks.LockSupport.park",
        "java.util.concurrent.LinkedBlockingQueue.take",
        "org.apache.tomcat.util.threads.TaskQueue.take",
        "org.apache.tomcat.util.threads.ThreadPoolExecutor.getTask",
        "org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker",
        "org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run",
        "org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run",
        "java.lang.Thread.run"));
    assertOrigin(idle, "Tomcat", Role.IDLE, Basis.STACK);
    ThreadOrigin poller = guess("http-nio-8080-Poller", Loader.OTHER, frames(
        "sun.nio.ch.EPoll.wait",
        "sun.nio.ch.EPollSelectorImpl.doSelect",
        "sun.nio.ch.SelectorImpl.lockAndDoSelect",
        "sun.nio.ch.SelectorImpl.select",
        "org.apache.tomcat.util.net.NioEndpoint$Poller.run",
        "java.lang.Thread.run"));
    assertOrigin(poller, "Tomcat", Role.SELECTOR, Basis.STACK);
  }

  @Test
  void namesTheIdleWorkersOfExecutors() {
    assertOrigin(guess("Statistics Thread-__DEFAULT__-1", Loader.OTHER, frames(EXECUTOR_IDLE)), "EHCache 2", Role.IDLE, Basis.NAME);
    assertOrigin(guess("static-web-caching.data", Loader.APPLICATION, frames(EXECUTOR_IDLE)), "EHCache 2", Role.IDLE, Basis.NAME);
    assertOrigin(guess("Session-HouseKeeper-45c408a4-1", Loader.OTHER, frames(EXECUTOR_IDLE)), "Jetty", Role.IDLE, Basis.NAME);
    assertOrigin(guess("Connector-Scheduler-17befef0-1", Loader.OTHER, frames(EXECUTOR_IDLE)), "Jetty", Role.IDLE, Basis.NAME);
    assertOrigin(guess("__DEFAULT__", Loader.APPLICATION, frames(
        "java.lang.Object.wait", "java.lang.Object.wait", "java.util.TimerThread.mainLoop", "java.util.TimerThread.run")),
        "EHCache 2", Role.IDLE, Basis.NAME);
    // Unnamed executors: the application, from the class loader, or nothing
    assertOrigin(guess("pool-3-thread-1", Loader.APPLICATION, frames(EXECUTOR_IDLE)), ThreadOrigin.APPLICATION, Role.IDLE, Basis.LOADER);
    assertOrigin(guess("pool-3-thread-1", Loader.SYSTEM, frames(EXECUTOR_IDLE)), ThreadOrigin.UNKNOWN, Role.IDLE, Basis.NONE);
  }

  @Test
  void prefersTheTaskAnExecutorRuns() {
    ThreadOrigin origin = guess("pool-3-thread-1", Loader.APPLICATION, frames(
        "java.lang.Thread.sleep",
        "net.sf.ehcache.statistics.extended.ExtendedStatisticsImpl$1.run",
        "java.util.concurrent.ThreadPoolExecutor.runWorker",
        "java.util.concurrent.ThreadPoolExecutor$Worker.run",
        "java.lang.Thread.run"));
    assertOrigin(origin, "EHCache 2", Role.RUNNING, Basis.STACK);
  }

  @Test
  void findsTheThreadsOfTheJvm() {
    assertOrigin(guess("Reference Handler", Loader.NONE, frames(
        "java.lang.ref.Reference.waitForReferencePendingList", "java.lang.ref.Reference$ReferenceHandler.run")),
        ThreadOrigin.JVM, Role.INTERNAL, Basis.NAME);
    // No stack
    assertOrigin(guess("Signal Dispatcher", Loader.NONE, frames()), ThreadOrigin.JVM, Role.INTERNAL, Basis.NAME);
    assertNull(guess("Signal Dispatcher", Loader.NONE, frames()).activity);
    assertOrigin(ThreadOrigin.guess("Some JDK thread", true, Loader.NONE, frames("java.lang.Object.wait")),
        ThreadOrigin.JVM, Role.INTERNAL, Basis.GROUP);
  }

  @Test
  void namesUnknownComponentsByPackage() {
    ThreadOrigin gretty = guess("main", Loader.SYSTEM, frames(
        "java.net.PlainSocketImpl.socketAccept",
        "java.net.AbstractPlainSocketImpl.accept",
        "java.net.ServerSocket.implAccept",
        "java.net.ServerSocket.accept",
        "org.akhikhl.gretty.ServiceProtocol$Reader.readMessage",
        "org.akhikhl.gretty.Runner.run",
        "jdk.internal.reflect.NativeMethodAccessorImpl.invoke0",
        "org.codehaus.groovy.runtime.callsite.AbstractCallSite.call",
        "org.akhikhl.gretty.Runner.main"));
    assertOrigin(gretty, "Gretty", Role.ACCEPTOR, Basis.STACK);
    ThreadOrigin custom = guess("worker", Loader.APPLICATION, frames(
        "java.lang.Thread.sleep", "com.example.app.jobs.Daemon.run", "java.lang.Thread.run"));
    assertOrigin(custom, "com.example.app", Role.RUNNING, Basis.STACK);
    assertEquals("Daemon", ThreadOrigin.packageOf("Daemon"));
    assertEquals("com.example", ThreadOrigin.packageOf("com.example.Daemon"));
  }

  @Test
  void showsTheTopJdkFrameWithoutOtherFrames() {
    ThreadOrigin origin = guess("pool-1-thread-1", Loader.SYSTEM, frames(EXECUTOR_IDLE));
    assertEquals("jdk.internal.misc.Unsafe.park", text(origin.activity));
  }

  private static ThreadOrigin guess(String name, Loader loader, StackTraceElement[] stack) {
    return ThreadOrigin.guess(name, false, loader, stack);
  }

  private static void assertOrigin(ThreadOrigin origin, String component, Role role, Basis basis) {
    assertEquals(component, origin.component, "component");
    assertEquals(role, origin.role, "role");
    assertEquals(basis, origin.basis, "basis");
  }

  private static String text(StackTraceElement frame) {
    return frame.getClassName() + "." + frame.getMethodName();
  }

  private static StackTraceElement[] concat(String[] top, String[] bottom) {
    String[] all = new String[top.length + bottom.length];
    System.arraycopy(top, 0, all, 0, top.length);
    System.arraycopy(bottom, 0, all, top.length, bottom.length);
    return frames(all);
  }

  /** Frames from 'class.method', the top first. */
  static StackTraceElement[] frames(String... frames) {
    StackTraceElement[] stack = new StackTraceElement[frames.length];
    for (int i = 0; i < frames.length; i++) {
      int dot = frames[i].lastIndexOf('.');
      stack[i] = new StackTraceElement(frames[i].substring(0, dot), frames[i].substring(dot + 1), null, -1);
    }
    return stack;
  }

}
