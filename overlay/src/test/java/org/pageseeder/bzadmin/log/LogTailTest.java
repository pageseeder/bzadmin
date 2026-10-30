package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bounded tail of a file (M3.3): bytes, lines and line lengths, UTF-8 boundaries, cursors over
 * appends, rotation and truncation, and the lines before a position.
 */
final class LogTailTest {

  private static final String ID = "0123456789abcdef";

  @TempDir
  Path dir;

  @Test
  void readsAFileFromItsStart() throws Exception {
    Path file = write("one INFO\ntwo WARN\r\nthree\n");
    LogTail tail = read(file, null, 100, 1000, 100);
    assertEquals(List.of("one INFO", "two WARN", "three"), tail.lines());
    assertEquals(1, tail.first());
    assertFalse(tail.truncated());
    assertFalse(tail.incomplete());
    assertFalse(tail.reset());
    assertEquals(Files.size(file), tail.cursor().position());
    assertEquals(4, tail.cursor().line());
  }

  @Test
  void keepsTheLastLines() throws Exception {
    Path file = write("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n");
    LogTail tail = read(file, null, 3, 1000, 100);
    assertEquals(List.of("8", "9", "10"), tail.lines());
    assertEquals(8, tail.first());
    assertTrue(tail.truncated());
  }

  @Test
  void readsTheEndOfALargeFile() throws Exception {
    Path file = write("line a\nline b\nline c\nline d\n");
    // From byte 10, in the middle of "line b"
    LogTail tail = read(file, null, 100, 18, 100);
    assertEquals(List.of("line c", "line d"), tail.lines());
    assertEquals(0, tail.first(), "Line numbers unknown");
    assertTrue(tail.truncated());
    assertEquals(0, tail.cursor().line());
  }

  @Test
  void cutsLongLines() throws Exception {
    Path file = write("x".repeat(100_000) + "\nend\n");
    LogTail tail = read(file, null, 100, 1_000_000, 8192);
    assertEquals(8193, tail.lines().get(0).length());
    assertTrue(tail.lines().get(0).endsWith("…"));
    assertEquals("end", tail.lines().get(1));
  }

  @Test
  void neverSplitsACharacter() throws Exception {
    // é is 2 bytes: a window of 22 bytes starts at byte 68, in the middle of one
    Path file = write("éééé\n".repeat(10));
    LogTail tail = read(file, null, 100, 22, 100);
    assertFalse(tail.lines().isEmpty());
    for (String line : tail.lines()) assertEquals("éééé", line);
    // 😀 is a surrogate pair: a cut at 5 characters would split the third one
    LogTail emoji = read(write("😀".repeat(10) + "\n"), null, 100, 1000, 5);
    assertEquals("😀😀…", emoji.lines().get(0));
    // Malformed input is replaced
    Path malformed = dir.resolve("malformed.log");
    Files.write(malformed, new byte[]{'a', (byte) 0xC3, 'b', '\n'});
    assertEquals("a�b", read(malformed, null, 100, 1000, 100).lines().get(0));
  }

  @Test
  void showsAnIncompleteLastLineAgain() throws Exception {
    Path file = write("one\ntwo");
    LogTail tail = read(file, null, 100, 1000, 100);
    assertEquals(List.of("one", "two"), tail.lines());
    assertTrue(tail.incomplete());
    assertEquals(4, tail.cursor().position(), "At the start of the incomplete line");
    assertEquals(2, tail.cursor().line());
    Files.writeString(file, " done\nthree\n", StandardOpenOption.APPEND);
    LogTail next = read(file, tail.cursor(), 100, 1000, 100);
    assertEquals(List.of("two done", "three"), next.lines());
    assertEquals(2, next.first());
    assertFalse(next.incomplete());
    assertFalse(next.reset());
  }

  @Test
  void continuesFromACursor() throws Exception {
    Path file = write("one\ntwo\n");
    LogTail tail = read(file, null, 100, 1000, 100);
    LogTail none = read(file, tail.cursor(), 100, 1000, 100);
    assertTrue(none.lines().isEmpty());
    assertEquals(0, none.bytesRead());
    assertEquals(tail.cursor().toString(), none.cursor().toString());
    Files.writeString(file, "three INFO\n", StandardOpenOption.APPEND);
    LogTail next = read(file, LogCursor.parse(tail.cursor().toString()), 100, 1000, 100);
    assertEquals(List.of("three INFO"), next.lines());
    assertEquals(3, next.first());
    assertEquals(11, next.bytesRead(), "Only what was added");
    assertFalse(next.truncated());
  }

