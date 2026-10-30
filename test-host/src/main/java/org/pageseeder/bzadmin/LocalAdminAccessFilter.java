package org.pageseeder.bzadmin;

import java.io.IOException;
import java.net.InetAddress;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.pageseeder.bzadmin.access.AdminAccess;
import org.pageseeder.bzadmin.access.AdminPermission;

/**
 * Grants every admin permission to requests from this machine, for local development only: the
 * host's part of the access contract, which a real host does after authenticating an administrator.
 */
public final class LocalAdminAccessFilter implements Filter {

  /** Creates the filter that grants local development access. */
  public LocalAdminAccessFilter() {
  }
  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    // The remote address is numeric: no lookup
    if (InetAddress.getByName(req.getRemoteAddr()).isLoopbackAddress()) {
      req.setAttribute(AdminAccess.ATTRIBUTE, AdminAccess.of("local", AdminPermission.OPERATE));
    }
    chain.doFilter(req, res);
  }

}
