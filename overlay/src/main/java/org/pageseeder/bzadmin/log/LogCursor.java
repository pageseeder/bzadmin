package org.pageseeder.bzadmin.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Where a read of a log file ended, to read only what was added since.
 *
 * <p>As text: {@code <identity>.<position>.<line>}, where the identity tells whether the file is still
 * the same (not replaced by a rotation), the position is a byte offset after a line feed, and the
 * line is the number of the next line (0 when unknown). It is not a secret: a cursor that does not
 * apply to the file only makes the tail start again from the end of the file.
 */
final class LogCursor {

  private static final Pattern FORMAT = Pattern.compile("([0-9a-f]{1,16})\\.(\\d{1,18})\\.(\\d{1,18})");

  private final String identity;

  private final long position;

  private final long line;

  LogCursor(String identity, long position, long line) {
    this.identity = identity;
    this.position = position;
    this.line = line;
  }

  /**
   * @param cursor A cursor as text, or {@code null}.
   * @return the cursor, or {@code null} if the text is not one.
   */
  static @Nullable LogCursor parse(@Nullable String cursor) {
    if (cursor == null) return null;
    Matcher m = FORMAT.matcher(cursor);
    if (!m.matches()) return null;
    return new LogCursor(m.group(1), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)));
  }

  String identity() {
    return this.identity;
  }

  long position() {
    return this.position;
  }

  long line() {
    return this.line;
  }

  @Override
  public String toString() {
    return this.identity + '.' + this.position + '.' + this.line;
  }

}
