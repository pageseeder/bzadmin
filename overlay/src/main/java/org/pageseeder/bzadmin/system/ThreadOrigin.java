package org.pageseeder.bzadmin.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A guess at the origin of a thread: the component that started it, what it is doing, and what
 * the guess is based on.
 *
 * <p>The component comes, in order, from:
 * <ol>
 *   <li>the JVM's own threads, by name or thread group;</li>
 *   <li>the stack: the first frame from the bottom that is not a JDK class is the code the thread
 *   runs (a Jetty pool runner, an EHCache task, a Gretty launcher…);</li>
 *   <li>the name, for threads whose stack is only JDK classes, such as the idle workers of an
 *   executor ({@code Statistics Thread-…}, {@code Session-HouseKeeper-…});</li>
 *   <li>the context class loader: a thread with the web application's loader was started by the
 *   application, or for it.</li>
 * </ol>
 *
 * <p>The role comes from the stack: accepting connections, selecting network events, serving a
 * request, idle in a pool, or running. Nothing at runtime records which code created a pool, so
 * an idle worker of an unnamed executor ({@code pool-3-thread-1}) is only known by its loader.
 */
final class ThreadOrigin {

  /** What the thread is doing. */
  enum Role {
    /** Waiting for connections on a server socket. */
    ACCEPTOR,
    /** Waiting for network events on a selector. */
    SELECTOR,
    /** Serving an HTTP request. */
    REQUEST,
    /** A pool or timer thread waiting for work. */
    IDLE,
    /** Anything else: running, or waiting for something other than work. */
    RUNNING,
    /** A thread of the JVM itself. */
    INTERNAL;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** What the component is based on. */
  enum Basis {
    STACK, NAME, GROUP, LOADER, NONE;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** The context class loader of the thread. */
  enum Loader {
    /** The web application's class loader, or one of its descendants. */
    APPLICATION,
    /** The system or platform class loader. */
    SYSTEM,
    /** Another class loader, such as the container's. */
    OTHER,
    /** No context class loader. */
    NONE,
    /** The class loader could not be read. */
    UNKNOWN;

    String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  static final String JVM = "JVM";

  static final String APPLICATION = "Application";

  static final String UNKNOWN = "Unknown";

  private static final String JETTY = "Jetty";

  private static final String TOMCAT = "Tomcat";

  /** Classes of the JDK: their frames say nothing about the component. */
  private static final List<String> JDK_PACKAGES = List.of("java.", "javax.", "jdk.", "sun.", "com.sun.");

  /** Components by package prefix, most specific first. */
  private static final Map<String, String> COMPONENTS = orderedMap(
      "org.eclipse.jetty.", JETTY,
      "org.apache.catalina.", TOMCAT,
      "org.apache.coyote.", TOMCAT,
      "org.apache.tomcat.", TOMCAT,
      "io.undertow.", "Undertow",
      "net.sf.ehcache.", "EHCache 2",
      "org.ehcache.", "EHCache 3",
      "com.github.benmanes.caffeine.", "Caffeine",
      "ch.qos.logback.", "Logback",
      "org.apache.logging.log4j.", "Log4j",
      "org.pageseeder.bzadmin.", "Berlioz admin",
      "org.pageseeder.berlioz.", "Berlioz",
      "org.pageseeder.flint.", "Flint",
      "org.apache.lucene.", "Lucene",
      "net.sf.saxon.", "Saxon",
      "com.zaxxer.hikari.", "HikariCP",
      "org.apache.derby.", "Derby",
      "org.akhikhl.gretty.", "Gretty",
      "org.gretty.", "Gretty");

  /** Threads that the JVM starts, by name. */
  private static final Set<String> JVM_THREADS = Set.of("Reference Handler", "Finalizer",
      "Signal Dispatcher", "Common-Cleaner", "Attach Listener", "Notification Thread", "Service Thread",
      "Monitor Deflation Thread", "DestroyJavaVM", "process reaper", "Sweeper thread",
      "Java2D Disposer", "JFR Recorder Thread", "JFR Periodic Tasks");