  @Test
  void startsAgainWhenTheFileIsTruncated() throws Exception {
    Path file = write("one\ntwo\nthree\n");
    LogTail tail = read(file, null, 100, 1000, 100);
    Files.writeString(file, "new\n");
    LogTail next = read(file, tail.cursor(), 100, 1000, 100);
    assertTrue(next.reset());
    assertEquals(List.of("new"), next.lines());
    assertEquals(1, next.first());
  }

  @Test
  void startsAgainWhenTheFileIsReplaced() throws Exception {
    Path file = write("one\ntwo\n");
    LogTail tail = read(file, null, 100, 1000, 100);
    Files.writeString(file, "one\ntwo\nthree\n");
    // Another identity: the file was rotated, and the new one is as long
    LogTail rotated = read(file, "fedcba9876543210", tail.cursor(), 100, 1000, 100);
    assertTrue(rotated.reset());
    assertEquals(List.of("one", "two", "three"), rotated.lines());
    // The same identity, but no line feed before the position: rewritten
    Files.writeString(file, "a much longer first line\n");
    LogTail rewritten = read(file, tail.cursor(), 100, 1000, 100);
    assertTrue(rewritten.reset());
    assertEquals(List.of("a much longer first line"), rewritten.lines());
  }

  @Test
  void startsAgainWhenMoreWasAddedThanARead() throws Exception {
    Path file = write("one\n");
    LogTail tail = read(file, null, 100, 22, 100);
    Files.writeString(file, "line two\nline three\nline four\n", StandardOpenOption.APPEND);
    LogTail next = read(file, tail.cursor(), 100, 22, 100);
    assertTrue(next.reset());
    assertTrue(next.truncated());
    assertEquals(List.of("line three", "line four"), next.lines());
    assertTrue(next.bytesRead() <= 22);
  }

  @Test
  void readsAtMostTheBoundOfAHugeFile() throws IOException {
    // A terabyte of "line\n": only the end is read
    GeneratedChannel channel = new GeneratedChannel(1L << 40, 0);
    LogTail tail = LogTail.read(channel, ID, null, 5000, 4L * 1024 * 1024, 8192);
    assertEquals(5000, tail.lines().size());
    assertTrue(tail.truncated());
    assertTrue(channel.read <= 4L * 1024 * 1024 + 1, "Read " + channel.read);
    // A single line longer than the window: no line, and the cursor after it
    GeneratedChannel noLineFeed = new GeneratedChannel(1L << 40, -1);
    LogTail long_ = LogTail.read(noLineFeed, ID, null, 5000, 4L * 1024 * 1024, 8192);
    assertTrue(long_.lines().isEmpty());
    assertEquals(1L << 40, long_.cursor().position());
    assertTrue(noLineFeed.read <= 4L * 1024 * 1024 + 1);
  }

  @Test
  void readsNoMoreThanTheSizeWhenOpened() throws IOException {
    // The file grows by 1 MB at each read
    GeneratedChannel channel = new GeneratedChannel(1000, 1024 * 1024);
    LogTail tail = LogTail.read(channel, ID, null, 5000, 4L * 1024 * 1024, 8192);
    assertEquals(200, tail.lines().size());
    assertEquals(1000, channel.read);
    assertEquals(1000, tail.cursor().position());
  }

  @Test
  void stopsWhenTheFileIsTruncatedWhileRead() throws IOException {
    GeneratedChannel channel = new GeneratedChannel(1L << 20, 0);
    channel.endAt = 100_000;
    LogTail tail = LogTail.read(channel, ID, null, 5000, 1L << 20, 8192);
    assertEquals(100_000, tail.bytesRead());
    assertEquals(5000, tail.lines().size());
  }

  @Test
  void readsTheLinesBefore() throws Exception {
    Path file = write("1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n");
    LogTail tail = read(file, null, 3, 1000, 100);
    assertEquals(14, tail.before().position(), "The start of line 8");
    assertEquals(8, tail.before().line());
    assertEquals(14, tail.positions()[0]);
    LogTail earlier = readBefore(file, LogCursor.parse(tail.before().toString()), 3, 1000);
    assertEquals(List.of("5", "6", "7"), earlier.lines());
    assertEquals(5, earlier.first());
    assertTrue(earlier.truncated());
    assertFalse(earlier.reset());
    assertFalse(earlier.incomplete());
    assertEquals(8, earlier.before().position());
    assertEquals(14, earlier.bytesRead(), "Only what is before the position");
    LogTail start = readBefore(file, earlier.before(), 10, 1000);
    assertEquals(List.of("1", "2", "3", "4"), start.lines());
    assertEquals(1, start.first());
    assertFalse(start.truncated());
    assertEquals(null, start.before(), "The first line of the file");
    assertTrue(readBefore(file, new LogCursor(ID, 0, 1), 10, 1000).lines().isEmpty());
  }

