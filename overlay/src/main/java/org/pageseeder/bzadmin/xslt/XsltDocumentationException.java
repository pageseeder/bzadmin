package org.pageseeder.bzadmin.xslt;

import javax.xml.transform.TransformerException;

/**
 * An expected reason why a stylesheet cannot be documented, with a message that can be shown to
 * the administrator.
 *
 * <p>It is thrown by the resolver while the documentation stylesheet runs, so it reaches the
 * caller as the cause of the processor's exception.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class XsltDocumentationException extends TransformerException {

  private static final long serialVersionUID = 1L;

  /**
   * Why the documentation failed.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Type {

    /** A referenced stylesheet does not exist. */
    NOT_FOUND,

    /** A reference is outside what the documentation may read. */
    DENIED,

    /** A stylesheet is not well-formed XML. */
    MALFORMED,

    /** The stylesheets exceed the documentation limits. */
    LIMIT;

    /**
     * @return the type as an XML attribute value, e.g. "not-found".
     */
    public String code() {
      return name().toLowerCase().replace('_', '-');
    }
  }

  private final Type type;

  XsltDocumentationException(Type type, String message) {
    super(message);
    this.type = type;
  }

  XsltDocumentationException(Type type, String message, Throwable cause) {
    super(message, cause);
    this.type = type;
  }

  /**
   * @return why the documentation failed.
   */
  public Type type() {
    return this.type;
  }

}
