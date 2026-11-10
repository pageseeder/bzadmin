package org.pageseeder.bzadmin.log;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The most recent log events at or above a capture threshold, in a ring: when it is full, a new
 * event replaces the oldest.
 *
 * <p>The capture threshold only selects among the events that the application logs: the levels of
 * the application's loggers decide what is logged, and a threshold below them captures nothing more.
 *
 * <p>Nonblocking: adding an event takes a sequence number and sets one slot, without a lock, so a
 * logging thread never waits for another one or for a reader. A slot only takes an event more
 * recent than the one it holds. A reader copies the slots that still hold the sequence numbers it
 * expects; an event replaced, or not yet set, while it reads is left out.
 *
 * <p>The capacity can {@linkplain #resize(int) change} while events are captured: the most recent
 * events that fit are kept, and the number captured continues.
 */
final class RecentEvents {

  /** The number of events held by default. */
  static final int DEFAULT_CAPACITY = 256;

  /** The largest number of events held. */
  static final int MAX_CAPACITY = 4096;

  /** The capture threshold when capture starts. */
  static final EventLevel DEFAULT_THRESHOLD = EventLevel.INFO;

  /** The slots; replaced on resize. */
  private final AtomicReference<Ring> ring;

  /** The sequence number of the next event: the number of events captured so far. */
  private final AtomicLong next = new AtomicLong();

  private volatile EventLevel threshold;

  /**
   * @param capacity  The number of events held, from 1 to {@value #MAX_CAPACITY}.
   * @param threshold The capture threshold.
   */
  RecentEvents(int capacity, EventLevel threshold) {
    this.ring = new AtomicReference<>(new Ring(checkCapacity(capacity)));
    this.threshold = threshold;
  }

  /**
   * @param capacity The number of events held.
   * @return the capacity, if from 1 to {@value #MAX_CAPACITY}.
   */
  private static int checkCapacity(int capacity) {
    if (capacity < 1 || capacity > MAX_CAPACITY) throw new IllegalArgumentException("capacity");
    return capacity;
  }

  /** @return the number of events held. */
  int capacity() {
    return this.ring.get().length();
  }

  /**
   * Changes the number of events held, keeping the most recent events that fit. Only the threads
   * that resize wait for one another; adding an event does not wait.
   *
   * <p>While the events are copied into the new ring, a reader may miss some of them.
   *
   * @param capacity The number of events held, from 1 to {@value #MAX_CAPACITY}.
   */
  synchronized void resize(int capacity) {
    checkCapacity(capacity);
    Ring previous = this.ring.get();
    if (previous.length() == capacity) return;
    Ring resized = new Ring(capacity);
    // Published before the copy: an event added to the previous ring after its slot was copied is
    // set again in this one by its thread (see add)
    this.ring.set(resized);
    long end = this.next.get();
    for (long sequence = Math.max(0, end - capacity); sequence < end; sequence++) {
      @Nullable Slot slot = previous.get(sequence);
      if (slot != null) resized.put(slot);
    }
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
    Slot slot = new Slot(this.next.getAndIncrement(), event);
    Ring target = this.ring.get();
    target.put(slot);
    // When resized meanwhile, the copy may have missed it
    for (Ring current = this.ring.get(); current != target; current = this.ring.get()) {
      target = current;
      target.put(slot);
    }
  }

  /**
   * @return the events held, oldest first, and the number captured so far.
   */
  Snapshot snapshot() {
    Ring current = this.ring.get();
    long end = this.next.get();
    int capacity = current.length();
    List<RecentEvent> events = new ArrayList<>(capacity);
    for (long sequence = Math.max(0, end - capacity); sequence < end; sequence++) {
      @Nullable Slot slot = current.get(sequence);
      if (slot != null) events.add(slot.event);
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

  /**
   * The slots of the events: the event of sequence number {@code n} goes in slot {@code n % length}.
   */
  private static final class Ring {

    private final AtomicReferenceArray<@Nullable Slot> slots;

    Ring(int length) {
      this.slots = new AtomicReferenceArray<>(length);
    }

    int length() {
      return this.slots.length();
    }

    /**
     * @return the slot of this sequence number, or {@code null} when it holds another one.
     */
    @Nullable Slot get(long sequence) {
      @Nullable Slot slot = this.slots.get(index(sequence));
      return slot != null && slot.sequence == sequence ? slot : null;
    }

    /**
     * Sets the slot unless it holds a more recent event.
     */
    void put(Slot slot) {
      int index = index(slot.sequence);
      @Nullable Slot current;
      do {
        current = this.slots.get(index);
        if (current != null && current.sequence >= slot.sequence) return;
      } while (!this.slots.compareAndSet(index, current, slot));
    }

    private int index(long sequence) {
      return (int) (sequence % this.slots.length());
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