  /** Components by thread name, for the threads whose stack is only JDK classes. */
  private static final Map<Pattern, String> NAMES = orderedMap(
      Pattern.compile("ForkJoinPool\\.commonPool-worker-\\d+"), "Common pool",
      Pattern.compile("HttpClient-\\d+-.*"), "HTTP client",
      Pattern.compile("RMI .*|JMX server connection timeout.*"), "JMX",
      Pattern.compile("qtp\\d+.*|Connector-Scheduler-.*|Session-HouseKeeper-.*"), JETTY,
      Pattern.compile("Scheduler-\\d+.*|Scanner-\\d+|HashSessionScavenger-.*"), JETTY,
      Pattern.compile("(https?|ajp)-(nio2?|apr|bio)-.*"), TOMCAT,
      Pattern.compile("Catalina-utility-\\d+|ContainerBackgroundProcessor.*|NioBlockingSelector.*"), TOMCAT,
      // The statistics executor and timer of a cache manager (the default manager is __DEFAULT__),
      // and the disk store of a cache
      Pattern.compile("Statistics Thread-.*|__DEFAULT__|.+\\.data"), "EHCache 2",
      Pattern.compile("logback-\\d+|AsyncAppender-Worker-.*"), "Logback",
      Pattern.compile("indexing-p.*"), "Flint");

  /** Frames of a pool or timer thread waiting for work, as class and method. */
  private static final List<String[]> IDLE = List.of(
      new String[]{"java.util.concurrent.ThreadPoolExecutor", "getTask"},
      new String[]{"org.apache.tomcat.util.threads.ThreadPoolExecutor", "getTask"},
      new String[]{"java.util.concurrent.ForkJoinPool", "awaitWork"},
      new String[]{"java.util.TimerThread", "mainLoop"},
      new String[]{"org.eclipse.jetty.util.thread.QueuedThreadPool", "idleJobPoll"},
      new String[]{"org.eclipse.jetty.util.thread.ReservedThreadExecutor", "reservedWait"});

  /** Classes, by prefix, on the stack of a thread serving an HTTP request. */
  private static final List<String> REQUEST = List.of("javax.servlet.", "jakarta.servlet.",
      "org.eclipse.jetty.server.HttpChannel", "org.eclipse.jetty.server.internal.HttpChannelState",
      "org.apache.catalina.connector.CoyoteAdapter", "io.undertow.servlet.handlers.ServletInitialHandler");

  final String component;

  final Role role;

  final Basis basis;

  final Loader loader;

  /** The frame that shows what the thread is doing, or {@code null} without a stack. */
  final @Nullable StackTraceElement activity;

  private ThreadOrigin(String component, Role role, Basis basis, Loader loader, @Nullable StackTraceElement activity) {
    this.component = component;
    this.role = role;
    this.basis = basis;
    this.loader = loader;
    this.activity = activity;
  }

  /**
   * @param name     The name of the thread.
   * @param jvmGroup Whether the thread is in a thread group of the JVM ({@code system} and its
   *                 groups other than {@code main}).
   * @param loader   Its context class loader.
   * @param stack    Its stack, the top first (possibly empty).
   * @return the guess.
   */
  static ThreadOrigin guess(String name, boolean jvmGroup, Loader loader, StackTraceElement[] stack) {
    StackTraceElement activity = activityOf(stack);
    if (JVM_THREADS.contains(name)) return new ThreadOrigin(JVM, Role.INTERNAL, Basis.NAME, loader, activity);
    if (jvmGroup) return new ThreadOrigin(JVM, Role.INTERNAL, Basis.GROUP, loader, activity);
    Role role = roleOf(stack);
    String entry = entryPoint(stack);
    String known = entry != null ? componentOf(entry) : null;
    if (known != null) return new ThreadOrigin(known, role, Basis.STACK, loader, activity);
    for (Map.Entry<Pattern, String> pattern : NAMES.entrySet()) {
      if (pattern.getKey().matcher(name).matches()) return new ThreadOrigin(pattern.getValue(), role, Basis.NAME, loader, activity);
    }
    if (entry != null) return new ThreadOrigin(packageOf(entry), role, Basis.STACK, loader, activity);
    if (loader == Loader.APPLICATION) return new ThreadOrigin(APPLICATION, role, Basis.LOADER, loader, activity);
    return new ThreadOrigin(UNKNOWN, role, Basis.NONE, loader, activity);
  }

