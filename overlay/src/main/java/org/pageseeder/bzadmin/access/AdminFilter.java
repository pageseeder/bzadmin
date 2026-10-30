package org.pageseeder.bzadmin.access;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.GlobalSettings;
import org.pageseeder.berlioz.http.HttpHeaders;
import org.pageseeder.berlioz.security.ControlAuthorization;
import org.pageseeder.berlioz.servlet.ControlParameter;
import org.pageseeder.berlioz.servlet.HttpRequestWrapper;
import org.pageseeder.berlioz.servlet.ReloadScope;
import org.pageseeder.bzadmin.log.LoggingBackends;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the access policy of the admin services, and records its verdict for their generators
 * ({@link AdminGuard}).
 *
 * <p>The host maps it to the URLs where Berlioz serves the admin services ({@code /bzadmin/*}, or
 * {@code /<prefix>/bzadmin/*} when Berlioz is mapped by prefix), after its authentication filter,
 * for {@code REQUEST} and the other dispatcher types it uses for those URLs ({@code FORWARD},
 * {@code ERROR}). It is not registered automatically: a mapping mistake denies the requests.
 *
 * <p>The access comes from the first of these that applies:
 * <ol>
 *   <li>the {@link AdminAccess} request attribute set by the host;</li>
 *   <li>the container roles of the authenticated user: the comma-separated roles in the global
 *     properties {@value #ROLE_VIEW} ({@link AdminPermission#VIEW}) and {@value #ROLE_OPERATE}
 *     ({@link AdminPermission#OPERATE}), not used when unset;</li>
 *   <li>the fallback login, for hosts without authentication: BASIC credentials matching the global
 *     properties {@value #USERNAME} and {@value #PASSWORD} (a password hash, or for local
 *     development the password itself, see {@link AdminPassword}), only
 *     over HTTPS ({@code isSecure()}: behind a proxy, the container must apply the forwarded
 *     headers), or over plain HTTP on this machine only ({@link #isLoopback(HttpServletRequest)}). It grants {@link AdminPermission#OPERATE}. Not used when unset, or when the host set
 *     the {@link AdminAccess} attribute.</li>
 * </ol>
 *
 * <p>For each request, in order:
 * <ol>
 *   <li>without any permission, the request is denied: 401 with a BASIC challenge when the
 *     fallback login applies, 403 otherwise;</li>
 *   <li>a method other than GET and HEAD is an operation: it requires {@link AdminPermission#OPERATE}
 *     (403 otherwise) and must come from a page of the same origin (403 otherwise): header
 *     {@code X-Requested-With: XMLHttpRequest}, and {@code Sec-Fetch-Site} and {@code Origin}
 *     same-origin when present;</li>
 *   <li>a request with a Berlioz control parameter is rejected with 400, unless it is a POST to an
 *     admin service with only {@code berlioz-reload}, once, for the scopes of that service
 *     ({@link #CONTROL_SERVICES}), which is then authorised to use it
 *     ({@link ControlAuthorization#CONTROL_AUTHORIZED_ATTRIBUTE}); the deprecated aliases
 *     ({@code reload-services}, {@code clear-xsl-cache}, {@code reset-etags}) and
 *     {@code berlioz-profile} are always rejected;</li>
 *   <li>the filter sets an {@link AdminVerdict} as request attribute.</li>
 * </ol>
 * A request denied or rejected does not reach Berlioz. Every response has
 * {@code Cache-Control: no-store} and the content security policy {@link #CSP}, added to the
 * host's.
 *
 * <p>Operations, rejected control parameters and failed logins are recorded by the logger
 * {@value #AUDIT_LOGGER}: method, path (no query or parameter), user and status code; the user name
 * of a failed login is not recorded.
 *
 * <p>The filter also holds the lifecycle of the capture of recent log events ({@link LoggingBackends}):
 * it starts when the filter is initialised and stops when it is destroyed.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class AdminFilter implements Filter {

  /** The global property listing the container roles granted {@link AdminPermission#VIEW}. */
  public static final String ROLE_VIEW = "berlioz.admin.role.view";

  /** The global property listing the container roles granted {@link AdminPermission#OPERATE}. */
  public static final String ROLE_OPERATE = "berlioz.admin.role.operate";

  /** The global property with the user name of the fallback login. */
  public static final String USERNAME = "berlioz.admin.username";

  /**
   * The global property with the password of the fallback login: a password hash, or the password
   * itself (see {@link AdminPassword}).
   */
  public static final String PASSWORD = "berlioz.admin.password";

  /** The name of the logger recording operations. */
  public static final String AUDIT_LOGGER = "org.pageseeder.bzadmin.audit";

  /** The realm of the BASIC challenge of the fallback login. */
  public static final String REALM = "Berlioz administration";

  /** The message of a denied request, the same whatever the reason, with no configuration detail. */
  public static final String DENIED = "Access to Berlioz administration is not configured for this request.";

  /** The message of a request without the credentials of the fallback login. */
  public static final String SIGN_IN = "Sign in to use Berlioz administration.";

  /** The message of an operation that does not come from a page of the same origin. */
  public static final String CROSS_SITE = "Berlioz administration operations are only accepted from its own pages.";

  /** The message of a request with a Berlioz control parameter outside its admin service. */
  public static final String CONTROL_REJECTED = "Berlioz control parameters are not accepted on this request.";

  /**
   * The content security policy of the admin services, added to the host's: browsers enforce
   * every policy, so it only narrows the host's.
   */
  public static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; "
      + "base-uri 'none'; frame-ancestors 'none'; form-action 'self'";

  /**
   * The scopes of {@code berlioz-reload} that each admin service using it applies, by Berlioz path;
   * {@code berlioz-reload=true} is every scope.
   */
  static final Map<String, Set<ReloadScope>> CONTROL_SERVICES = Map.of(
      "/bzadmin/config/reload", EnumSet.allOf(ReloadScope.class),
      "/bzadmin/services/reload", EnumSet.of(ReloadScope.SERVICES),
      "/bzadmin/xslt/cache/clear", EnumSet.of(ReloadScope.XSLT));

  /** The headers that proxies add, which the container may not apply. */
  private static final List<String> FORWARDING_HEADERS =
      List.of(HttpHeaders.FORWARDED, HttpHeaders.X_FORWARDED_FOR, HttpHeaders.X_FORWARDED_HOST,
          HttpHeaders.X_FORWARDED_PROTO, HttpHeaders.X_REAL_IP);

  private static final Logger LOGGER = LoggerFactory.getLogger(AdminFilter.class);

  private static final Logger AUDIT = LoggerFactory.getLogger(AUDIT_LOGGER);

  private final FallbackLogin login = new FallbackLogin();

  /** The configuration problems reported, so that each is reported once. */
  private final Set<String> reported = ConcurrentHashMap.newKeySet();

  @Override
  public void init(FilterConfig config) {
    LoggingBackends.start();
  }

  @Override
  public void destroy() {
    LoggingBackends.stop();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest && response instanceof HttpServletResponse)) {
      throw new ServletException("Only HTTP requests are supported");
    }
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = new AdminResponse((HttpServletResponse) response);
    // Never keep the verdict of an earlier dispatch of the request
    req.removeAttribute(AdminVerdict.ATTRIBUTE);
    boolean operation = !("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod()));
    AdminAccess access = authenticate(req, res, operation);
    if (access == null || !isAccepted(req, res, access, operation)) return;
    req.setAttribute(AdminVerdict.ATTRIBUTE, new AdminVerdict(access));
    if (!operation) {
      chain.doFilter(req, res);
      return;
    }
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(req, res);
      status = res.getStatus();
    } finally {
      audit(req, access, status);
    }
  }

  /**
   * Resolves the access of the request, with the fallback login when it applies, and denies the
   * request without any permission.
   *
   * @return the access of the request, or {@code null} when the request was denied.
   */
  private @Nullable AdminAccess authenticate(HttpServletRequest req, HttpServletResponse res, boolean operation)
      throws IOException {
    AdminAccess access = resolve(req);
    if (access != null) return access;
    // Not when the host set the access attribute, even without permission
    Credentials fallback = req.getAttribute(AdminAccess.ATTRIBUTE) instanceof AdminAccess ? null : fallbackLogin();
    if (fallback != null && !req.isSecure() && !isLoopback(req)) {
      report("The fallback login of Berlioz administration is refused over plain HTTP, except from this machine");
    } else if (fallback != null) {
      access = this.login.authenticate(req, fallback.username, fallback.password);
      if (access != null) return access;
      boolean failed = FallbackLogin.hasCredentials(req);
      if (failed && AUDIT.isWarnEnabled()) AUDIT.warn("Login failed: {} {}", req.getMethod(), path(req));
      res.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
      refuse(req, res, null, HttpServletResponse.SC_UNAUTHORIZED, SIGN_IN, operation && !failed);
      return null;
    }
    refuse(req, res, null, HttpServletResponse.SC_FORBIDDEN, DENIED, operation);
    return null;
  }

  /**
   * Checks an operation and the control parameters of the request, and rejects it when they are not
   * accepted; authorises the reload scopes of its admin service.
   *
   * @return whether the request is accepted.
   */
  private static boolean isAccepted(HttpServletRequest req, HttpServletResponse res, AdminAccess access,
      boolean operation) throws IOException {
    if (operation && !access.has(AdminPermission.OPERATE)) {
      refuse(req, res, access, HttpServletResponse.SC_FORBIDDEN, DENIED, true);
      return false;
    }
    if (operation && !isSameOrigin(req)) {
      refuse(req, res, access, HttpServletResponse.SC_FORBIDDEN, CROSS_SITE, true);
      return false;
    }
    if (!hasControlParameter(req)) return true;
    // Only on a POST to its own service, already checked as an operation
    if (!"POST".equals(req.getMethod()) || !hasOnlyScopes(req, CONTROL_SERVICES.get(HttpRequestWrapper.getBerliozPath(req)))) {
      refuse(req, res, access, HttpServletResponse.SC_BAD_REQUEST, CONTROL_REJECTED, true);
      return false;
    }
    req.setAttribute(ControlAuthorization.CONTROL_AUTHORIZED_ATTRIBUTE, Boolean.TRUE);
    return true;
  }

  /**
   * @return the user name and password of the fallback login, or {@code null} when it is not
   *   configured or not valid (reported).
   */
  private @Nullable Credentials fallbackLogin() {
    String username;
    String password;
    try {
      username = GlobalSettings.get(USERNAME);
      password = GlobalSettings.get(PASSWORD);
    } catch (IllegalStateException ex) {
      // Berlioz is not set up: no login is configured
      return null;
    }
    boolean hasUsername = username != null && !username.isEmpty();
    boolean hasPassword = password != null && !password.isEmpty();
    if (!hasUsername && !hasPassword) return null;
    if (!hasUsername || username.indexOf(':') >= 0) {
      report("The fallback login of Berlioz administration needs a user name without ':' (" + USERNAME + ")");
    } else if (!hasPassword) {
      report("The fallback login of Berlioz administration needs a password (" + PASSWORD + ")");
    } else if (AdminPassword.isHash(password) && !AdminPassword.isValid(password)) {
      report("The password of the fallback login of Berlioz administration (" + PASSWORD
          + ") is not a valid PBKDF2 hash, see " + AdminPassword.class.getName());
    } else {
      if (!AdminPassword.isHash(password)) {
        report("The password of the fallback login of Berlioz administration (" + PASSWORD
            + ") is not hashed: use a hash outside local development, see " + AdminPassword.class.getName());
      }
      return new Credentials(username, password);
    }
    return null;
  }

  private void report(String problem) {
    // A few distinct messages only
    if (this.reported.add(problem)) LOGGER.warn(problem);
  }

  /**
   * Rejects the request, and records it when it is an operation or carries control parameters.
   */
  private static void refuse(HttpServletRequest req, HttpServletResponse res, @Nullable AdminAccess access, int status,
      String message, boolean audited) throws IOException {
    reject(req, res, status, message);
    if (audited) audit(req, access, status);
  }

  private static void audit(HttpServletRequest req, @Nullable AdminAccess access, int status) {
    boolean success = status < 400;
    if (success ? !AUDIT.isInfoEnabled() : !AUDIT.isWarnEnabled()) return;
    String path = path(req);
    String user = access != null ? printable(access.name()) : "-";
    if (success) {
      AUDIT.info("{} {} user={} status={}", req.getMethod(), path, user, status);
    } else {
      AUDIT.warn("{} {} user={} status={}", req.getMethod(), path, user, status);
    }
  }

  /**
   * @return the path of the request in the web application, without query.
   */
  private static String path(HttpServletRequest req) {
    String uri = req.getRequestURI();
    String context = req.getContextPath();
    return printable(uri.startsWith(context) ? uri.substring(context.length()) : uri);
  }

  /**
   * @return the text without control characters, so that a log entry is one line.
   */
  private static String printable(String text) {
    StringBuilder out = new StringBuilder(Math.min(text.length(), 200));
    for (int i = 0; i < text.length() && i < 200; i++) {
      char c = text.charAt(i);
      out.append(Character.isISOControl(c) ? '?' : c);
    }
    return out.toString();
  }

  /**
   * @return the access with at least one permission, or {@code null}.
   */
  private static @Nullable AdminAccess resolve(HttpServletRequest req) {
    Object attribute = req.getAttribute(AdminAccess.ATTRIBUTE);
    if (attribute instanceof AdminAccess) {
      AdminAccess access = (AdminAccess) attribute;
      return access.permissions().isEmpty() ? null : access;
    }
    Principal principal = req.getUserPrincipal();
    if (principal == null) return null;
    Set<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
    if (isInAnyRole(req, ROLE_OPERATE)) permissions.add(AdminPermission.OPERATE);
    if (isInAnyRole(req, ROLE_VIEW)) permissions.add(AdminPermission.VIEW);
    if (permissions.isEmpty()) return null;
    return AdminAccess.of(principal.getName(), permissions.toArray(new AdminPermission[0]));
  }

  private static boolean isInAnyRole(HttpServletRequest req, String property) {
    String roles;
    try {
      roles = GlobalSettings.get(property);
    } catch (IllegalStateException ex) {
      // Berlioz is not set up: no role is configured
      return false;
    }
    if (roles == null) return false;
    for (String role : roles.split(",")) {
      String name = role.trim();
      if (!name.isEmpty() && req.isUserInRole(name)) return true;
    }
    return false;
  }

  /**
   * A request from this machine, not through a proxy, so plain HTTP does not expose credentials:
   * the client and the server addresses are loopback addresses, the host requested is
   * {@code localhost} or a loopback address, and there is no forwarding header (a proxy on this
   * machine also connects from a loopback address).
   *
   * @return whether the request comes from this machine directly.
   */
  static boolean isLoopback(HttpServletRequest req) {
    for (String header : FORWARDING_HEADERS) {
      if (req.getHeader(header) != null) return false;
    }
    return isLoopbackLiteral(req.getRemoteAddr()) && isLoopbackLiteral(req.getLocalAddr())
        && ("localhost".equalsIgnoreCase(req.getServerName()) || isLoopbackLiteral(req.getServerName()));
  }

  /**
   * @return whether the text is a numeric loopback address: {@code 127.0.0.0/8} or {@code ::1}
   *   (never resolved as a name).
   */
  static boolean isLoopbackLiteral(@Nullable String address) {
    if (address == null || address.isEmpty()) return false;
    String literal = address.startsWith("[") && address.endsWith("]") ? address.substring(1, address.length() - 1) : address;
    if (literal.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
      String[] octets = literal.split("\\.");
      for (String octet : octets) {
        if (Integer.parseInt(octet) > 255) return false;
      }
      return "127".equals(octets[0]);
    }
    // An IPv6 literal (with a colon) is parsed, not looked up
    if (literal.indexOf(':') < 0 || !literal.matches("[0-9A-Fa-f:.]+(%[0-9A-Za-z]+)?")) return false;
    try {
      return InetAddress.getByName(literal).isLoopbackAddress();
    } catch (UnknownHostException ex) {
      return false;
    }
  }

  /**
   * Stateless CSRF check: cross-site pages cannot send a custom header without a CORS preflight,
   * and browsers send {@code Sec-Fetch-Site} and {@code Origin} with script requests.
   *
   * @return whether the request comes from a page of the same origin.
   */
  static boolean isSameOrigin(HttpServletRequest req) {
    if (!"XMLHttpRequest".equals(req.getHeader("X-Requested-With"))) return false;
    String site = req.getHeader("Sec-Fetch-Site");
    if (site != null && !"same-origin".equals(site)) return false;
    String origin = req.getHeader("Origin");
    return origin == null || origin.equalsIgnoreCase(origin(req));
  }

  /**
   * @return the origin of the request, as the container sees it (behind a proxy, the container
   *   must apply the forwarded headers).
   */
  private static String origin(HttpServletRequest req) {
    String scheme = req.getScheme().toLowerCase(Locale.ROOT);
    int port = req.getServerPort();
    boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    return scheme + "://" + req.getServerName() + (defaultPort || port <= 0 ? "" : ":" + port);
  }

  /**
   * @return whether the request has a Berlioz control parameter, whatever its value.
   */
  private static boolean hasControlParameter(HttpServletRequest req) {
    for (String name : req.getParameterMap().keySet()) {
      if (ControlParameter.isControlParameter(name)) return true;
    }
    return false;
  }

  /**
   * @return whether the only control parameter of the request is {@code berlioz-reload}, with a
   *   single value for exactly the scopes allowed.
   */
  private static boolean hasOnlyScopes(HttpServletRequest req, @Nullable Set<ReloadScope> allowed) {
    if (allowed == null) return false;
    String reload = ControlParameter.BERLIOZ_RELOAD.parameter();
    for (String name : req.getParameterMap().keySet()) {
      if (!name.equals(reload) && ControlParameter.isControlParameter(name)) return false;
    }
    String[] values = req.getParameterValues(reload);
    if (values == null || values.length != 1) return false;
    try {
      return ReloadScope.parse(values[0]).equals(allowed);
    } catch (IllegalArgumentException ex) {
      // An unknown scope, which Berlioz would ignore
      return false;
    }
  }

  private static void reject(HttpServletRequest req, HttpServletResponse res, int status, String message) throws IOException {
    if (res.isCommitted()) return;
    res.resetBuffer();
    res.setStatus(status);
    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
    boolean html = req.getRequestURI().endsWith(".html");
    res.setContentType(html ? "text/html" : "text/plain");
    if ("HEAD".equals(req.getMethod())) return;
    PrintWriter out = res.getWriter();
    if (html) {
      out.print("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"/>"
          + "<title>Berlioz administration</title></head><body><p>" + message + "</p></body></html>");
    } else {
      out.print(message);
    }
    out.flush();
  }

  /** The user name and password of the fallback login. */
  private static final class Credentials {

    final String username;

    final String password;

    Credentials(String username, String password) {
      this.username = username;
      this.password = password;
    }

  }

  /**
   * Keeps {@code Cache-Control: no-store} and the policy {@link #CSP} on the response, including
   * after a reset (Berlioz resets the response for errors and redirects).
   */
  private static final class AdminResponse extends HttpServletResponseWrapper {

    AdminResponse(HttpServletResponse res) {
      super(res);
      secure();
    }

    private void secure() {
      super.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      super.addHeader("Content-Security-Policy", CSP);
    }

    @Override
    public void setHeader(String name, String value) {
      if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name)) return;
      super.setHeader(name, value);
      // A policy set after this filter replaces the earlier ones
      if ("Content-Security-Policy".equalsIgnoreCase(name)) super.addHeader(name, CSP);
    }

    @Override
    public void addHeader(String name, String value) {
      if (HttpHeaders.CACHE_CONTROL.equalsIgnoreCase(name)) return;
      super.addHeader(name, value);
    }

    @Override
    public void reset() {
      super.reset();
      secure();
    }

  }

}
