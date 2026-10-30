package org.pageseeder.bzadmin.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A bounded range of the keys of a cache, in the order of the provider: the keys of supported
 * types, and the number of keys of each other type (by class name, never as text). Values are not
 * read.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheEntries {

  /** The maximum number of keys read from a cache per request. */
  public static final int MAX_SCAN = 10_000;

  /** The maximum number of keys returned. */
  public static final int MAX_LIMIT = 1000;

  private final int offset;

  private final List<CacheKey> keys;

  private final Map<String, Integer> unsupported;

  private final int scanned;

  private final boolean more;

  private CacheEntries(int offset, List<CacheKey> keys, Map<String, Integer> unsupported, int scanned, boolean more) {
    this.offset = offset;
    this.keys = keys;
    this.unsupported = unsupported;
    this.scanned = scanned;
    this.more = more;
  }

  /**
   * Reads a range of keys: positions {@code offset} to {@code offset + limit} of the keys, reading
   * at most {@value #MAX_SCAN} keys in all.
   *
   * @param keys   The keys of the cache, read lazily when the provider allows it.
   * @param offset The position of the first key (clamped to 0 – {@value #MAX_SCAN}).
   * @param limit  The number of positions (clamped to 1 – {@value #MAX_LIMIT}).
   * @return the range.
   */
  public static CacheEntries scan(Iterator<?> keys, int offset, int limit) {
    int from = Math.max(0, Math.min(offset, MAX_SCAN));
    int to = Math.min(MAX_SCAN, from + Math.max(1, Math.min(limit, MAX_LIMIT)));
    List<CacheKey> found = new ArrayList<>();
    Map<String, Integer> unsupported = new TreeMap<>();
    int position = 0;
    while (position < to && keys.hasNext()) {
      Object key = keys.next();
      if (position++ < from || key == null) continue;
      CacheKey supported = CacheKey.of(key);
      if (supported != null) found.add(supported);
      else unsupported.merge(key instanceof String ? "java.lang.String (too long)" : key.getClass().getName(), 1, Integer::sum);
    }
    return new CacheEntries(from, Collections.unmodifiableList(found), Collections.unmodifiableMap(unsupported), position, keys.hasNext());
  }

  /**
   * @return the position of the first key.
   */
  public int offset() {
    return this.offset;
  }

  /**
   * @return the keys of supported types in the range.
   */
  public List<CacheKey> keys() {
    return this.keys;
  }

  /**
   * @return the number of keys of each unsupported type in the range, by class name.
   */
  public Map<String, Integer> unsupported() {
    return this.unsupported;
  }

  /**
   * @return the number of keys read, from the first one.
   */
  public int scanned() {
    return this.scanned;
  }

  /**
   * @return {@code true} if the cache has keys after the range.
   */
  public boolean more() {
    return this.more;
  }

}
