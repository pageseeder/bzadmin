package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class LoginThrottleTest {

  private final AtomicLong now = new AtomicLong(Long.MAX_VALUE - TimeUnit.MINUTES.toNanos(1));

  private final LoginThrottle throttle = new LoginThrottle(this.now::get, 1);

  @Test
  void allowsABurstThenOneCheckPerInterval() {
    for (int i = 0; i < LoginThrottle.BURST; i++) {
      assertEquals(0, this.throttle.acquire("a"), "check " + i);
    }
    assertEquals(LoginThrottle.INTERVAL_SECONDS, this.throttle.acquire("a"));
    advance(TimeUnit.SECONDS.toNanos(1));
    assertEquals(LoginThrottle.INTERVAL_SECONDS - 1, this.throttle.acquire("a"));
    advance(TimeUnit.SECONDS.toNanos(LoginThrottle.INTERVAL_SECONDS - 1));
    assertEquals(0, this.throttle.acquire("a"));
    assertEquals(LoginThrottle.INTERVAL_SECONDS, this.throttle.acquire("a"));
    // Refused checks are not counted
    advance(TimeUnit.SECONDS.toNanos(LoginThrottle.INTERVAL_SECONDS));
    assertEquals(0, this.throttle.acquire("a"));
    // Fully restored, across the overflow of the clock
    advance(TimeUnit.SECONDS.toNanos((long) LoginThrottle.BURST * LoginThrottle.INTERVAL_SECONDS));
    for (int i = 0; i < LoginThrottle.BURST; i++) {
      assertEquals(0, this.throttle.acquire("a"), "check " + i);
    }
  }

  @Test
  void limitsEachClientSeparately() {
    for (int i = 0; i < LoginThrottle.BURST; i++) this.throttle.acquire("a");
    assertTrue(this.throttle.acquire("a") > 0);
    assertEquals(0, this.throttle.acquire("b"));
    this.throttle.reset("a");
    assertEquals(0, this.throttle.acquire("a"));
  }

  @Test
  void sharesOneLimitBeyondTheClientsTracked() {
    for (int i = 0; i < LoginThrottle.MAX_CLIENTS; i++) assertEquals(0, this.throttle.acquire("client-" + i));
    for (int i = 0; i < LoginThrottle.BURST; i++) {
      assertEquals(0, this.throttle.acquire("other-" + i), "other " + i);
    }
    assertTrue(this.throttle.acquire("other-new") > 0);
    // Tracked clients keep their own limit
    assertEquals(0, this.throttle.acquire("client-0"));
    // Restored clients are removed to make room
    advance(TimeUnit.SECONDS.toNanos((long) LoginThrottle.BURST * LoginThrottle.INTERVAL_SECONDS));
    for (int i = 0; i < LoginThrottle.BURST; i++) {
      assertEquals(0, this.throttle.acquire("other-new"), "check " + i);
    }
  }

  /**
   * New clients arriving at once, with room for one only: one is tracked, the others share one
   * limit.
   */
  @Test
  void admitsNewClientsWithinTheBoundConcurrently() throws Exception {
    for (int i = 0; i < LoginThrottle.MAX_CLIENTS - 1; i++) assertEquals(0, this.throttle.acquire("client-" + i));
    int threads = 64;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      CyclicBarrier start = new CyclicBarrier(threads);
      List<Future<Integer>> waits = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        String client = "new-" + i;
        waits.add(executor.submit(() -> {
          start.await();
          return this.throttle.acquire(client);
        }));
      }
      int allowed = 0;
      for (Future<Integer> wait : waits) {
        if (wait.get(10, TimeUnit.SECONDS) == 0) allowed++;
      }
      assertEquals(LoginThrottle.MAX_CLIENTS, this.throttle.tracked() - 1, "the clients, and the shared limit");
      assertEquals(1 + LoginThrottle.BURST, allowed);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void limitsConcurrentChecks() {
    assertTrue(this.throttle.begin());
    long start = System.nanoTime();
    assertFalse(this.throttle.begin());
    assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(LoginThrottle.WAIT_MILLIS) / 2);
    this.throttle.end();
    assertTrue(this.throttle.begin());
    this.throttle.end();
  }

  @Test
  void reportsOnceAMinute() {
    assertTrue(this.throttle.report());
    assertFalse(this.throttle.report());
    advance(TimeUnit.SECONDS.toNanos(59));
    assertFalse(this.throttle.report());
    advance(TimeUnit.SECONDS.toNanos(1));
    assertTrue(this.throttle.report());
  }

  @Test
  void identifiesClientsByAddressOrIpv6Network() {
    assertEquals("203.0.113.5", LoginThrottle.client("203.0.113.5"));
    assertEquals("2001:db8:0:1::/64", LoginThrottle.client("2001:db8:0:1:aaaa::1"));
    assertEquals("2001:db8:0:1::/64", LoginThrottle.client("[2001:db8:0:1:bbbb::2]"));
    assertEquals("2001:db8:0:2::/64", LoginThrottle.client("2001:db8:0:2::1"));
    assertEquals("0:0:0:0::/64", LoginThrottle.client("::1"));
    assertEquals("192.0.2.1", LoginThrottle.client("::ffff:192.0.2.1"));
    assertEquals("not-an-address", LoginThrottle.client("not-an-address"));
    assertEquals("", LoginThrottle.client(null));
  }

  private void advance(long nanos) {
    this.now.addAndGet(nanos);
  }

}
