package org.pageseeder.bzadmin.cache;

import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The key of a cache entry, of a type that the admin pages can show and send back exactly: text,
 * an integer or a long. Keys of other types are never shown as text ({@code toString()} is not
 * reversible) and cannot be named in a request; no key is ever deserialized.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheKey {

  /** The maximum length of a text key that can be shown and removed. */
  public static final int MAX_LENGTH = 1024;

  /**
   * The types of key supported.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Type {

    /** {@link String}. */
    STRING,

    /** {@link Integer}. */
    INTEGER,

    /** {@link Long}. */
    LONG;

    /**
     * @return the name in the output and requests: lower case.
     */
    @Override
    public String toString() {
      return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param name The name of the type, as {@link #toString()} returns it.
     * @return the type, or {@code null} when not supported.
     */
    public static @Nullable Type forName(@Nullable String name) {
      for (Type type : values()) {
        if (type.toString().equals(name)) return type;
      }
      return null;
    }
  }

  private final Type type;

  private final Object value;

  private CacheKey(Type type, Object value) {
    this.type = type;
    this.value = value;
  }

  /**
   * @param key A key of a cache.
   * @return the key, or {@code null} when its type is not supported or it is longer than
   *   {@value #MAX_LENGTH} characters.
   */
  public static @Nullable CacheKey of(@Nullable Object key) {
    if (key instanceof String) return ((String) key).length() <= MAX_LENGTH ? new CacheKey(Type.STRING, key) : null;
    if (key instanceof Integer) return new CacheKey(Type.INTEGER, key);
    if (key instanceof Long) return new CacheKey(Type.LONG, key);
    return null;
  }

  /**
   * Parses a key from a request.
   *
   * @param type The type of the key ({@code string}, {@code integer} or {@code long}).
   * @param text The key as text.
   * @return the key.
   * @throws IllegalArgumentException if the type is not supported or the text is not a key of
   *   that type.
   */
  public static CacheKey parse(String type, String text) {
    Objects.requireNonNull(text, "text");
    Type found = Type.forName(type);
    if (found == null) throw new IllegalArgumentException("Unsupported key type");
    if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("Key too long");
    switch (found) {
      case INTEGER:
        return new CacheKey(found, Integer.valueOf(text));
      case LONG:
        return new CacheKey(found, Long.valueOf(text));
      default:
        return new CacheKey(found, text);
    }
  }

  /**
   * @return the type of the key.
   */
  public Type type() {
    return this.type;
  }

  /**
   * @return the key itself: a {@link String}, {@link Integer} or {@link Long}.
   */
  public Object value() {
    return this.value;
  }

  /**
   * @return the key as text, from which {@link #parse(String, String)} returns the same key.
   */
  public String text() {
    return this.value.toString();
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof CacheKey)) return false;
    return this.value.equals(((CacheKey) o).value);
  }

  @Override
  public int hashCode() {
    return this.value.hashCode();
  }

}
