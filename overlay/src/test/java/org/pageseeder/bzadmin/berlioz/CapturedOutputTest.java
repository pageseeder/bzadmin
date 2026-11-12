package org.pageseeder.bzadmin.berlioz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.pageseeder.berlioz.output.OutputWriter.ContextOption;
import org.pageseeder.berlioz.servlet.RedirectFilter;

/**
 * The capture of Berlioz output into maps, as {@code GetRedirectFilters} reads the snapshot of the
 * redirect filters.
 */
final class CapturedOutputTest {

  @Test
  void capturesObjectsArraysAndValues() {
    Map<String, Object> root = CapturedOutput.capture(out -> {
      out.startObject("redirect-config");
      out.startArray("filters", ContextOption.JSON_ONLY);
      out.startObject("filter");
      out.field("name", "Redirects");
      out.field("exists", true);
      out.field("rule-count", 2L);
      out.nullField("failure");
      out.startArray("rules", ContextOption.JSON_ONLY);
      out.startObject("rule");
      out.field("from", "/a");
      out.endObject();
      out.startObject("rule");
      out.field("from", "/b");
      out.endObject();
      out.endArray();
      out.field("tags", List.of("x", "y"));
      out.endObject();
      out.endArray();
      out.endObject();
    });
    Map<String, Object> config = CapturedOutput.object(root, "redirect-config");
    assertNotNull(config);
    List<Map<String, Object>> filters = CapturedOutput.objects(config, "filters");
    assertEquals(1, filters.size());
    Map<String, Object> filter = filters.get(0);
    assertEquals("Redirects", CapturedOutput.text(filter, "name"));
    assertTrue(CapturedOutput.flag(filter, "exists"));
    assertEquals(2, CapturedOutput.number(filter, "rule-count", 0));
    assertFalse(filter.containsKey("failure"));
    assertEquals(List.of("x", "y"), filter.get("tags"));
    List<Map<String, Object>> rules = CapturedOutput.objects(filter, "rules");
    assertEquals("/b", CapturedOutput.text(rules.get(1), "from"));
  }

  @Test
  void readsMissingFieldsAsAbsent() {
    Map<String, Object> object = Map.of("n", "text");
    assertNull(CapturedOutput.object(object, "n"));
    assertNull(CapturedOutput.text(object, "x"));
    assertEquals(List.of(), CapturedOutput.objects(object, "n"));
    assertEquals(7, CapturedOutput.number(object, "n", 7));
    assertFalse(CapturedOutput.flag(object, "n"));
  }

  @Test
  void rejectsUnbalancedOutput() {
    assertThrows(IllegalStateException.class, () -> CapturedOutput.capture(out -> out.endObject()));
    assertThrows(IllegalStateException.class, () -> CapturedOutput.capture(out -> {
      out.startObject("a");
      out.endArray();
    }));
  }

  /**
   * Without an initialized filter, Berlioz's snapshot has its root and no filter.
   */
  @Test
  void capturesTheRedirectSnapshot() {
    Map<String, Object> root = CapturedOutput.capture(RedirectFilter.diagnosticSnapshot()::writeTo);
    Map<String, Object> config = CapturedOutput.object(root, "redirect-config");
    assertNotNull(config);
    assertEquals(List.of(), CapturedOutput.objects(config, "filters"));
  }

}
