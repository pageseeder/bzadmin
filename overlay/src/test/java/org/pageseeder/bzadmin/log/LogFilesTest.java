package org.pageseeder.bzadmin.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pageseeder.bzadmin.log.LogFiles.LogFile;
import org.pageseeder.bzadmin.log.LogFiles.LogSource;

/**
 * The registered log files (M3.3): only the files of the registered sources, by opaque ID, and at
 * most a few reads at the same time.
 */
final class LogFilesTest {

  @TempDir
  Path dir;

  private Path logs;

  private Path outside;

  @BeforeEach
  void files() throws IOException {
    this.logs = Files.createDirectories(this.dir.resolve("logs"));
    this.outside = Files.createDirectories(this.dir.resolve("outside"));
    Files.writeString(this.logs.resolve("b.log"), "b\n");
    Files.writeString(this.logs.resolve("a.log"), "a\n");
    Files.writeString(this.logs.resolve("notes.txt"), "not a log\n");
    Files.writeString(this.logs.resolve(".hidden.log"), "hidden\n");
    Files.writeString(this.logs.resolve("a.log.1"), "rotated\n");
    Files.createDirectories(this.logs.resolve("folder.log"));
    Files.writeString(this.outside.resolve("secret.log"), "fixture-secret\n");
    Files.writeString(this.outside.resolve("server.out"), "registered\n");
  }

  @Test
  void listsTheLogFilesOfTheSources() throws IOException {
    Files.createSymbolicLink(this.logs.resolve("link.log"), this.outside.resolve("secret.log"));
    LogFiles files = LogFiles.of(List.of(this.logs, this.outside.resolve("server.out"), this.dir.resolve("missing")));
    List<LogSource> sources = files.list();
    assertEquals(2, sources.size(), "The missing source left out");
    assertTrue(sources.get(0).isDirectory());
    assertEquals(List.of("a.log", "b.log"), names(sources.get(0)), "Only regular *.log files, by name, no links");
    assertEquals(2, sources.get(0).total());
    assertEquals(List.of("server.out"), names(sources.get(1)), "A registered file, whatever its name");
    for (LogFile file : sources.get(0).files()) assertEquals(file.name(), files.find(file.id()).name());
    assertNotNull(files.find(sources.get(1).files().get(0).id()));
  }

  @Test
  void findsOnlyListedFiles() throws IOException {
    LogFiles files = LogFiles.of(List.of(this.logs));
    String secret = LogFiles.id(this.outside.resolve("secret.log").toRealPath());
    String notes = LogFiles.id(this.logs.resolve("notes.txt").toRealPath());
    for (String id : new String[]{secret, notes, "0123456789abcdef", "a.log", "../outside/secret.log", "", "0123456789ABCDEF"}) {
      assertNull(files.find(id), id);
    }
    assertNull(files.find(null));
  }

  @Test
  void identifiesFilesByTheirRealPath() throws IOException {
    Files.createSymbolicLink(this.dir.resolve("linked"), this.logs);
    String direct = LogFiles.of(List.of(this.logs)).list().get(0).files().get(0).id();
    LogFiles linked = LogFiles.of(List.of(this.dir.resolve("linked")));
    assertEquals(direct, linked.list().get(0).files().get(0).id(), "A linked source, the same IDs");
    assertEquals(16, direct.length());
    // The same directory twice, and one of its files registered: listed once
    LogFiles twice = LogFiles.of(List.of(this.logs, this.dir.resolve("linked"), this.logs.resolve("a.log")));
    assertEquals(1, twice.list().size());
  }

  @Test
  void resolvesThePropertyFromTheDataFolder() {
    Path base = this.dir.resolve("WEB-INF");
    assertEquals(List.of(base.resolve("logs"), Path.of("/var/log/app.log")), LogFiles.parse(" logs , ,/var/log/app.log,", base));
  }

  @Test
  void boundsTheListing() throws IOException {
    for (int i = 0; i < 1001; i++) {
      Path log = Files.writeString(this.logs.resolve(String.format("f%04d.log", i)), "");
      // f0000 is the oldest
      Files.setLastModifiedTime(log, FileTime.fromMillis(1_000_000_000_000L + i * 1000L));
    }
    Files.setLastModifiedTime(this.logs.resolve("a.log"), FileTime.fromMillis(0));
    Files.setLastModifiedTime(this.logs.resolve("b.log"), FileTime.fromMillis(0));
    LogFiles files = LogFiles.of(List.of(this.logs));
    LogSource source = files.list().get(0);
    assertEquals(LogFiles.MAX_FILES, source.files().size());
    assertEquals(1003, source.total());
    assertTrue(source.truncated());
    assertEquals("f0001.log", source.files().get(0).name(), "The most recent (not a.log, b.log, f0000.log), by name");
    assertEquals("f1000.log", source.files().get(LogFiles.MAX_FILES - 1).name());
    // Not listed, but found: by ID and by name
    assertNotNull(files.find(LogFiles.id(this.logs.resolve("a.log").toRealPath())));
    assertNotNull(files.findByName("f0000.log"));
    for (int i = 1001; i < LogFiles.MAX_SCAN; i++) Files.writeString(this.logs.resolve(String.format("f%05d.txt", i)), "");
    source = LogFiles.of(List.of(this.logs)).list().get(0);
    assertTrue(source.truncated());
    assertTrue(source.total() <= LogFiles.MAX_SCAN);
  }

