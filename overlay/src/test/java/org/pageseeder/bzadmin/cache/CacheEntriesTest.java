package org.pageseeder.bzadmin.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Typed keys (no text round-trip of other objects) and bounded ranges of keys.
 */
final class CacheEntriesTest {

  @Test
  void supportsTextAndNumberKeys() {
    assertEquals(CacheKey.Type.STRING, CacheKey.of("/index.psml").type());
    assertEquals(CacheKey.Type.INTEGER, CacheKey.of(42).type());
    assertEquals(CacheKey.Type.LONG, CacheKey.of(42L).type());
    for (Object key : new Object[] {"/index.psml", "", "a\nb", 42, -1, 42L, Long.MIN_VALUE}) {
      CacheKey typed = CacheKey.of(key);
      CacheKey parsed = CacheKey.parse(typed.type().toString(), typed.text());
      assertEquals(key, parsed.value(), "Round trip of " + key);
      assertEquals(typed, parsed);
    }
    assertEquals(CacheKey.parse("integer", "42").value().getClass(), Integer.class);
    assertNotEquals(CacheKey.parse("integer", "42"), CacheKey.parse("string", "42"), "Typed");
  }

  @Test
  void refusesOtherKeys() {
    for (Object key : new Object[] {UUID.randomUUID(), 1.5, new Object(), List.of("a"), 'c', "x".repeat(CacheKey.MAX_LENGTH + 1)}) {
      assertNull(CacheKey.of(key), String.valueOf(key.getClass()));
    }
    assertEquals(CacheKey.MAX_LENGTH, ((String) CacheKey.of("x".repeat(CacheKey.MAX_LENGTH)).value()).length());
    for (String[] invalid : new String[][] {{"uuid", "x"}, {"object", "x"}, {"", "x"}, {null, "x"}, {"String", "x"},
        {"integer", "x"}, {"integer", "2147483648"}, {"long", "1.5"}, {"long", ""}, {"string", "x".repeat(CacheKey.MAX_LENGTH + 1)}}) {
      assertThrows(IllegalArgumentException.class, () -> CacheKey.parse(invalid[0], invalid[1]), invalid[0] + " " + invalid[1]);
    }
    assertThrows(NullPointerException.class, () -> CacheKey.parse("string", null));
  }

  @Test
  void readsARange() {
    List<Object> keys = new ArrayList<>();
    for (int i = 0; i < 10; i++) keys.add("k" + i);
    CacheEntries entries = CacheEntries.scan(keys.iterator(), 3, 4);
    assertEquals(List.of("k3", "k4", "k5", "k6"), texts(entries));
    assertEquals(3, entries.offset());
    assertEquals(7, entries.scanned());
    assertTrue(entries.more());
    CacheEntries last = CacheEntries.scan(keys.iterator(), 8, 4);
    assertEquals(List.of("k8", "k9"), texts(last));
    assertFalse(last.more());
    assertTrue(CacheEntries.scan(keys.iterator(), 20, 4).keys().isEmpty());
    assertEquals(List.of("k0"), texts(CacheEntries.scan(keys.iterator(), -5, 0)), "Clamped");
  }

  @Test
  void countsOtherKeysByClass() {
    List<Object> keys = List.of("a", UUID.randomUUID(), UUID.randomUUID(), 1.5, "x".repeat(CacheKey.MAX_LENGTH + 1), 7L);
    CacheEntries entries = CacheEntries.scan(keys.iterator(), 0, 100);
    assertEquals(List.of("a", "7"), texts(entries));
    assertEquals(Map.of("java.util.UUID", 2, "java.lang.Double", 1, "java.lang.String (too long)", 1), entries.unsupported());
  }

  /**
   * However many keys the cache has and whatever the range requested, at most
   * {@link CacheEntries#MAX_SCAN} keys are read and {@link CacheEntries#MAX_LIMIT} returned.
   */
  @Test
  void boundsTheWork() {
    int[] read = new int[1];
    Iterator<Object> endless = new Iterator<>() {
      @Override public boolean hasNext() { return true; }
      @Override public Object next() { return "k" + read[0]++; }
    };
    CacheEntries entries = CacheEntries.scan(endless, Integer.MAX_VALUE, Integer.MAX_VALUE);
    assertEquals(CacheEntries.MAX_SCAN, read[0]);
    assertEquals(CacheEntries.MAX_SCAN, entries.offset());
    assertTrue(entries.keys().isEmpty());
    assertTrue(entries.more());
    read[0] = 0;
    CacheEntries first = CacheEntries.scan(endless, 0, Integer.MAX_VALUE);
    assertEquals(CacheEntries.MAX_LIMIT, first.keys().size());
    assertEquals(CacheEntries.MAX_LIMIT, read[0]);
    read[0] = 0;
    CacheEntries end = CacheEntries.scan(endless, CacheEntries.MAX_SCAN - 10, CacheEntries.MAX_LIMIT);
    assertEquals(10, end.keys().size());
    assertEquals(CacheEntries.MAX_SCAN, read[0]);
  }

  private static List<String> texts(CacheEntries entries) {
    List<String> texts = new ArrayList<>();
    for (CacheKey key : entries.keys()) texts.add(key.text());
    return texts;
  }

}
