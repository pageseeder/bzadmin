package org.pageseeder.bzadmin.psml;

import java.nio.file.attribute.BasicFileAttributes;

import org.jspecify.annotations.Nullable;

/**
 * The metadata of a PSML document: its file attributes, and what a streaming parse finds in it.
 *
 * <p>The parse is bounded: a file larger than {@value #MAX_BYTES} bytes is not parsed, and it stops
 * at a document type declaration (never processed, so no entity is expanded or loaded), deeper than
 * {@value #MAX_XML_DEPTH} elements, or after {@value #MAX_ELEMENTS} elements. Values are cut after
 * {@value #MAX_VALUE_LENGTH} characters. When the parse stops, the {@link #status()} says why, and
 * the values found before are kept.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlDocument {

  /** Maximum document size in bytes accepted for metadata parsing. */
  public static final long MAX_BYTES = 4L * 1024 * 1024;

  /** Maximum XML element nesting depth accepted by the metadata parser. */
  public static final int MAX_XML_DEPTH = 128;

  /** Maximum number of XML elements read while parsing metadata. */
  public static final int MAX_ELEMENTS = 100_000;

  /** Maximum number of characters retained for a metadata value. */
  public static final int MAX_VALUE_LENGTH = 8192;

  /**
   * The result of the parse.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Status {

    /** Parsed to the end. */
    OK("ok"),

    /** Not well-formed: see the message and location. */
    MALFORMED("malformed"),

    /** Larger than {@value PsmlDocument#MAX_BYTES} bytes. */
    TOO_LARGE("too-large"),

    /** Has a document type declaration. */
    DOCTYPE("doctype"),

    /** Deeper than {@value PsmlDocument#MAX_XML_DEPTH} elements. */
    TOO_DEEP("too-deep"),

    /** More than {@value PsmlDocument#MAX_ELEMENTS} elements. */
    TOO_MANY_ELEMENTS("too-many-elements");

    private final String code;

    Status(String code) {
      this.code = code;
    }

    /**
     * @return the code for the output.
     */
    public String code() {
      return this.code;
    }
  }

  private final String root;

  private final String path;

  private final long size;

  private final long modified;

  // Set by the reader

  Status status = Status.OK;

  @Nullable String message;

  int line;

  int column;

  @Nullable String element;

  @Nullable String type;

  @Nullable String level;

  @Nullable String docid;

  @Nullable String title;

  int sections;

  int fragments;

  PsmlDocument(String root, String path, BasicFileAttributes attributes) {
    this.root = root;
    this.path = path;
    this.size = attributes.size();
    this.modified = attributes.lastModifiedTime().toMillis();
  }

  /**
   * @return the name of the root.
   */
  public String root() {
    return this.root;
  }

  /**
   * @return the path of the document in canonical form.
   */
  public String path() {
    return this.path;
  }

  /**
   * @return the file name.
   */
  public String name() {
    return this.path.substring(this.path.lastIndexOf('/') + 1);
  }

  /**
   * @return the size of the file in bytes.
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

  /**
   * @return the result of the parse.
   */
  public Status status() {
    return this.status;
  }

  /**
   * @return the parser's message when {@link Status#MALFORMED}, or {@code null}.
   */
  public @Nullable String message() {
    return this.message;
  }

  /**
   * @return the line of the error when {@link Status#MALFORMED}, or 0 when unknown.
   */
  public int line() {
    return this.line;
  }

  /**
   * @return the column of the error when {@link Status#MALFORMED}, or 0 when unknown.
   */
  public int column() {
    return this.column;
  }

  /**
   * @return the local name of the root element ("document" for PSML), or {@code null}.
   */
  public @Nullable String element() {
    return this.element;
  }

  /**
   * @return the {@code type} of the {@code document} element, or {@code null}.
   */
  public @Nullable String type() {
    return this.type;
  }

  /**
   * @return the {@code level} of the {@code document} element, or {@code null}.
   */
  public @Nullable String level() {
    return this.level;
  }

  /**
   * @return the {@code docid} of {@code documentinfo/uri}, or {@code null}.
   */
  public @Nullable String docid() {
    return this.docid;
  }

  /**
   * @return the text of {@code documentinfo/uri/displaytitle}, or else the {@code title} of
   *         {@code documentinfo/uri}, or {@code null}.
   */
  public @Nullable String title() {
    return this.title;
  }

  /**
   * @return the number of {@code section} elements.
   */
  public int sections() {
    return this.sections;
  }

  /**
   * @return the number of fragments ({@code fragment}, {@code media-fragment}, {@code properties-fragment}
   *         and {@code xref-fragment} elements).
   */
  public int fragments() {
    return this.fragments;
  }

}
