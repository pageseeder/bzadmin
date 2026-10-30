package org.pageseeder.bzadmin.fixture;

import java.io.IOException;
import java.security.Principal;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.pageseeder.bzadmin.access.AdminAccess;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * The authentication of the test host (never published): the user named by the {@code X-Fixture-User}
 * header, as a host's authentication filter would identify them.
 *
 * <ul>
 *   <li>{@code operator}, {@code viewer}: administrators, with {@link AdminAccess} set by the host;</li>
 *   <li>{@code role-operator}, {@code role-viewer}: users in the container roles
 *     {@code fixture-admin-operate} and {@code fixture-admin-view} (see {@code config-test.xml});</li>
 *   <li>{@code user}: an authenticated user who is not an administrator;</li>
 *   <li>{@code revoked}: in the container role {@code fixture-admin-operate}, but with an
 *     {@link AdminAccess} without permission, which the admin filter takes as the only source;</li>
 *   <li>none or any other value: anonymous.</li>
 * </ul>
 */
public final class FixtureAuthentication implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    String user = req.getHeader("X-Fixture-User");
    if (user == null) {
      chain.doFilter(req, response);
      return;
    }
    switch (user) {
      case "operator":
        req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of(user, AdminPermission.OPERATE));
        chain.doFilter(new Authenticated(req, user, Set.of()), response);
        break;
      case "viewer":
        req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of(user, AdminPermission.VIEW));
        chain.doFilter(new Authenticated(req, user, Set.of()), response);
        break;
      case "role-operator":
        chain.doFilter(new Authenticated(req, user, Set.of("fixture-admin-operate")), response);
        break;
      case "role-viewer":
        chain.doFilter(new Authenticated(req, user, Set.of("fixture-admin-view")), response);
        break;
      case "revoked":
        req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of(user));
        chain.doFilter(new Authenticated(req, user, Set.of("fixture-admin-operate")), response);
        break;
      case "user":
        chain.doFilter(new Authenticated(req, user, Set.of("fixture-user")), response);
        break;
      default:
        chain.doFilter(req, response);
    }
  }

  private static final class Authenticated extends HttpServletRequestWrapper {

    private final Principal principal;

    private final Set<String> roles;

    Authenticated(HttpServletRequest req, String name, Set<String> roles) {
      super(req);
      this.principal = () -> name;
      this.roles = roles;
    }

    @Override
    public Principal getUserPrincipal() {
      return this.principal;
    }

    @Override
    public String getRemoteUser() {
      return this.principal.getName();
    }

    @Override
    public boolean isUserInRole(String role) {
      return this.roles.contains(role);
    }

  }

}
