package org.pageseeder.bzadmin.psml;

/**
 * The source of a document, as text: at most {@value PsmlRoot#MAX_SOURCE_BYTES} bytes, decoded as
 * UTF-8 with malformed input replaced. It is not escaped.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlSource {

  private final String root;

  private final String path;

  private final long size;

  private final String text;

  private final boolean truncated;

  PsmlSource(String root, String path, long size, String text, boolean truncated) {
    this.root = root;
    this.path = path;
    this.size = size;
    this.text = text;
    this.truncated = truncated;
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
   * @return the size of the file in bytes, when it was read.
   */
  public long size() {
    return this.size;
  }

  /**
   * @return the text read.
   */
  public String text() {
    return this.text;
  }

  /**
   * @return whether the file has more bytes than those read.
   */
  public boolean truncated() {
    return this.truncated;
  }

}
