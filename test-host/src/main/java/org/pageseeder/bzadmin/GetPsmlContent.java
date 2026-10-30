package org.pageseeder.bzadmin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import org.pageseeder.berlioz.content.ContentStatus;
import org.pageseeder.berlioz.content.Request;
import org.pageseeder.berlioz.content.Response;
import org.pageseeder.berlioz.content.XmlGenerator;
import org.pageseeder.berlioz.xml.XmlWriter;

import org.jspecify.annotations.Nullable;

/**
 * Returns a PSML document of the application, through the {@code PSML} cache of the default
 * EHCache 2 manager ({@link DevCacheManagers}), so that the cache pages have an application cache
 * in use: the key is the path of the document, the value its source.
 *
 * <p>The {@code path} parameter is the path of the document in {@code WEB-INF/psml}, set by the
 * service: the document is written as it is (without its XML declaration), so only for the
 * application's own PSML. An entry older than the file is replaced, so edits show on the next
 * request. Without EHCache (the test hosts without cache library) or the cache, the document is
 * read each time.
 * Local development only.
 *
 * <pre>{@code
 * <psml-content path="/content/home.psml" cache="hit" cached="2026-10-26T01:02:03Z">
 *   <document>...</document>
 * </psml-content>
 * }</pre>
 *
 * <p>{@code cache} is {@code hit} when the document came from the cache, {@code miss} when it was
 * read and stored, {@code none} without cache (and no {@code cached}).
 */
public final class GetPsmlContent implements XmlGenerator {

  /** Creates the development-host PSML content generator. */
  public GetPsmlContent() {
  }

  /** The cache of the default manager (in {@code ehcache.xml}). */
  public static final String CACHE = "PSML";

  private static final boolean EHCACHE = isPresent("net.sf.ehcache.CacheManager");

  @Override
  public Response generate(Request req, XmlWriter xml) {
    String path = req.getParameter("path", "");
    Path base = req.getEnvironment().getPrivateFile("psml").toPath().toAbsolutePath().normalize();
    Path file = base.resolve(path.replaceFirst("^/+", "")).normalize();
    if (!file.startsWith(base) || !Files.isRegularFile(file)) return Response.status(ContentStatus.NOT_FOUND);
    Content content = EHCACHE ? PsmlCache.get(path, file) : new Content(read(file), "none", null);
    xml.openElement("psml-content");
    xml.attribute("path", path);
    xml.attribute("cache", content.cache);
    if (content.cached != null) xml.attribute("cached", content.cached.toString());
    xml.xml(withoutDeclaration(content.source));
    xml.closeElement();
    return Response.ok();
  }

  static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static String withoutDeclaration(String source) {
    if (!source.startsWith("<?xml")) return source;
    int end = source.indexOf("?>");
    return end < 0 ? source : source.substring(end + 2);
  }

  private static boolean isPresent(String name) {
    try {
      Class.forName(name, false, GetPsmlContent.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

  /**
   * The source of a document, how it was obtained, and when it was stored in the cache.
   */
  private static final class Content {

    final String source;

    final String cache;

    final @Nullable Instant cached;

    Content(String source, String cache, @Nullable Instant cached) {
      this.source = source;
      this.cache = cache;
      this.cached = cached;
    }
  }

  /**
   * The PSML cache: only loaded with EHCache.
   */
  private static final class PsmlCache {

    static Content get(String path, Path file) {
      Ehcache cache = CacheManager.create().getEhcache(CACHE);
      if (cache == null) return new Content(read(file), "none", null);
      Element element = cache.get(path);
      boolean hit = element != null && element.getLatestOfCreationAndUpdateTime() >= modified(file);
      if (!hit) {
        element = new Element(path, read(file));
        cache.put(element);
      }
      Instant cached = Instant.ofEpochMilli(element.getLatestOfCreationAndUpdateTime());
      return new Content((String) element.getObjectValue(), hit ? "hit" : "miss", cached);
    }

    private static long modified(Path file) {
      try {
        return Files.getLastModifiedTime(file).toMillis();
      } catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

}
