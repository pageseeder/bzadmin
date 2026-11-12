package org.pageseeder.bzadmin.berlioz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Typed reads of the fields of a Berlioz diagnostic snapshot, as returned by
 * {@code ConfigurationSnapshot.entries()} and {@code fields()}, such as
 * {@code RedirectFilter.diagnosticSnapshot()}.
 *
 * <p>Berlioz documents the values as a {@code String}, {@code Boolean}, {@code Long},
 * {@code List<String>}, nested map or list of maps, and omits a field without a value. These
 * methods read a field of another type as absent, so that the overlay does not fail on a
 * snapshot from a later Berlioz.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class SnapshotFields {

  private SnapshotFields() {
  }

  /**
   * @param object The fields of a snapshot entry.
   * @param name   The name of a field.
   * @return the value of the field if it is an object, or {@code null}.
   */
  @SuppressWarnings("unchecked")
  public static @Nullable Map<String, Object> object(Map<String, Object> object, String name) {
    Object value = object.get(name);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  /**
   * @param object The fields of a snapshot entry.
   * @param name   The name of a list of objects.
   * @return the objects of the list, empty if there is no such list.
   */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> objects(Map<String, Object> object, String name) {
    Object value = object.get(name);
    if (!(value instanceof List)) return List.of();
    List<Map<String, Object>> objects = new ArrayList<>();
    for (Object item : (List<Object>) value) {
      if (item instanceof Map) objects.add((Map<String, Object>) item);
    }
    return objects;
  }

  /**
   * @return the value of the field if it is text, or {@code null}.
   */
  public static @Nullable String text(Map<String, Object> object, String name) {
    Object value = object.get(name);
    return value instanceof String ? (String) value : null;
  }

  /**
   * @return the value of the field if it is a number, or the default.
   */
  public static long number(Map<String, Object> object, String name, long otherwise) {
    Object value = object.get(name);
    return value instanceof Number ? ((Number) value).longValue() : otherwise;
  }

  /**
   * @return whether the field is {@code true}.
   */
  public static boolean flag(Map<String, Object> object, String name) {
    return Boolean.TRUE.equals(object.get(name));
  }

}
