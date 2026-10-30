package org.pageseeder.bzadmin.cache;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A property of the configuration of a cache, as text for display.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class CacheProperty {

  /** The maximum length of a value; longer values are cut with "…". */
  public static final int MAX_VALUE_LENGTH = 1024;

  private final String name;

  private final String value;

  private CacheProperty(String name, String value) {
    this.name = name;
    this.value = value;
  }

  /**
   * @param name  The name of the property (the provider's name, for display).
   * @param value Its value, as text; {@code null} for none.
   * @return the property, with the value cut at {@value #MAX_VALUE_LENGTH} characters.
   */
  public static CacheProperty of(String name, @Nullable Object value) {
    Objects.requireNonNull(name, "name");
    String text = value != null ? value.toString() : "";
    if (text.length() > MAX_VALUE_LENGTH) text = text.substring(0, MAX_VALUE_LENGTH - 1) + "…";
    return new CacheProperty(name, text);
  }

  /**
   * @return the name of the property.
   */
  public String name() {
    return this.name;
  }

  /**
   * @return the value, as text; empty for none.
   */
  public String value() {
    return this.value;
  }

}
