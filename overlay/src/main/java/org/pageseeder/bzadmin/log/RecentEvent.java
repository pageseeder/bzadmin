package org.pageseeder.bzadmin.log;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A log event kept by the {@linkplain RecentEvents recent events}: immutable text only, made when
 * the event is logged, so that it keeps no reference to the arguments, throwable or logger of the
 * application.
 *
 * <p>Its text is bounded: at most {@value #MAX_TEXT} characters for the message and the throwable
 * (class names, messages and stack trace elements, in this order), with "…" where a value is cut,
 * at most {@value #MAX_CAUSES} throwables (the throwable and its causes) of
 * {@value #MAX_STEPS} stack trace elements each, and {@value #MAX_NAME} characters for the names
 * of the logger and thread.
 */
final class RecentEvent {

  /** The maximum number of characters of the message and throwable of an event. */
  static final int MAX_TEXT = 8192;

  /** The maximum number of characters of the name of a logger or thread. */
  static final int MAX_NAME = 256;

  /** The maximum number of throwables of an event: the throwable and its causes. */
  static final int MAX_CAUSES = 8;

  /** The maximum number of stack trace elements per throwable, so that its causes fit too. */
  static final int MAX_STEPS = 16;

  private final long timestamp;

  private final EventLevel level;

  private final @Nullable String logger;

  private final @Nullable String thread;

  private final @Nullable String message;

  private final @Nullable Trace throwable;

  private final boolean truncated;

  /**
   * @param timestamp When it was logged, in milliseconds since the epoch.
   * @param level     Its level.
   * @param logger    The name of the logger, cut to {@value #MAX_NAME} characters.
   * @param thread    The name of the thread, cut to {@value #MAX_NAME} characters.
   * @param message   The message, taken from the budget of the event.
   * @param throwable The throwable, taken from the same budget (may be {@code null}).
   * @param truncated Whether the budget cut a value or left out stack trace elements.
   */
  RecentEvent(long timestamp, EventLevel level, @Nullable String logger, @Nullable String thread, @Nullable String message, @Nullable Trace throwable, boolean truncated) {
    this.timestamp = timestamp;
    this.level = level;
    this.logger = Budget.cut(logger, MAX_NAME);
    this.thread = Budget.cut(thread, MAX_NAME);
    this.message = message;
    this.throwable = throwable;
    this.truncated = truncated;
  }

  /** @return when it was logged, in milliseconds since the epoch. */
  long timestamp() {
    return this.timestamp;
  }

  /** @return its level. */
  EventLevel level() {
    return this.level;
  }

  /** @return the name of the logger (may be {@code null}). */
  @Nullable String logger() {
    return this.logger;
  }

  /** @return the name of the thread (may be {@code null}). */
  @Nullable String thread() {
    return this.thread;
  }

  /** @return the formatted message (may be {@code null}). */
  @Nullable String message() {
    return this.message;
  }

  /** @return the throwable, or {@code null}. */
  @Nullable Trace throwable() {
    return this.throwable;
  }

  /** @return whether text was cut or stack trace elements left out to fit the bounds. */
  boolean truncated() {
    return this.truncated;
  }

  /**
   * A throwable as text.
   */
  static final class Trace {

    private final String className;

    private final @Nullable String message;

    private final List<String> steps;

    private final int omitted;

    private final @Nullable Trace cause;

    /**
     * @param className The class of the throwable.
     * @param message   Its message (may be {@code null}).
     * @param steps     The stack trace elements kept.
     * @param omitted   The number of stack trace elements left out (in common with the enclosing
     *                  trace, past the servlet API, past {@value RecentEvent#MAX_STEPS}, or past the
     *                  budget).
     * @param cause     Its cause (may be {@code null}).
     */
    Trace(String className, @Nullable String message, List<String> steps, int omitted, @Nullable Trace cause) {
      this.className = className;
      this.message = message;
      this.steps = List.copyOf(steps);
      this.omitted = omitted;
      this.cause = cause;
    }

    /** @return the class of the throwable. */
    String className() {
      return this.className;
    }

    /** @return its message (may be {@code null}). */
    @Nullable String message() {
      return this.message;
    }

    /** @return the stack trace elements kept. */
    List<String> steps() {
      return this.steps;
    }

    /** @return the number of stack trace elements left out. */
    int omitted() {
      return this.omitted;
    }

    /** @return its cause, or {@code null}. */
    @Nullable Trace cause() {
      return this.cause;
    }
  }

  /**
   * The characters left for the text of an event.
   */
  static final class Budget {

    private int left;

    private boolean truncated;

    /**
     * @param size The number of characters.
     */
    Budget(int size) {
      this.left = size;
    }

    /**
     * @param text A value of the event (may be {@code null}).
     * @return the value, cut with "…" to the characters left, which it uses up.
     */
    @Nullable String take(@Nullable String text) {
      return text == null ? null : takeNonNull(text);
    }

    /** Takes required text, preserving its non-null contract even when the budget is exhausted. */
    String takeNonNull(String text) {
      if (text.length() <= this.left) {
        this.left -= text.length();
        return text;
      }
      String cut = cutNonNull(text, this.left);
      this.left = 0;
      this.truncated = true;
      return cut;
    }

    /** @return whether no character is left. */
    boolean exhausted() {
      return this.left == 0;
    }

    /** Records that something was left out. */
    void omit() {
      this.truncated = true;
    }

    /** @return whether a value was cut or something left out. */
    boolean truncated() {
      return this.truncated;
    }

    /**
     * @return the text cut to at most {@code max} characters followed by "…", without splitting a
     *   surrogate pair; the text itself when it is not longer.
     */
    static @Nullable String cut(@Nullable String text, int max) {
      return text == null ? null : cutNonNull(text, max);
    }

    private static String cutNonNull(String text, int max) {
      if (text.length() <= max) return text;
      int end = max;
      if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
      return text.substring(0, end) + "…";
    }
  }

}
