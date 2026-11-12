package org.pageseeder.bzadmin.berlioz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.servlet.RedirectFilter;

/**
 * The typed reads of Berlioz snapshot fields, as {@code GetRedirectFilters} reads the snapshot of
 * the redirect filters.
 */
final class SnapshotFieldsTest {

  @Test
  void readsFieldsOfEachType() {
    Map<String, Object> filter = Map.of(
        "name", "Redirects",
        "exists", true,
        "rule-count", 2L,
        "rules", List.of(Map.of("from", "/a"), Map.of("from", "/b")),
        "nested", Map.of("x", "y"),
        "tags", List.of("x", "y"));
    assertEquals("Redirects", SnapshotFields.text(filter, "name"));
    assertTrue(SnapshotFields.flag(filter, "exists"));
    assertEquals(2, SnapshotFields.number(filter, "rule-count", 0));
    List<Map<String, Object>> rules = SnapshotFields.objects(filter, "rules");
    assertEquals(2, rules.size());
    assertEquals("/b", SnapshotFields.text(rules.get(1), "from"));
    Map<String, Object> nested = SnapshotFields.object(filter, "nested");
    assertNotNull(nested);
    assertEquals("y", SnapshotFields.text(nested, "x"));
    assertEquals(List.of(), SnapshotFields.objects(filter, "tags"));
  }

  @Test
  void readsMissingOrOtherFieldsAsAbsent() {
    Map<String, Object> object = Map.of("n", "text", "b", "true");
    assertNull(SnapshotFields.object(object, "n"));
    assertNull(SnapshotFields.text(object, "x"));
    assertEquals(List.of(), SnapshotFields.objects(object, "n"));
    assertEquals(7, SnapshotFields.number(object, "n", 7));
    assertFalse(SnapshotFields.flag(object, "n"));
    assertFalse(SnapshotFields.flag(object, "b"));
  }

  /**
   * Without an initialized filter, Berlioz's snapshot has no entry.
   */
  @Test
  void readsTheRedirectSnapshot() {
    assertEquals(List.of(), RedirectFilter.diagnosticSnapshot().entries());
  }

}
