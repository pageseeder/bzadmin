package org.pageseeder.bzadmin.redirect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The parser of mapping files: as Berlioz's {@code ConfigLoader} accepts them, without its logging,
 * and with the schema of the Berlioz JAR or, when unavailable, well-formedness only.
 */
final class MappingParserTest {

  @TempDir
  Path temp;

  private static byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  private static MappingParser.Result redirects(String xml) {
    return MappingParser.parse(bytes(xml), FilterType.REDIRECT, MappingParser.schema(FilterType.REDIRECT));
  }

  @Test
  void loadsTheSchemasOfBerlioz() {
    assertNotNull(MappingParser.schema(FilterType.REDIRECT));
    assertNotNull(MappingParser.schema(FilterType.RELOCATION));
  }

  @Test
  void parsesValidRules() {
    MappingParser.Result result = redirects("<redirect-mapping><redirect from='/a' to='/b' permanent='yes'/>"
        + "<redirect from='/c' to='/d'/></redirect-mapping>");
    assertTrue(result.parsed);
    assertTrue(result.valid);
    assertNull(result.message);
    assertEquals(2, result.total);
    assertEquals("/a", result.rules.get(0).from);
    assertEquals("yes", result.rules.get(0).permanent);
    assertNull(result.rules.get(1).permanent);
  }

  /**
   * {@code permanent="true"}, as the DTD of Berlioz says, is invalid against its schema: the rules
   * are still listed.
   */
  @Test
  void reportsSchemaErrors() {
    MappingParser.Result result = redirects("<redirect-mapping><redirect from='/a' to='/b' permanent='true'/></redirect-mapping>");
    assertTrue(result.parsed);
    assertFalse(result.valid);
    assertNotNull(result.message);
    assertTrue(result.message.contains("true"), result.message);
    assertEquals(1, result.rules.size());
  }

  /**
   * A Berlioz update without the schema: well-formedness only, and the same file is then valid.
   */
  @Test
  void checksWellFormednessWithoutSchema() {
    MappingParser.Result result = MappingParser.parse(bytes("<redirect-mapping><redirect from='/a' to='/b' permanent='true'/></redirect-mapping>"),
        FilterType.REDIRECT, null);
    assertTrue(result.parsed);
    assertTrue(result.valid);
    MappingParser.Result malformed = MappingParser.parse(bytes("<redirect-mapping>"), FilterType.REDIRECT, null);
    assertFalse(malformed.parsed);
    assertFalse(malformed.valid);
    assertNotNull(malformed.message);
  }

  @Test
  void reportsMalformedFilesWithTheLine() {
    MappingParser.Result result = redirects("<redirect-mapping>\n<redirect from='/a' to='/b'>\n");
    assertFalse(result.parsed);
    assertNotNull(result.message);
    assertTrue(result.message.startsWith("Line "), result.message);
    assertTrue(result.rules.isEmpty());
  }

  /**
   * Berlioz removes a DOCTYPE with its public identifier, and refuses others.
   */
  @Test
  void removesBerliozDoctypesOnly() {
    String rules = "<redirect-mapping><redirect from='/a' to='/b'/></redirect-mapping>";
    assertTrue(redirects("<!DOCTYPE redirect-mapping PUBLIC \"-//Berlioz//DTD::Redirect Mapping 1.0//EN\" \"x.dtd\">" + rules).valid);
    assertTrue(redirects("<!DOCTYPE redirect-mapping PUBLIC \"-//Weborganic//DTD::Redirect Mapping 1.0//EN\" \"x.dtd\">" + rules).valid);
    MappingParser.Result other = redirects("<!DOCTYPE redirect-mapping SYSTEM \"redirect-mapping-1.0.dtd\">" + rules);
    assertFalse(other.parsed);
    assertNotNull(other.message);
  }

  @Test
  void removesTheDoctypeAsBerlioz() {
    byte[] clean = MappingParser.removeBerliozDoctype(bytes("<?xml version='1.0'?><!DOCTYPE m PUBLIC \"-//Berlioz//X\" \"y\"><m/>"));
    assertArrayEquals(bytes("<?xml version='1.0'?><m/>"), clean);
    byte[] other = bytes("<!DOCTYPE m SYSTEM \"y\"><m/>");
    assertSame(other, MappingParser.removeBerliozDoctype(other));
    byte[] unterminated = bytes("<!DOCTYPE m PUBLIC \"-//Berlioz//X\"");
    assertSame(unterminated, MappingParser.removeBerliozDoctype(unterminated));
  }

  /**
   * An internal subset after a removed Berlioz DOCTYPE is still refused: external entities never load.
   */
  @Test
  void neverResolvesEntities() throws Exception {
    Path secret = Files.writeString(this.temp.resolve("secret.txt"), "fixture-secret");
    String xml = "<!DOCTYPE redirect-mapping [<!ENTITY x SYSTEM '" + secret.toUri() + "'>]>"
        + "<redirect-mapping><redirect from='/&x;' to='/b'/></redirect-mapping>";
    MappingParser.Result result = redirects(xml);
    assertFalse(result.parsed);
    assertTrue(result.rules.isEmpty());
    assertFalse(String.valueOf(result.message).contains("fixture-secret"));
  }

  @Test
  void boundsTheRulesListed() {
    StringBuilder xml = new StringBuilder("<redirect-mapping>");
    for (int i = 0; i <= MappingParser.MAX_RULES; i++) xml.append("<redirect from='/a/").append(i).append("' to='/b'/>");
    MappingParser.Result result = redirects(xml.append("</redirect-mapping>").toString());
    assertTrue(result.valid);
    assertEquals(MappingParser.MAX_RULES + 1, result.total);
    assertEquals(MappingParser.MAX_RULES, result.rules.size());
  }

  @Test
  void stopsAtTheBoundOnElements() {
    StringBuilder xml = new StringBuilder("<redirect-mapping>");
    for (int i = 0; i < MappingParser.MAX_ELEMENTS; i++) xml.append("<x/>");
    MappingParser.Result result = MappingParser.parse(bytes(xml.append("</redirect-mapping>").toString()), FilterType.REDIRECT, null);
    assertTrue(result.tooLarge);
    assertFalse(result.parsed);
  }

  @Test
  void readsRelocations() {
    MappingParser.Result result = MappingParser.parse(bytes("<relocation-mapping><relocation from='/a' to='/b'/></relocation-mapping>"),
        FilterType.RELOCATION, MappingParser.schema(FilterType.RELOCATION));
    assertTrue(result.valid, String.valueOf(result.message));
    assertEquals(1, result.total);
    // Not a relocation file
    assertFalse(MappingParser.parse(bytes("<redirect-mapping/>"), FilterType.RELOCATION, MappingParser.schema(FilterType.RELOCATION)).valid);
  }

}
