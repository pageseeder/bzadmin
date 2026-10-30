package org.pageseeder.bzadmin.log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Limits the number of log files read at the same time: each read is bounded, and this bounds the
 * reads together (memory and I/O), whatever the number of requests.
 */
final class LogReads {

  /** The maximum number of reads at the same time. */
  static final int MAX_CONCURRENT = 4;

  /** How long a read waits for another one to finish before it is refused. */
  static final long WAIT_MILLIS = 2000;

  private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT, true);

  private LogReads() {
  }

  /**
   * @return whether the read can start; if so, it must {@link #release()} when it is done.
   */
  static boolean acquire() {
    try {
      return PERMITS.tryAcquire(WAIT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  static void release() {
    PERMITS.release();
  }

}
