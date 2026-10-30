package org.pageseeder.bzadmin;

import org.pageseeder.berlioz.security.ContentSecurityPolicy;
import org.pageseeder.berlioz.security.NonceFactory;
import org.pageseeder.berlioz.security.Directive;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Injects security headers into Berlioz response
 */
public final class SecurityHeadersFilter implements Filter {

  /** Creates the development-host security headers filter. */
  public SecurityHeadersFilter() {
  }

  private static final String SELF = "'self'";

  private static final String NONE = "'none'";

  private final NonceFactory nonceFactory = new NonceFactory();

  // A strict host policy: no inline style, and Trusted Types with no policy (bzadmin has no sink)
  private static final ContentSecurityPolicy DEFAULT_CSP = new ContentSecurityPolicy.Builder()
      .set(Directive.DEFAULT_SRC, SELF)
      .set(Directive.FRAME_ANCESTORS, SELF)
      .set(Directive.OBJECT_SRC, NONE)
      .set(Directive.BASE_URI, NONE)
      .set(Directive.SCRIPT_SRC, SELF + " 'strict-dynamic'")
      .set(Directive.STYLE_SRC, SELF)
      .set(Directive.REQUIRE_TRUSTED_TYPES_FOR, "'script'")
      .set(Directive.TRUSTED_TYPES, NONE)
      .build();

  // The webpack dev server client (overlay, hot reload) is not checked with Trusted Types
  private static final ContentSecurityPolicy DEFAULT_CSP_DEV = new ContentSecurityPolicy.Builder()
      .set(Directive.DEFAULT_SRC, SELF)
      .set(Directive.FRAME_ANCESTORS, SELF)
      .set(Directive.OBJECT_SRC, NONE)
      .set(Directive.BASE_URI, NONE)
      .set(Directive.SCRIPT_SRC, SELF + " 'strict-dynamic'")
      .set(Directive.STYLE_SRC, SELF)
      .set(Directive.CONNECT_SRC, SELF + " wss://localhost:9000")
      .build();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws ServletException, IOException {
    this.doHttpFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
  }

  private void doHttpFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    // html only
    String path = req.getRequestURI();
    if (path.endsWith(".html") || path.endsWith(".xml")) {
      String csp = getContentSecurityPolicy(req);
      res.setHeader("Content-Security-Policy", csp);
      res.setHeader("Permissions-Policy", "microphone=(),camera=(),fullscreen=(self),payment=()");
      res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
      res.setHeader("X-Frame-Options", "DENY"); // cannot be set with <meta>
    }
    // All media types
    res.setHeader("X-Content-Type-Options", "nosniff");
    chain.doFilter(req, res);
  }

  private String getContentSecurityPolicy(HttpServletRequest req) {
    String nonce = this.nonceFactory.generate();
    boolean isDev = req.getServerPort() == 9000;
    req.setAttribute("berlioz-nonce", nonce);
    return getBaseCSP(isDev).withNonce(Directive.SCRIPT_SRC, nonce).toString();
  }

  private static ContentSecurityPolicy getBaseCSP(boolean isDev) {
    return isDev ? DEFAULT_CSP_DEV : DEFAULT_CSP;
  }
}
