package org.pageseeder.bzadmin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.jspecify.annotations.Nullable;

/**
 * Serves static files from memory, so that the cache pages have caches in use: the same file is
 * looked up and stored in a cache of each library ({@link DevCacheManagers}), so their statistics
 * and configuration can be compared for the same requests.
 *
 * <p>The key is the path of the file without the context path. A file is served from the first
 * cache that has it, and stored in those that do not; otherwise, it is read from the web
 * application. Only for {@code GET} requests without range, and files up to 1 MiB; the response
 * has no validators ({@code ETag}, {@code Last-Modified}), so conditional requests get the file.
 * Local development only.
 */
public final class DevStaticCacheFilter implements Filter {

  /** Creates the development filter that serves static resources through host caches. */
  public DevStaticCacheFilter() {
  }

  /** The largest file kept in the caches. */
  private static final int MAX_SIZE = 1024 * 1024;

  private ServletContext context;

  @Override
  public void init(FilterConfig config) {
    this.context = config.getServletContext();
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest http = (HttpServletRequest) req;
    List<Store> stores = DevCacheManagers.staticStores(this.context);
    if (!"GET".equals(http.getMethod()) || http.getHeader("Range") != null || stores.isEmpty()) {
      chain.doFilter(req, res);
      return;
    }
    String path = http.getRequestURI().substring(http.getContextPath().length());
    byte[] data = null;
    for (Store store : stores) {
      byte[] cached = store.get(path);
      if (data == null) data = cached;
    }
    if (data == null) {
      data = read(path);
      if (data.length == 0) {
        chain.doFilter(req, res);
        return;
      }
    }
    // Every cache sees the same lookups and puts: each stores what it did not have
    for (Store store : stores) {
      if (!store.contains(path)) store.put(path, data);
    }
    String type = this.context.getMimeType(path);
    res.setContentType(type != null ? type : "application/octet-stream");
    res.setContentLength(data.length);
    res.getOutputStream().write(data);
  }

  /**
   * @return the file of the web application at that path, or an empty array if there is none, it
   *   is empty or it is too large (the container serves it).
   */
  private byte[] read(String path) throws IOException {
    try (InputStream in = this.context.getResourceAsStream(path)) {
      if (in == null) return new byte[0];
      byte[] data = in.readNBytes(MAX_SIZE + 1);
      return data.length <= MAX_SIZE ? data : new byte[0];
    }
  }

  /**
   * A cache of static files: the path of a file and its content.
   */
  interface Store {

    byte @Nullable [] get(String path);

    /** Without access: {@code isKeyInCache} or {@code containsKey}. */
    boolean contains(String path);

    void put(String path, byte[] data);

    static Store of(Ehcache cache) {
      return new Store() {
        @Override
        public byte @Nullable [] get(String path) {
          Element element = cache.get(path);
          return element != null ? (byte[]) element.getObjectValue() : null;
        }

        @Override
        public boolean contains(String path) {
          return cache.isKeyInCache(path);
        }

        @Override
        public void put(String path, byte[] data) {
          cache.put(new Element(path, data));
        }
      };
    }

    static Store of(javax.cache.Cache<String, byte[]> cache) {
      return new Store() {
        @Override
        public byte @Nullable [] get(String path) {
          return cache.get(path);
        }

        @Override
        public boolean contains(String path) {
          return cache.containsKey(path);
        }

        @Override
        public void put(String path, byte[] data) {
          cache.put(path, data);
        }
      };
    }
  }

}
