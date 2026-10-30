package org.pageseeder.bzadmin.psml;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

/**
 * A range of the entries of a folder, folders first then by name ignoring case.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlListing {

  private final String root;

  private final String path;

  private final int offset;

  private final int total;

  private final int folders;

  private final boolean truncated;

  private final List<Entry> entries;

  PsmlListing(String root, String path, int offset, int total, int folders, boolean truncated, List<Entry> entries) {
    this.root = root;
    this.path = path;
    this.offset = offset;
    this.total = total;
    this.folders = folders;
    this.truncated = truncated;
    this.entries = Collections.unmodifiableList(entries);
  }

  /**
   * @return the name of the root.
   */
  public String root() {
    return this.root;
  }

  /**
   * @return the path of the folder in canonical form ("/" for the root, "/content" for a folder).
   */
  public String path() {
    return this.path;
  }

  /**
   * @return the index of the first entry, from 0.
   */
  public int offset() {
    return this.offset;
  }

  /**
   * @return the number of entries of the folder, of those read when {@link #truncated()}.
   */
  public int total() {
    return this.total;
  }

  /**
   * @return the number of folders among the entries of the folder (they come first), of those read
   *         when {@link #truncated()}.
   */
  public int folders() {
    return this.folders;
  }

  /**
   * @return whether the directory has more entries than {@value PsmlRoot#MAX_SCAN}, which were not read:
   *         the order and the total then only apply to those read.
   */
  public boolean truncated() {
    return this.truncated;
  }

  /**
   * @return the entries in this range.
   */
  public List<Entry> entries() {
    return this.entries;
  }

  /**
   * A folder or a document of a folder.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public static final class Entry {

    private final String name;

    private final String path;

    private final boolean folder;

    private final long size;

    private final long modified;

    Entry(String name, String path, boolean folder, BasicFileAttributes attributes) {
      this.name = name;
      this.path = path;
      this.folder = folder;
      this.size = attributes.size();
      this.modified = attributes.lastModifiedTime().toMillis();
    }

    /**
     * @return the file name.
     */
    public String name() {
      return this.name;
    }

    /**
     * @return its path in the root, in canonical form.
     */
    public String path() {
      return this.path;
    }

    /**
     * @return {@code true} for a folder, {@code false} for a document.
     */
    public boolean isFolder() {
      return this.folder;
    }

    /**
     * @return the size in bytes, as reported by the file system for a folder.
     */
    public long size() {
      return this.size;
    }

    /**
     * @return the time of the last modification, in milliseconds since the epoch.
     */
    public long modified() {
      return this.modified;
    }
  }

}
