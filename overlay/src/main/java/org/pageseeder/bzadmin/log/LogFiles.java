package org.pageseeder.bzadmin.log;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;

/**
 * The log files that the admin pages can read: the files registered by the host, or found in the
 * directories it registered. A request names a file by its opaque {@linkplain LogFile#id() id}, from
 * a listing, never by a name or a path.
 *
 * <p>Sources (in order, duplicates removed):
 * <ul>
 *   <li>the directories and files of the global property {@value #PROPERTY}, separated by commas,
 *     relative to the application data folder unless they are absolute (relative ones are left
 *     out when Berlioz has no application data folder);</li>
 *   <li>when that property is not set, the directories of the file appenders of the logging
 *     framework (logback, see {@link LoggingBackends}), as Bastille's {@code ListAvailableLogFiles}.</li>
 * </ul>
 *
 * <p>In a directory, only regular files named {@code *.log} that are not hidden or symbolic links;
 * a source itself may be a link. At most {@value #MAX_SCAN} directory entries are read per
 * directory; of the log files found, the {@value #MAX_FILES} most recently modified are listed, but
 * any of them can be read, by ID or {@linkplain #findByName(String) by name} (dated logs past the
 * listing stay reachable). A name is only compared with the names of these files: it never becomes
 * a path.
 *
 * <p>Nothing is cached: every call reads the file system and the configuration.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class LogFiles {

  /** The global property for the log directories and files. */
  public static final String PROPERTY = "berlioz.admin.logs";

  /** The maximum number of log files listed per directory. */
  static final int MAX_FILES = 1000;

  /** The maximum number of directory entries read per directory. */
  static final int MAX_SCAN = 10_000;

  private static final Pattern ID = Pattern.compile("[0-9a-f]{16}");

  /** Where the sources come from. */
  enum Origin {

    /** The global property {@value LogFiles#PROPERTY}. */
    CONFIGURED,

    /** The file appenders of the logging framework. */
    DETECTED,

    /** Neither: the property is not set and the logging framework does not list its files. */
    NONE;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  private final List<Path> sources;

  private final Origin origin;

  private LogFiles(List<Path> sources, Origin origin) {
    this.sources = sources;
    this.origin = origin;
  }

  /**
   * @param sources The directories and files, in display order.
   * @return the log files of these sources.
   */
  static LogFiles of(List<Path> sources) {
    return new LogFiles(distinct(sources), Origin.CONFIGURED);
  }

  /**
   * @return the log files as configured now.
   */
  public static LogFiles fromSettings() {
    String configured = GlobalSettings.get(PROPERTY);
    if (configured != null && !configured.isBlank()) {
      File data = GlobalSettings.getAppData();
      return of(parse(configured, data != null ? data.toPath() : null));
    }
    List<Path> directories = LoggingBackends.current().logDirectories();
    if (directories == null) return new LogFiles(List.of(), Origin.NONE);
    return new LogFiles(distinct(directories), Origin.DETECTED);
  }

  /**
   * @param property The value of the property: paths separated by commas.
   * @param base     The folder of relative paths, or {@code null} to leave them out.
   * @return the paths.
   */
  static List<Path> parse(String property, @Nullable Path base) {
    return Arrays.stream(property.split(","))
        .filter(path -> !path.isBlank())
        .map(path -> resolve(base, path.trim()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  private static @Nullable Path resolve(@Nullable Path base, String path) {
    if (base != null) return base.resolve(path);
    Path absolute = Path.of(path);
    return absolute.isAbsolute() ? absolute : null;
  }

  private static List<Path> distinct(List<Path> paths) {
    return paths.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .distinct()
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * @return where the sources come from.
   */
  Origin origin() {
    return this.origin;
  }

  /**
   * @return the sources and their files, in order; a source that is missing is left out.
   */
  List<LogSource> list() {
    List<LogSource> listed = new ArrayList<>();
    Set<Path> directories = new LinkedHashSet<>();
    Set<String> ids = new LinkedHashSet<>();
    for (Path source : this.sources) {
      LogSource s = list(source, directories, ids);
      if (s != null) listed.add(s);
    }
    return listed;
  }

  /**
   * @param id The ID of a log file, from a listing.
   * @return the file with this ID among the files found now, or {@code null}.
   */
  @Nullable LogFile find(@Nullable String id) {
    if (id == null || !ID.matcher(id).matches()) return null;
    for (LogSource source : list()) {
      for (LogFile file : source.found()) {
        if (file.id().equals(id)) return file;
      }
    }
    return null;
  }

  /**
   * Finds a log file by its name, as a person types it: {@code app.2026-10-23.log} (the first file
   * with this name, in the order of the sources), or {@code logs/app.2026-10-23.log} to name the
   * directory too (its last name, when several sources have a file with this name).
   *
   * @param name The name of a log file, optionally after the name of its directory and "/".
   * @return the file with this name among the files found now, or {@code null}.
   */
  @Nullable LogFile findByName(@Nullable String name) {
    if (name == null || name.isEmpty()) return null;
    int slash = name.lastIndexOf('/');
    String directory = slash < 0 ? null : name.substring(0, slash);
    String file = name.substring(slash + 1);
    for (LogSource source : list()) {
      if (directory != null && !(source.isDirectory() && directory.equals(source.name()))) continue;
      for (LogFile f : source.found()) {
        if (f.name().equals(file)) return f;
      }
    }
    return null;
  }

  /**
   * @param directories The real paths of the directories listed so far: a directory is listed once.
   * @param ids         The IDs of the files listed so far: a file is listed once.
   */
  private static @Nullable LogSource list(Path source, Set<Path> directories, Set<String> ids) {
    BasicFileAttributes attributes;
    Path real;
    try {
      attributes = Files.readAttributes(source, BasicFileAttributes.class);
      real = source.toRealPath();
    } catch (IOException ex) {
      return null;
    }
    // A registered file
    if (attributes.isRegularFile()) {
      LogFile file = new LogFile(id(real), real.getFileName().toString(), real, attributes, false);
      if (!ids.add(file.id())) return null;
      return new LogSource(source, false, List.of(file), List.of(file), false);
    }
    if (!attributes.isDirectory() || !directories.add(real)) return null;
    Scan scan = scan(real);
    if (scan == null) return null;
    List<LogFile> files = scan.files;
    files.removeIf(file -> !ids.add(file.id()));
    files.sort(Comparator.comparing(LogFile::name));
    List<LogFile> listed = files;
    boolean truncated = scan.truncated;
    if (files.size() > MAX_FILES) {
      listed = mostRecent(files);
      truncated = true;
    }
    return new LogSource(source, true, Collections.unmodifiableList(listed), Collections.unmodifiableList(files), truncated);
  }

  /**
   * Reads at most {@value #MAX_SCAN} entries of a directory.
   *
   * @return the log files found, or {@code null} if it is no longer a directory.
   */
  private static @Nullable Scan scan(Path directory) {
    Scan scan = new Scan();
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      Iterator<Path> i = entries.iterator();
      for (int scanned = 0; scanned < MAX_SCAN && i.hasNext(); scanned++) {
        LogFile file = logFile(i.next());
        if (file != null) scan.files.add(file);
      }
      scan.truncated = i.hasNext();
    } catch (NoSuchFileException | NotDirectoryException ex) {
      return null;
    } catch (DirectoryIteratorException ex) {
      throw new UncheckedIOException(ex.getCause());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return scan;
  }

  /**
   * @return the log file of a directory entry, or {@code null} if it is not one.
   */
  private static @Nullable LogFile logFile(Path entry) throws IOException {
    String name = entry.getFileName().toString();
    if (!name.endsWith(".log") || name.startsWith(".")) return null;
    try {
      BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return attributes.isRegularFile() ? new LogFile(id(entry), name, entry, attributes, true) : null;
    } catch (NoSuchFileException ex) {
      // Deleted while listed
      return null;
    }
  }

  /**
   * @return the {@value #MAX_FILES} most recent files, by name: with dated logs, the oldest are the
   *   least useful.
   */
  private static List<LogFile> mostRecent(List<LogFile> files) {
    List<LogFile> recent = new ArrayList<>(files);
    recent.sort(Comparator.comparingLong(LogFile::modified).reversed().thenComparing(LogFile::name));
    recent = new ArrayList<>(recent.subList(0, MAX_FILES));
    recent.sort(Comparator.comparing(LogFile::name));
    return recent;
  }

  /**
   * The log files found in the entries read of a directory.
   */
  private static final class Scan {

    private final List<LogFile> files = new ArrayList<>();

    /** Whether the directory has entries that were not read. */
    private boolean truncated;
  }

  /**
   * @return the ID of the file with this real path: the first 16 hexadecimal digits of the SHA-256
   *   digest of the path, stable while the file keeps its path.
   */
  static String id(Path real) {
    return hex(sha256(real.toString()), 16);
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      // Every Java platform has it
      throw new IllegalStateException(ex);
    }
  }

  private static String hex(byte[] bytes, int digits) {
    StringBuilder hex = new StringBuilder(digits);
    for (int i = 0; hex.length() < digits; i++) {
      hex.append(Character.forDigit((bytes[i] >> 4) & 0xF, 16)).append(Character.forDigit(bytes[i] & 0xF, 16));
    }
    return hex.toString();
  }

  /**
   * A registered directory or file, and the log files it has.
   */
  static final class LogSource {

    private final Path path;

    private final boolean directory;

    private final List<LogFile> files;

    private final List<LogFile> found;

    private final boolean truncated;

    LogSource(Path path, boolean directory, List<LogFile> files, List<LogFile> found, boolean truncated) {
      this.path = path;
      this.directory = directory;
      this.files = files;
      this.found = found;
      this.truncated = truncated;
    }

    /** @return the last name of the path as registered. */
    String name() {
      Path name = this.path.getFileName();
      return name != null ? name.toString() : this.path.toString();
    }

    /** @return the path as registered. */
    Path path() {
      return this.path;
    }

    /** @return whether it is a directory (otherwise a registered file). */
    boolean isDirectory() {
      return this.directory;
    }

    /** @return the log files listed (the most recent ones), by name. */
    List<LogFile> files() {
      return this.files;
    }

    /** @return every log file found in the entries read, by name: they can all be read. */
    List<LogFile> found() {
      return this.found;
    }

    /** @return the number of log files found in the entries read. */
    int total() {
      return this.found.size();
    }

    /** @return whether entries were not read or files not listed. */
    boolean truncated() {
      return this.truncated;
    }
  }

  /**
   * A log file that can be read.
   */
  static final class LogFile {

    private final String id;

    private final String name;

    private final Path path;

    private final long size;

    private final long modified;

    /** Whether it is in a registered directory (opened without following a link). */
    private final boolean listed;

    LogFile(String id, String name, Path path, BasicFileAttributes attributes, boolean listed) {
      this.id = id;
      this.name = name;
      this.path = path;
      this.size = attributes.size();
      this.modified = attributes.lastModifiedTime().toMillis();
      this.listed = listed;
    }

    /** @return the opaque ID, for requests. */
    String id() {
      return this.id;
    }

    /** @return the name of the file. */
    String name() {
      return this.name;
    }

    /** @return the size when it was listed. */
    long size() {
      return this.size;
    }

    /** @return when it was last modified, when it was listed. */
    long modified() {
      return this.modified;
    }

    /**
     * Open the file for reading: a file in a registered directory that was replaced by a link since
     * it was listed is not opened.
     *
     * @return a channel to read the file.
     * @throws NoSuchFileException if the file is no longer there.
     */
    SeekableByteChannel open() throws IOException {
      OpenOption[] options = this.listed
          ? new OpenOption[]{StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS}
          : new OpenOption[]{StandardOpenOption.READ};
      try {
        return Files.newByteChannel(this.path, options);
      } catch (IOException ex) {
        // The JDK reports a link opened with NOFOLLOW_LINKS as "Too many levels of symbolic links"
        if (this.listed && Files.isSymbolicLink(this.path)) throw new NoSuchFileException(this.path.toString());
        throw ex;
      }
    }

    /**
     * @return the identity of the file now, for cursors: it changes when the file is replaced.
     */
    String identity() throws IOException {
      LinkOption[] options = this.listed ? new LinkOption[]{LinkOption.NOFOLLOW_LINKS} : new LinkOption[0];
      BasicFileAttributes attributes = Files.readAttributes(this.path, BasicFileAttributes.class, options);
      // The file key (device and inode on POSIX), else the creation time (Windows): not the creation
      // time on POSIX, where the JDK may report the modification time instead
      Object key = attributes.fileKey() != null ? attributes.fileKey() : attributes.creationTime().toMillis();
      return hex(sha256(key.toString()), 16);
    }
  }

}
