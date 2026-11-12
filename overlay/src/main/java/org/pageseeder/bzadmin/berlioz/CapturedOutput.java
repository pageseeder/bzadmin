package org.pageseeder.bzadmin.berlioz;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.output.OutputType;
import org.pageseeder.berlioz.output.OutputWriter;

/**
 * Captures what a Berlioz object writes as JSON into maps, so that the overlay can read a Berlioz
 * diagnostic snapshot that only exposes {@code writeTo(OutputWriter)}, such as
 * {@code RedirectFilter.diagnosticSnapshot()}.
 *
 * <p>An object is a {@code Map<String, Object>} of its fields in order; an array is a
 * {@code List<Object>} of its objects or values. Field values are {@code String}, {@code Long},
 * {@code Double}, {@code Boolean} or a {@code List} of them; a null field is not recorded. Element
 * names of the objects in an array are ignored, as in JSON.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
public final class CapturedOutput implements OutputWriter {

  private final Map<String, Object> root = new LinkedHashMap<>();

  /** The open objects and arrays, innermost first. */
  private final Deque<Object> open = new ArrayDeque<>();

  private CapturedOutput() {
    this.open.push(this.root);
  }

  /**
   * @param writer What writes the output, such as {@code snapshot::writeTo}.
   * @return the fields written at the top level, such as the root object of a snapshot by name.
   */
  public static Map<String, Object> capture(Consumer<OutputWriter> writer) {
    CapturedOutput out = new CapturedOutput();
    writer.accept(out);
    return out.root;
  }

  /**
   * @param object A captured object.
   * @param name   The name of a field.
   * @return the value of the field if it is an object, or {@code null}.
   */
  @SuppressWarnings("unchecked")
  public static @Nullable Map<String, Object> object(Map<String, Object> object, String name) {
    Object value = object.get(name);
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  /**
   * @param object A captured object.
   * @param name   The name of an array.
   * @return the objects of the array, empty if there is no such array.
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

  @Override
  public OutputType getType() {
    return OutputType.JSON;
  }

  @Override
  public OutputWriter startObject(String name, ContextOption option) {
    Map<String, Object> object = new LinkedHashMap<>();
    add(name, object);
    this.open.push(object);
    return this;
  }

  @Override
  public OutputWriter endObject() {
    end(Map.class);
    return this;
  }

  @Override
  public OutputWriter startArray(String name, ContextOption option) {
    List<Object> array = new ArrayList<>();
    add(name, array);
    this.open.push(array);
    return this;
  }

  @Override
  public OutputWriter endArray() {
    end(List.class);
    return this;
  }

  @Override
  public OutputWriter field(String name, boolean value, FieldOption option) {
    return value(name, value);
  }

  @Override
  public OutputWriter field(String name, long value, FieldOption option) {
    return value(name, value);
  }

  @Override
  public OutputWriter field(String name, double value, FieldOption option) {
    return value(name, value);
  }

  @Override
  public OutputWriter field(String name, String value, FieldOption option) {
    return value(name, value);
  }

  @Override
  public OutputWriter field(String name, String[] values, FieldOption option) {
    return value(name, List.of(values));
  }

  @Override
  public OutputWriter field(String name, Iterable<String> values, FieldOption option) {
    List<String> list = new ArrayList<>();
    values.forEach(list::add);
    return value(name, list);
  }

  @Override
  public OutputWriter field(String name, long[] values, FieldOption option) {
    List<Long> list = new ArrayList<>();
    for (long value : values) list.add(value);
    return value(name, list);
  }

  @Override
  public OutputWriter field(String name, boolean[] values, FieldOption option) {
    List<Boolean> list = new ArrayList<>();
    for (boolean value : values) list.add(value);
    return value(name, list);
  }

  @Override
  public OutputWriter field(String name, double[] values, FieldOption option) {
    List<Double> list = new ArrayList<>();
    for (double value : values) list.add(value);
    return value(name, list);
  }

  @Override
  public OutputWriter nullField(String name, FieldOption option) {
    return this;
  }

  @Override
  public void flush() {
    // Nothing to flush
  }

  @Override
  public void close() {
    // Nothing to close
  }

  private OutputWriter value(String name, Object value) {
    add(name, value);
    return this;
  }

  /** Adds an object, array or value to the innermost open object or array. */
  @SuppressWarnings("unchecked")
  private void add(String name, Object value) {
    Object parent = this.open.peek();
    if (parent instanceof List) ((List<Object>) parent).add(value);
    else if (parent instanceof Map) ((Map<String, Object>) parent).put(name, value);
  }

  private void end(Class<?> type) {
    if (this.open.size() <= 1 || !type.isInstance(this.open.peek())) {
      throw new IllegalStateException("Unbalanced end of " + type.getSimpleName());
    }
    this.open.pop();
  }

}
