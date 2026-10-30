package org.pageseeder.bzadmin.psml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.pageseeder.bzadmin.io.Utf8;
import org.pageseeder.bzadmin.psml.PsmlException.Problem;

import org.jspecify.annotations.Nullable;

/**
 * A directory of PSML files, read-only, with the path policy of the repository.
 *
 * <h3>Paths</h3>
 * <p>A path names a folder or a {@value #EXTENSION} document in the root, with "/" between names
 * ("/" or "" for the root itself, "/content/home.psml"); a leading and a trailing "/" are ignored.
 * It is invalid ({@link Problem#INVALID_PATH}) when longer than {@value #MAX_PATH_LENGTH}
 * characters, deeper than {@value #MAX_DEPTH} names, or when a name is empty, starts with "." (so
 * "." and ".." too) or contains "\", ":" or a control character.
 *
 * <p>Below the root, symbolic links are not followed: a path through one is not found, and listings
 * leave them out, with hidden names and the files of other types. The real path of what is read must
 * be in the real path of the root, which may itself be a symbolic link. Hard links cannot be told
 * apart from files and are read as such.
 *
 * <h3>Bounds</h3>
 * <p>A listing reads at most {@value #MAX_SCAN} entries of a directory and returns at most
 * {@value #MAX_ENTRIES}; the metadata of a document is parsed with the bounds of
 * {@link PsmlDocument}; at most {@value #MAX_SOURCE_BYTES} bytes of source are read.
 *
 * <p>Files can be deleted or replaced at any time: an entry that disappears during a listing is left
 * out, and a document that disappears before it is read is not found.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlRoot {

  /** The extension of the PSML documents. */
  public static final String EXTENSION = ".psml";

  /** Maximum number of characters in a requested relative path. */
  public static final int MAX_PATH_LENGTH = 1024;

  /** Maximum number of names in a requested relative path. */
  public static final int MAX_DEPTH = 32;

  /** The maximum number of directory entries read per listing. */
  public static final int MAX_SCAN = 10_000;

  /** The maximum number of entries per listing. */
  public static final int MAX_ENTRIES = 1000;

  /** Maximum number of bytes read from a document source. */
  public static final int MAX_SOURCE_BYTES = 1024 * 1024;

  /** Folders first, then by name ignoring case. */
  private static final Comparator<PsmlListing.Entry> ORDER = Comparator
      .comparing((PsmlListing.Entry entry) -> !entry.isFolder())
      .thenComparing(PsmlListing.Entry::name, String.CASE_INSENSITIVE_ORDER)
      .thenComparing(PsmlListing.Entry::name);

  private final String name;

  private final Path directory;

  PsmlRoot(String name, Path directory) {
    this.name = name;
    this.directory = directory;
  }

  /**
   * @return the name of the root in the repository.
   */
  public String name() {
    return this.name;
  }

  /**
   * @return the directory, as configured.
   */
  public Path directory() {
    return this.directory;
  }

  /**
   * @return whether the directory of the root exists, as a directory (following a symbolic link).
   */
  public boolean exists() {
    return Files.isDirectory(this.directory);
  }

  /**
   * Lists a folder: its folders, then its documents, by name ignoring case.
   *
   * @param path   The path of the folder.
   * @param offset The index of the first entry to return, from 0.
   * @param limit  The number of entries to return, at most {@value #MAX_ENTRIES}.
   * @return the entries of the folder in this range.
   * @throws PsmlException if the path is invalid, there is no folder at this path or it cannot be read.
   */
  public PsmlListing list(String path, int offset, int limit) throws PsmlException {
    Located folder = locate(path, true);
    List<PsmlListing.Entry> entries = new ArrayList<>();
    boolean truncated = false;
    int scanned = 0;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder.file)) {
      for (Path child : stream) {
        if (scanned++ == MAX_SCAN) {
          truncated = true;
          break;
        }
        PsmlListing.Entry entry = toEntry(folder.path, child);
        if (entry != null) entries.add(entry);
      }
    } catch (NoSuchFileException | NotDirectoryException ex) {
      throw new PsmlException(Problem.NOT_FOUND, path, ex);
    } catch (AccessDeniedException ex) {
      throw new PsmlException(Problem.UNREADABLE, path, ex);
    } catch (DirectoryIteratorException ex) {
      if (ex.getCause() instanceof NoSuchFileException) throw new PsmlException(Problem.NOT_FOUND, path, ex);
      throw new UncheckedIOException(ex.getCause());
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    entries.sort(ORDER);
    int total = entries.size();
    int folders = (int) entries.stream().filter(PsmlListing.Entry::isFolder).count();
    int from = Math.min(Math.max(offset, 0), total);
    int to = Math.min(from + Math.max(1, Math.min(limit, MAX_ENTRIES)), total);
    return new PsmlListing(this.name, folder.path, from, total, folders, truncated, new ArrayList<>(entries.subList(from, to)));
  }

  /**
   * Reads the metadata of a document, parsing it with bounds.
   *
   * @param path The path of the document.
   * @return its metadata; malformed or rejected content is reported in its status.
   * @throws PsmlException if the path is invalid, there is no document at this path or it cannot be read.
   */
  public PsmlDocument document(String path) throws PsmlException {
    Located document = locate(path, false);
    PsmlDocument metadata = new PsmlDocument(this.name, document.path, document.attributes);
    if (document.attributes.size() > PsmlDocument.MAX_BYTES) {
      metadata.status = PsmlDocument.Status.TOO_LARGE;
      return metadata;
    }
    try (InputStream in = Files.newInputStream(document.file, LinkOption.NOFOLLOW_LINKS)) {
      PsmlMetadataReader.read(in, metadata);
    } catch (IOException ex) {
      throw failure(ex, document);
    }
    return metadata;
  }

  /**
   * Reads the source of a document: at most {@value #MAX_SOURCE_BYTES} bytes, decoded as UTF-8.
   *
   * @param path The path of the document.
   * @return its source.
   * @throws PsmlException if the path is invalid, there is no document at this path or it cannot be read.
   */
  public PsmlSource source(String path) throws PsmlException {
    Located document = locate(path, false);
    try (SeekableByteChannel channel = Files.newByteChannel(document.file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      ByteBuffer bytes = ByteBuffer.allocate((int) Math.min(MAX_SOURCE_BYTES, channel.size()));
      while (bytes.hasRemaining() && channel.read(bytes) != -1) {
        // Fill the buffer: the file may be shorter than its size by now
      }
      boolean truncated = !bytes.hasRemaining() && channel.read(ByteBuffer.allocate(1)) > 0;
      bytes.flip();
      return new PsmlSource(this.name, document.path, channel.size(), Utf8.decode(bytes, truncated), truncated);
    } catch (IOException ex) {
      throw failure(ex, document);
    }
  }

  /**
   * @return the entry for a child of a folder, or {@code null} to leave it out.
   */
  private static PsmlListing.@Nullable Entry toEntry(String folder, Path child) {
    String name = child.getFileName().toString();
    if (name.startsWith(".")) return null;
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (IOException ex) {
      // Deleted since it was listed, or its attributes cannot be read
      return null;
    }
    String path = folder.equals("/") ? "/" + name : folder + "/" + name;
    if (attributes.isDirectory()) return new PsmlListing.Entry(name, path, true, attributes);
    if (attributes.isRegularFile() && isDocumentName(name)) return new PsmlListing.Entry(name, path, false, attributes);
    return null;
  }

  private static boolean isDocumentName(String name) {
    return name.length() > EXTENSION.length() && name.endsWith(EXTENSION);
  }

  /**
   * Finds a folder or a document, applying the path policy.
   */
  private Located locate(String path, boolean folder) throws PsmlException {
    List<String> names = names(path);
    if (!folder && names.isEmpty()) throw new PsmlException(Problem.NOT_FOUND, path);
    Path root = realDirectory(path);
    Path file = root;
    BasicFileAttributes attributes;
    try {
      attributes = Files.readAttributes(file, BasicFileAttributes.class);
      for (String segment : names) {
        // Each name is resolved without following links, so a link cannot lead out of the root
        if (!attributes.isDirectory()) throw new PsmlException(Problem.NOT_FOUND, path);
        file = file.resolve(segment);
        attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) throw new PsmlException(Problem.NOT_FOUND, path);
      }
      boolean found = folder ? attributes.isDirectory() : attributes.isRegularFile() && isDocumentName(names.get(names.size() - 1));
      if (!found || !file.toRealPath().startsWith(root)) throw new PsmlException(Problem.NOT_FOUND, path);
    } catch (NoSuchFileException ex) {
      throw new PsmlException(Problem.NOT_FOUND, path, ex);
    } catch (AccessDeniedException ex) {
      throw new PsmlException(Problem.UNREADABLE, path, ex);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return new Located(file, "/" + String.join("/", names), attributes);
  }

  /**
   * @param path The path requested, for the exception.
   * @return the real path of the directory of the root.
   */
  private Path realDirectory(String path) throws PsmlException {
    try {
      return this.directory.toRealPath();
    } catch (NoSuchFileException ex) {
      throw new PsmlException(Problem.NOT_FOUND, path, ex);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * @return the names of a path, after checking it.
   */
  static List<String> names(String path) throws PsmlException {
    if (path.length() > MAX_PATH_LENGTH) throw new PsmlException(Problem.INVALID_PATH, path);
    String relative = path;
    if (relative.startsWith("/")) relative = relative.substring(1);
    if (relative.endsWith("/")) relative = relative.substring(0, relative.length() - 1);
    if (relative.isEmpty()) return List.of();
    List<String> names = Arrays.asList(relative.split("/", -1));
    if (names.size() > MAX_DEPTH) throw new PsmlException(Problem.INVALID_PATH, path);
    for (String name : names) {
      if (!isAllowed(name)) throw new PsmlException(Problem.INVALID_PATH, path);
    }
    return names;
  }

  private static boolean isAllowed(String name) {
    if (name.isEmpty() || name.startsWith(".")) return false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '\\' || c == ':' || Character.isISOControl(c)) return false;
    }
    return true;
  }

  /**
   * @return the exception for a failure to read a located document.
   */
  private static PsmlException failure(IOException ex, Located document) {
    if (ex instanceof NoSuchFileException) return new PsmlException(Problem.NOT_FOUND, document.path, ex);
    if (ex instanceof AccessDeniedException) return new PsmlException(Problem.UNREADABLE, document.path, ex);
    // Replaced by a symbolic link since it was located: not followed
    if (ex instanceof FileSystemException && Files.isSymbolicLink(document.file)) return new PsmlException(Problem.NOT_FOUND, document.path, ex);
    throw new UncheckedIOException(ex);
  }

  /**
   * A file found by its path.
   */
  private static final class Located {

    private final Path file;

    /** The path in canonical form. */
    private final String path;

    private final BasicFileAttributes attributes;

    Located(Path file, String path, BasicFileAttributes attributes) {
      this.file = file;
      this.path = path;
      this.attributes = attributes;
    }
  }

}
