package org.pageseeder.bzadmin.psml;

/**
 * An expected failure of a {@link PsmlRoot} operation, which a generator reports with a 4xx status.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class PsmlException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Why an operation failed.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Problem {

    /** The path is not allowed (parent or hidden segment, too long or too deep...): 400. */
    INVALID_PATH("invalid-path"),

    /** No folder or document at this path, or a symbolic link, or the root does not exist: 404. */
    NOT_FOUND("not-found"),

    /** The file exists but cannot be read: 403. */
    UNREADABLE("unreadable");

    private final String code;

    Problem(String code) {
      this.code = code;
    }

    /**
     * @return the code for the output.
     */
    public String code() {
      return this.code;
    }
  }

  private final Problem problem;

  private final String path;

  PsmlException(Problem problem, String path) {
    super(problem.code() + ": " + path);
    this.problem = problem;
    this.path = path;
  }

  PsmlException(Problem problem, String path, Throwable cause) {
    super(problem.code() + ": " + path, cause);
    this.problem = problem;
    this.path = path;
  }

  /**
   * @return why the operation failed.
   */
  public Problem problem() {
    return this.problem;
  }

  /**
   * @return the path as requested.
   */
  public String path() {
    return this.path;
  }

}
