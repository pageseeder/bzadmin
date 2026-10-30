package org.pageseeder.bzadmin;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The host integrations of the admin filter that the policy tests run against, each with its web
 * descriptor next to this class (the installation examples of {@code docs/access.md}).
 *
 * <p>Tests name users as {@code FixtureAuthentication} does ({@code operator}, {@code viewer},
 * {@code role-operator}, {@code role-viewer}, {@code user}, any other name for an unknown user,
 * {@code null} for anonymous); each example identifies them in its own way.
 */
enum HostExample {

  /**
   * The host's own authentication filter sets {@code AdminAccess} ({@code FixtureAuthentication}),
   * over plain HTTP.
   */
  FILTER("/", "web.xml", "config-test.xml",
      List.of("viewer", "role-viewer"), List.of("operator", "role-operator")),

  /**
   * Container BASIC authentication over HTTPS (a security constraint with {@code CONFIDENTIAL}),
   * administrators by container role ({@code berlioz.admin.role.*}).
   */
  CONTAINER("/", "web-container.xml", "config-test.xml",
      List.of("viewer", "role-viewer"), List.of("operator", "role-operator")),

  /**
   * The fallback login of the admin filter ({@code berlioz.admin.username}, {@code operator}),
   * behind a TLS-terminating proxy that forwards the scheme and host.
   */
  FALLBACK("/", "web-fallback.xml", "config-test-fallback.xml",
      List.of(), List.of("operator"));

  /** The prefix of the password of each user of the container realm and of the fallback login. */
  static final String PASSWORD_PREFIX = "fixture-secret-password-";

  /** The origin of the admin pages behind the proxy of {@link #FALLBACK}. */
  static final String PROXY_ORIGIN = "https://admin.example.org";

  private final String contextPath;

  private final String descriptor;

  private final String config;

  private final List<String> viewers;

  private final List<String> operators;

  HostExample(String contextPath, String descriptor, String config, List<String> viewers, List<String> operators) {
    this.contextPath = contextPath;
    this.descriptor = descriptor;
    this.config = config;
    this.viewers = viewers;
    this.operators = operators;
  }

  String contextPath() {
    return this.contextPath;
  }

  String descriptor() {
    return this.descriptor;
  }

  /**
   * @return the global configuration of the host, in {@code WEB-INF/config}.
   */
  String config() {
    return this.config;
  }

  /**
   * @return whether the host has an HTTPS connector (plus a plain one, see {@link BerliozHost#plain()}).
   */
  boolean tls() {
    return this == CONTAINER;
  }

  /**
   * @return whether requests go through the TLS-terminating proxy (see {@link BerliozHost#direct()}).
   */
  boolean proxied() {
    return this == FALLBACK;
  }

  /**
   * @return the users with only the permission to view.
   */
  List<String> viewers() {
    return this.viewers;
  }

  /**
   * @return the users with the permission to operate.
   */
  List<String> operators() {
    return this.operators;
  }

  /**
   * @param user The user, {@code null} for anonymous.
   * @return the header identifying the user, or {@code null} for none.
   */
  String @Nullable [] credentials(@Nullable String user) {
    if (user == null) return null;
    if (this == FILTER) return new String[]{"X-Fixture-User", user};
    return new String[]{"Authorization", basic(user)};
  }

  /**
   * @return the BASIC credentials of the user, with their password.
   */
  static String basic(String user) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + PASSWORD_PREFIX + user).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @param user A user who is not an administrator, or {@code null} for anonymous.
   * @return the status of their admin requests: 403 from the filter, or 401 (a BASIC challenge)
   *   from the container or the fallback login.
   */
  int deniedStatus(@Nullable String user) {
    switch (this) {
      case FILTER:
        return 403;
      case CONTAINER:
        // Authenticated users reach the filter; the container challenges the others
        return "user".equals(user) ? 403 : 401;
      default:
        return 401;
    }
  }

  /**
   * @param user A user who is not an administrator, or {@code null} for anonymous.
   * @return whether the admin filter answers their admin requests (not the container).
   */
  boolean deniedByFilter(@Nullable String user) {
    return this != CONTAINER || "user".equals(user);
  }

  /**
   * @return whether the Berlioz control key can be sent with the requests of administrators: not
   *   with BASIC credentials, which use the same header.
   */
  boolean sendsControlKey() {
    return this == FILTER;
  }

}
