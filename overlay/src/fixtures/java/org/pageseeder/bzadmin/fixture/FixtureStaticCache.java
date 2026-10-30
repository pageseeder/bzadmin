package org.pageseeder.bzadmin.fixture;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * A filter of the test host (never published) that records the static files it serves in the
 * {@code static-web-caching} cache of the default EHCache 2 manager, under the key
 * {@code <method>_<path>} (the path without the context path), as the static caching filter of a
 * host would: the operation tests remove that entry and check that it is gone.
 */
public final class FixtureStaticCache implements Filter {

  /** The cache of the default manager (in {@code ehcache.xml}). */
  public static final String CACHE = "static-web-caching";

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    chain.doFilter(req, res);
    HttpServletRequest http = (HttpServletRequest) req;
    if ("GET".equals(http.getMethod()) && ((HttpServletResponse) res).getStatus() == HttpServletResponse.SC_OK) {
      String path = http.getRequestURI().substring(http.getContextPath().length());
      CacheManager.create().getEhcache(CACHE).put(new Element("GET_" + path, path));
    }
  }

}
