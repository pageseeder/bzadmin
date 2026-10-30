package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The most recent log events at or above a capture threshold, in a ring of fixed capacity: when it
 * is full, a new event replaces the oldest.
 *
 * <p>The capture threshold only selects among the events that the application logs: the levels of
 * the application's loggers decide what is logged, and a threshold below them captures nothing more.
 *
 * <p>Nonblocking: adding an event takes a sequence number and sets one slot, without a lock, so a
 * logging thread never waits for another one or for a reader. A reader copies the slots that still
 * hold the sequence numbers it expects; an event replaced, or not yet set, while it reads is left out.
 */
final class RecentEvents {

  /** The number of events held by the logging backends. */
  static final int DEFAULT_CAPACITY = 256;

  /** The capture threshold when capture starts. */
  static final EventLevel DEFAULT_THRESHOLD = EventLevel.INFO;

  private final AtomicReferenceArray<@Nullable Slot> slots;

  /** The sequence number of the next event: the number of events captured so far. */
  private final AtomicLong next = new AtomicLong();

  private volatile EventLevel threshold;

  /**
   * @param capacity  The number of events held.
   * @param threshold The capture threshold.
   */
  RecentEvents(int capacity, EventLevel threshold) {
    this.slots = new AtomicReferenceArray<>(checkCapacity(capacity));
    this.threshold = threshold;
  }

  /**
   * @param capacity The number of events held.
   * @return the capacity, if positive.
   */
  private static int checkCapacity(int capacity) {
    if (capacity < 1) throw new IllegalArgumentException("capacity");
    return capacity;
  }

  /** @return the number of events held. */
  int capacity() {
    return this.slots.length();
  }

  /** @return the capture threshold. */
  EventLevel threshold() {
    return this.threshold;
  }

  /**
   * @param threshold The capture threshold, {@link EventLevel#OFF} to capture nothing.
   */
  void setThreshold(EventLevel threshold) {
    this.threshold = threshold;
  }

  /**
   * To check before making an event.
   *
   * @param level The level of an event.
   * @return whether an event of this level is captured.
   */
  boolean accepts(EventLevel level) {
    return level.passes(this.threshold);
  }

  /**
   * Adds an event, replacing the oldest when the ring is full.
   *
   * @param event An event that {@linkplain #accepts(EventLevel) passed the threshold}.
   */
  void add(RecentEvent event) {
    long sequence = this.next.getAndIncrement();
    this.slots.set((int) (sequence % this.slots.length()), new Slot(sequence, event));
  }

  /**
   * @return the events held, oldest first, and the number captured so far.
   */
  Snapshot snapshot() {
    long end = this.next.get();
    int capacity = this.slots.length();
    List<RecentEvent> events = new ArrayList<>(capacity);
    for (long sequence = Math.max(0, end - capacity); sequence < end; sequence++) {
      @Nullable Slot slot = this.slots.get((int) (sequence % capacity));
      if (slot != null && slot.sequence == sequence) events.add(slot.event);
    }
    return new Snapshot(events, end);
  }

  /**
   * The events held when read.
   */
  static final class Snapshot {

    private final List<RecentEvent> events;

    private final long captured;

    Snapshot(List<RecentEvent> events, long captured) {
      this.events = List.copyOf(events);
      this.captured = captured;
    }

    /** @return the events, oldest first. */
    List<RecentEvent> events() {
      return this.events;
    }

    /** @return the number of events captured since capture started, including those replaced. */
    long captured() {
      return this.captured;
    }
  }

  private static final class Slot {

    private final long sequence;

    private final RecentEvent event;

    Slot(long sequence, RecentEvent event) {
      this.sequence = sequence;
      this.event = event;
    }
  }

}
