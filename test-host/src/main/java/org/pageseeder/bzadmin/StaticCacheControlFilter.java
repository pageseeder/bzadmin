package org.pageseeder.bzadmin;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * Sets the HTTP caching of static files (the versioned bundles, fonts and images of the overlay),
 * in place of Bastille's {@code StaticCachingFilter}: the servlet container serves the files with
 * their validators, so the files are not cached in memory or compressed by the application.
 *
 * <p>The {@code cache-control} init parameter is the value of the {@code Cache-Control} header.
 */
public final class StaticCacheControlFilter implements Filter {

  /** Creates the static-resource cache control filter. */
  public StaticCacheControlFilter() {
  }

  private String cacheControl = "max-age=86400";

  @Override
  public void init(FilterConfig config) {
    String value = config.getInitParameter("cache-control");
    if (value != null && !value.isBlank()) this.cacheControl = value;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    if (res instanceof HttpServletResponse) {
      ((HttpServletResponse) res).setHeader("Cache-Control", this.cacheControl);
    }
    chain.doFilter(req, res);
  }

}