  /**
   * @return the first class from the bottom of the stack that is not a JDK class, or {@code null}.
   */
  static @Nullable String entryPoint(StackTraceElement[] stack) {
    for (int i = stack.length - 1; i >= 0; i--) {
      if (!isJdk(stack[i].getClassName())) return stack[i].getClassName();
    }
    return null;
  }

  /**
   * @return the top frame that is not a JDK class, else the top frame, or {@code null} without a stack.
   */
  static @Nullable StackTraceElement activityOf(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (!isJdk(frame.getClassName())) return frame;
    }
    return stack.length > 0 ? stack[0] : null;
  }

  static Role roleOf(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (startsWithAny(frame.getClassName(), REQUEST)) return Role.REQUEST;
    }
    Role blocked = blockedIn(stack);
    if (blocked != null) return blocked;
    return isIdle(stack) ? Role.IDLE : Role.RUNNING;
  }

  /**
   * @return what the JDK frames at the top show the thread is blocked in (selector or acceptor), or {@code null}.
   */
  private static @Nullable Role blockedIn(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      String type = frame.getClassName();
      if (!isJdk(type)) break;
      if (type.endsWith("SelectorImpl")) return Role.SELECTOR;
      if (isAccept(type, frame.getMethodName())) return Role.ACCEPTOR;
    }
    return null;
  }

  /**
   * @return whether the thread waits for work: only JDK or queue classes above the pool's loop.
   */
  private static boolean isIdle(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (isIdleLoop(frame)) return true;
      String type = frame.getClassName();
      if (!isJdk(type) && !type.contains("Queue")) break;
    }
    return false;
  }

  /**
   * @return the component of a class, or {@code null} if it is not a known one.
   */
  static @Nullable String componentOf(String type) {
    for (Map.Entry<String, String> prefix : COMPONENTS.entrySet()) {
      if (type.startsWith(prefix.getKey())) return prefix.getValue();
    }
    return null;
  }

  static boolean isJdk(String type) {
    return startsWithAny(type, JDK_PACKAGES);
  }

  /**
   * @return the first three segments of the package of a class (for example {@code com.example.app}).
   */
  static String packageOf(String type) {
    String[] segments = type.split("\\.");
    // The last segment is the class
    int length = Math.min(3, segments.length - 1);
    if (length <= 0) return type;
    return String.join(".", List.of(segments).subList(0, length));
  }

  private static boolean isAccept(String type, String method) {
    return method.startsWith("accept") && (type.equals("java.net.ServerSocket") || type.endsWith("ServerSocketChannelImpl"))
        || method.equals("implAccept") || type.endsWith("SocketImpl") && method.contains("ccept");
  }

  private static boolean isIdleLoop(StackTraceElement frame) {
    for (String[] idle : IDLE) {
      if (frame.getClassName().startsWith(idle[0]) && frame.getMethodName().equals(idle[1])) return true;
    }
    return false;
  }

  private static boolean startsWithAny(String type, List<String> prefixes) {
    for (String prefix : prefixes) {
      if (type.startsWith(prefix)) return true;
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static <K> Map<K, String> orderedMap(Object... pairs) {
    Map<K, String> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) map.put((K) pairs[i], (String) pairs[i + 1]);
    return map;
  }

}
