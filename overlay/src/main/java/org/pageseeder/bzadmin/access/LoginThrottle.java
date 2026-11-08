package org.pageseeder.bzadmin.access;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

/**
 * Limits the password checks of the fallback login ({@link FallbackLogin}), each a PBKDF2
 * computation with a password hash:
 *
 * <ul>
 *   <li>by client: a burst of {@value #BURST} checks, then one every {@value #INTERVAL_SECONDS}
 *     seconds, until a check succeeds; the client is the address of the connection (behind a
 *     proxy, the container must apply the forwarded headers), or its {@code /64} network for IPv6,
 *     since a single host usually has a whole one. At most {@value #MAX_CLIENTS} clients are
 *     tracked, and further ones share one limit;</li>
 *   <li>overall: at most {@link #CONCURRENT} checks at a time, so that clients from many addresses
 *     cannot take every processor; a check waits up to {@value #WAIT_MILLIS} ms for its turn.</li>
 * </ul>
 *
 * <p>Clients are tracked with the generic cell rate algorithm: the time when a client's limit is
 * fully restored, which needs no timer and makes a restored client removable.
 */
final class LoginThrottle {

  /** The checks a client can make at once. */
  static final int BURST = 10;

  /** The seconds after which a client can make another check. */
  static final int INTERVAL_SECONDS = 6;

  /** The most clients tracked. */
  static final int MAX_CLIENTS = 1024;

  /** The checks at a time, half of the processors: hashes are computed in the request thread. */
  static final int CONCURRENT = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

  /** The longest wait for a check to start. */
  static final long WAIT_MILLIS = 1_000;

  /** The interval at which a throttled login is reported. */
  private static final long REPORT_INTERVAL = TimeUnit.MINUTES.toNanos(1);

  private static final long INTERVAL = TimeUnit.SECONDS.toNanos(INTERVAL_SECONDS);

  /** How far ahead of now the restoration time of a client can be for another check. */
  private static final long TOLERANCE = (BURST - 1) * INTERVAL;

  /** The client of the clients beyond {@link #MAX_CLIENTS}, not an address. */
  private static final String OTHERS = "*";

  /**
   * The time at which the limit of each client is fully restored, in {@link #clock} time; guarded
   * by itself, so that admitting a new client and its bound are one step.
   */
  private final Map<String, Long> clients = new HashMap<>();

  private final Semaphore checks;

  private final LongSupplier clock;

  /** The time of the last report, or of the start. */
  private final AtomicLong reported;

  LoginThrottle() {
    this(System::nanoTime, CONCURRENT);
  }

  /**
   * @param clock      Time in nanoseconds, as {@link System#nanoTime()}.
   * @param concurrent The checks at a time.
   */
  LoginThrottle(LongSupplier clock, int concurrent) {
    this.clock = clock;
    this.checks = new Semaphore(concurrent);
    this.reported = new AtomicLong(clock.getAsLong() - REPORT_INTERVAL);
  }

  /**
   * Counts a check by the client.
   *
   * @param client The client ({@link #client(String)}).
   * @return {@code 0} when the client can make the check, or else the seconds until it can.
   */
  int acquire(String client) {
    long wait;
    synchronized (this.clients) {
      long now = this.clock.getAsLong();
      String key = client;
      if (this.clients.size() >= MAX_CLIENTS && !this.clients.containsKey(key)) {
        prune(now);
        if (this.clients.size() >= MAX_CLIENTS) key = OTHERS;
      }
      Long restored = this.clients.get(key);
      long start = restored == null || restored - now < 0 ? now : restored;
      wait = start - now - TOLERANCE;
      if (wait <= 0) this.clients.put(key, start + INTERVAL);
    }
    return wait <= 0 ? 0 : (int) Math.max(1, (wait + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
  }

  /**
   * Forgets the checks of the client, after a successful one.
   */
  void reset(String client) {
    synchronized (this.clients) {
      this.clients.remove(client);
    }
  }

  /**
   * Waits for a check to start: release it with {@link #end()}.
   *
   * @return whether the check can start.
   */
  boolean begin() {
    try {
      return this.checks.tryAcquire(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  void end() {
    this.checks.release();
  }

  /**
   * @return whether to report a throttled login: once a minute at most, so that throttled
   *   requests, which cost nothing else, cannot fill the log.
   */
  boolean report() {
    long now = this.clock.getAsLong();
    long last = this.reported.get();
    return now - last >= REPORT_INTERVAL && this.reported.compareAndSet(last, now);
  }

  /**
   * @return the clients tracked, including the shared limit of further clients.
   */
  int tracked() {
    synchronized (this.clients) {
      return this.clients.size();
    }
  }

  /** Removes the clients whose limit is restored; holding the lock of {@link #clients}. */
  private void prune(long now) {
    this.clients.values().removeIf(restored -> restored - now <= 0);
  }

  /**
   * @param address The address of the client ({@code getRemoteAddr()}).
   * @return the client: the IPv4 address, or the {@code /64} network of an IPv6 address.
   */
  static String client(@Nullable String address) {
    if (address == null) return "";
    String literal = address.startsWith("[") && address.endsWith("]") ? address.substring(1, address.length() - 1) : address;
    // An IPv6 literal (with a colon) is parsed, not looked up
    if (literal.indexOf(':') < 0 || !literal.matches("[0-9A-Fa-f:.]+(%[0-9A-Za-z]+)?")) return literal;
    try {
      InetAddress parsed = InetAddress.getByName(literal);
      byte[] bytes = parsed.getAddress();
      // An IPv4-mapped address
      if (bytes.length != 16) return parsed.getHostAddress();
      StringBuilder network = new StringBuilder(20);
      for (int i = 0; i < 8; i += 2) {
        network.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
      }
      return network.append(":/64").toString();
    } catch (UnknownHostException ex) {
      return literal;
    }
  }

}