  @Test
  void readsTheLinesBeforeWithinTheBounds() throws Exception {
    Path file = write("line a\nline b\nline c\nline d\nline e\n");
    LogTail tail = read(file, null, 100, 15, 100);
    assertEquals(List.of("line d", "line e"), tail.lines());
    assertEquals(0, tail.first(), "Line numbers unknown");
    assertEquals(21, tail.before().position());
    assertEquals(0, tail.before().line());
    // From byte 6, the end of "line a"
    LogTail earlier = readBefore(file, tail.before(), 100, 15);
    assertEquals(List.of("line b", "line c"), earlier.lines());
    assertEquals(0, earlier.first());
    assertEquals(7, earlier.before().position());
    LogTail start = readBefore(file, earlier.before(), 100, 15);
    assertEquals(List.of("line a"), start.lines());
    assertEquals(1, start.first(), "Read from the start of the file");
    assertEquals(null, start.before());
  }

  @Test
  void readsTheEndWhenTheLinesBeforeDoNotApply() throws Exception {
    Path file = write("one\ntwo\nthree\n");
    LogTail tail = read(file, null, 1, 1000, 100);
    LogCursor before = tail.before();
    // Not after a line feed
    LogTail misplaced = readBefore(file, new LogCursor(ID, 5, 0), 100, 1000);
    assertTrue(misplaced.reset());
    assertEquals(List.of("one", "two", "three"), misplaced.lines());
    // Replaced, or truncated
    assertTrue(readBefore(file, new LogCursor("fedcba9876543210", before.position(), 0), 100, 1000).reset());
    Files.writeString(file, "new\n");
    LogTail truncated = readBefore(file, before, 100, 1000);
    assertTrue(truncated.reset());
    assertEquals(List.of("new"), truncated.lines());
  }

  @Test
  void parsesCursors() {
    assertEquals("0123456789abcdef.12.3", LogCursor.parse("0123456789abcdef.12.3").toString());
    for (String invalid : new String[]{"", "x.1.2", "0123.-1.2", "0123.1", "0123.1.2.3", "0123.99999999999999999999.1"}) {
      assertEquals(null, LogCursor.parse(invalid), invalid);
    }
  }

  private Path write(String content) throws IOException {
    Path file = Files.createTempFile(this.dir, "tail", ".log");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private static LogTail read(Path file, LogCursor cursor, int lines, long bytes, int length) throws IOException {
    return read(file, ID, cursor, lines, bytes, length);
  }

  private static LogTail readBefore(Path file, LogCursor before, int lines, long bytes) throws IOException {
    try (SeekableByteChannel channel = Files.newByteChannel(file)) {
      return LogTail.readBefore(channel, ID, before, lines, bytes, 100);
    }
  }

  private static LogTail read(Path file, String identity, LogCursor cursor, int lines, long bytes, int length) throws IOException {
    try (SeekableByteChannel channel = Files.newByteChannel(file)) {
      return LogTail.read(channel, identity, cursor, lines, bytes, length);
    }
  }

  /**
   * A read-only channel over generated content, "line\n" repeated (or only "x" without line feed),
   * that counts the bytes read and can grow at each read or end early.
   */
  private static final class GeneratedChannel implements SeekableByteChannel {

    private long size;

    private final long growth;

    private final boolean lineFeeds;

    private long position = 0;

    long read = 0;

    long endAt = Long.MAX_VALUE;

    /**
     * @param size   The initial size.
     * @param growth The bytes added at each read; negative for content without line feeds.
     */
    GeneratedChannel(long size, long growth) {
      this.size = size;
      this.growth = Math.max(0, growth);
      this.lineFeeds = growth >= 0;
    }

    @Override
    public int read(ByteBuffer dst) {
      if (this.position >= Math.min(this.size, this.endAt)) return -1;
      int n = 0;
      while (dst.hasRemaining() && this.position < Math.min(this.size, this.endAt)) {
        dst.put(this.lineFeeds ? (byte) "line\n".charAt((int) (this.position % 5)) : (byte) 'x');
        this.position++;
        n++;
      }
      this.read += n;
      this.size += this.growth;
      return n;
    }

    @Override
    public long position() {
      return this.position;
    }

    @Override
    public SeekableByteChannel position(long position) {
      this.position = position;
      return this;
    }

    @Override
    public long size() {
      return this.size;
    }

    @Override
    public int write(ByteBuffer src) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel truncate(long size) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void close() {
      // Nothing to release: the content is generated
    }
  }

}