  @Test
  void findsAFileByName() throws IOException {
    Path other = Files.createDirectories(this.dir.resolve("other"));
    Files.writeString(other.resolve("a.log"), "other a\n");
    Files.createSymbolicLink(this.logs.resolve("link.log"), this.outside.resolve("secret.log"));
    LogFiles files = LogFiles.of(List.of(this.logs, other, this.outside.resolve("server.out")));
    String a = LogFiles.id(this.logs.resolve("a.log").toRealPath());
    assertEquals(a, id(files.findByName("a.log")), "The first source");
    assertEquals(LogFiles.id(other.resolve("a.log").toRealPath()), id(files.findByName("other/a.log")), "Its directory named");
    assertEquals(a, id(files.findByName("logs/a.log")));
    assertNotNull(files.findByName("server.out"), "A registered file");
    // Compared with the names found, never resolved
    for (String name : new String[]{"notes.txt", "link.log", ".hidden.log", "folder.log", "a.log.1", "../outside/secret.log",
        "secret.log", "outside/secret.log", "/etc/hosts", "./a.log", "logs/../a.log", "logs//a.log", "missing/a.log",
        "A.LOG", "", "server.out/a.log"}) {
      assertNull(files.findByName(name), name);
    }
    assertNull(files.findByName(null));
  }

  @Test
  void opensNoLinkInADirectory() throws IOException {
    LogFiles files = LogFiles.of(List.of(this.logs));
    LogFile file = files.list().get(0).files().get(0);
    // Replaced by a link after it was listed
    Files.delete(this.logs.resolve("a.log"));
    Files.createSymbolicLink(this.logs.resolve("a.log"), this.outside.resolve("secret.log"));
    assertThrows(NoSuchFileException.class, file::open);
    assertNull(files.find(file.id()));
    // Deleted
    Files.delete(this.logs.resolve("a.log"));
    assertThrows(NoSuchFileException.class, file::open);
  }

  @Test
  void changesIdentityWhenTheFileIsRotated() throws IOException {
    Path log = this.logs.resolve("a.log");
    LogFile file = LogFiles.of(List.of(this.logs)).list().get(0).files().get(0);
    String identity = file.identity();
    Files.writeString(log, "more\n", StandardOpenOption.APPEND);
    assertEquals(identity, file.identity(), "Appending keeps the identity");
    assumeTrue(Files.readAttributes(log, java.nio.file.attribute.BasicFileAttributes.class).fileKey() != null);
    Files.move(log, this.logs.resolve("a.log.1"), StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(log, "new\n");
    assertNotEquals(identity, file.identity());
  }

  @Test
  void limitsTheReadsAtTheSameTime() throws Exception {
    int threads = 16;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    AtomicInteger active = new AtomicInteger();
    AtomicInteger most = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch full = new CountDownLatch(LogReads.MAX_CONCURRENT);
    try {
      List<Future<Boolean>> reads = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        reads.add(executor.submit(() -> {
          start.await();
          if (!LogReads.acquire()) return false;
          try {
            most.accumulateAndGet(active.incrementAndGet(), Math::max);
            // Hold the permit until all the permits are taken, so that the reads overlap
            full.countDown();
            return full.await(5, TimeUnit.SECONDS);
          } finally {
            active.decrementAndGet();
            LogReads.release();
          }
        }));
      }
      start.countDown();
      for (Future<Boolean> read : reads) assertTrue(read.get(30, TimeUnit.SECONDS), "Each waits for its turn");
      assertEquals(LogReads.MAX_CONCURRENT, most.get());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void refusesAReadWhenTheOthersTakeTooLong() {
    List<Boolean> acquired = new ArrayList<>();
    try {
      for (int i = 0; i < LogReads.MAX_CONCURRENT; i++) acquired.add(LogReads.acquire());
      assertEquals(LogReads.MAX_CONCURRENT, acquired.stream().filter(b -> b).count());
      long started = System.nanoTime();
      assertFalse(LogReads.acquire(), "Refused");
      assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(LogReads.WAIT_MILLIS) - 1_000_000);
    } finally {
      for (boolean a : acquired) if (a) LogReads.release();
    }
  }

  @Test
  void readsAListedFile() throws IOException {
    Files.writeString(this.logs.resolve("a.log"), "one\ntwo\n");
    LogFile file = LogFiles.of(List.of(this.logs)).list().get(0).files().get(0);
    try (SeekableByteChannel channel = file.open()) {
      LogTail tail = LogTail.read(channel, file.identity(), null, 10, 1000, 100);
      assertEquals(List.of("one", "two"), tail.lines());
    }
  }

  /**
   * @return the ID of the file found, which identifies its real path.
   */
  private static String id(LogFile file) {
    assertNotNull(file);
    return file.id();
  }

  private static List<String> names(LogSource source) {
    return source.files().stream().map(LogFile::name).collect(Collectors.toList());
  }

}
