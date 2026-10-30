package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The host libraries resolved by the consumer fixture from the staging repository (release
 * qualification, {@code hostTestStaged} only).
 */
@EnabledIfSystemProperty(named = "bzadmin.staged.jar", matches = ".+")
final class StagedArtifactTest {

  private static @Nullable BerliozHost host;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void deploysTheStagedOverlayJar() throws Exception {
    String version = System.getProperty("bzadmin.version");
    List<String> overlays = libraries("pso-bzadmin-");
    assertEquals(List.of("pso-bzadmin-" + version + ".jar"), overlays);
    assertEquals(List.of("bzadmin-fixture-" + version + ".jar"), libraries("bzadmin-"));
    Path staged = Paths.get(System.getProperty("bzadmin.staged.jar"));
    Path deployed = host.webInf().resolve("lib/pso-bzadmin-" + version + ".jar");
    assertArrayEquals(sha256(staged), sha256(deployed), "Deployed JAR is the staged one");
  }

  /**
   * The host declares Saxon-HE 12 without a strict constraint: the overlay must not bring a newer
   * processor (Bastille 0.13.0, a dependency until M5, declared Saxon-HE 13, Java 17 bytecode).
   */
  @Test
  void usesTheProcessorSelectedByTheHost() throws Exception {
    List<String> saxon = libraries("Saxon-HE-");
    assertEquals(1, saxon.size(), saxon.toString());
    assertTrue(saxon.get(0).startsWith("Saxon-HE-12."), saxon.toString());
    assertEquals(List.of(), libraries("cobble"), "No Cobble");
  }

  /**
   * The logback adapter is optional: the published metadata declares no logging backend (the host
   * selects one, logback here).
   */
  @Test
  void declaresNoLoggingBackend() throws Exception {
    Path staged = Paths.get(System.getProperty("bzadmin.staged.jar"));
    String base = staged.getFileName().toString().replaceFirst("\\.jar$", "");
    for (String metadata : new String[]{base + ".pom", base + ".module"}) {
      String text = Files.readString(staged.resolveSibling(metadata));
      for (String backend : new String[]{"logback", "log4j", "slf4j-simple", "slf4j-nop", "slf4j-jdk14", "slf4j-reload4j"}) {
        assertFalse(text.contains(backend), metadata + " declares " + backend);
      }
    }
  }

  /**
   * The cache adapters are optional: the published POM depends on Berlioz only (no Bastille, no
   * cache library), and the host gets the EHCache 2 and JCache it declares.
   */
  @Test
  void declaresNoCacheLibrary() throws Exception {
    Path staged = Paths.get(System.getProperty("bzadmin.staged.jar"));
    String base = staged.getFileName().toString().replaceFirst("\\.jar$", "");
    Document pom = PackagedHostTest.parse(Files.readString(staged.resolveSibling(base + ".pom")));
    assertEquals("org.pageseeder.berlioz", PackagedHostTest.xpath(pom,
        "/*[local-name() = 'project']/*[local-name() = 'groupId']"));
    assertEquals("pso-bzadmin", PackagedHostTest.xpath(pom,
        "/*[local-name() = 'project']/*[local-name() = 'artifactId']"));
    String dependencies = "/*[local-name() = 'project']/*[local-name() = 'dependencies']/*[local-name() = 'dependency']";
    assertEquals("1", PackagedHostTest.xpath(pom, "count(" + dependencies + ")"));
    assertEquals("pso-berlioz", PackagedHostTest.xpath(pom, dependencies + "/*[local-name() = 'artifactId']"));
    assertEquals("0", PackagedHostTest.xpath(pom, "count(" + dependencies + "[contains(*[local-name() = 'groupId'], 'ehcache')"
        + " or contains(*[local-name() = 'groupId'], 'javax.cache') or contains(*[local-name() = 'groupId'], 'caffeine')])"));
    assertEquals(List.of(), libraries("pso-bastille"), "No Bastille");
    String module = Files.readString(staged.resolveSibling(base + ".module"));
    assertFalse(module.contains("\"module\": \"cache-api\""), "No JCache API");
    assertEquals(List.of("ehcache-2.11.1.3.11.jar"), libraries("ehcache-"), "The host's EHCache only");
  }

  /**
   * The distribution materials (M6.1): sources and Javadoc, the license files in every JAR, the
   * license in the POM, and an SBOM of the runtime graph only.
   */
  @Test
  void publishesDistributionMaterials() throws Exception {
    Path staged = Paths.get(System.getProperty("bzadmin.staged.jar"));
    String base = staged.getFileName().toString().replaceFirst("\\.jar$", "");
    for (String jar : new String[]{base + ".jar", base + "-sources.jar", base + "-javadoc.jar"}) {
      try (ZipFile zip = new ZipFile(staged.resolveSibling(jar).toFile())) {
        for (String entry : new String[]{"META-INF/LICENSE", "META-INF/NOTICE", "META-INF/licenses/Quicksand-OFL-1.1.txt"}) {
          assertNotNull(zip.getEntry(entry), jar + " has " + entry);
        }
      }
    }
    Document pom = PackagedHostTest.parse(Files.readString(staged.resolveSibling(base + ".pom")));
    assertEquals("The Apache Software License, Version 2.0", PackagedHostTest.xpath(pom,
        "/*[local-name() = 'project']/*[local-name() = 'licenses']/*[local-name() = 'license']/*[local-name() = 'name']"));
    String sbom = Files.readString(staged.resolveSibling(base + "-cyclonedx.json"));
    Matcher purl = Pattern.compile("\"purl\"\\s*:\\s*\"pkg:maven/([^/\"]+)/([^@\"]+)@").matcher(sbom);
    Set<String> components = new TreeSet<>();
    while (purl.find()) components.add(purl.group(1) + ":" + purl.group(2));
    components.remove("org.pageseeder.berlioz:pso-bzadmin");
    assertEquals(Set.of("org.pageseeder.berlioz:pso-berlioz", "org.pageseeder.xmlwriter:pso-xmlwriter", "org.slf4j:slf4j-api"), components);
  }

  private static List<String> libraries(String prefix) throws Exception {
    try (Stream<Path> files = Files.list(host.webInf().resolve("lib"))) {
      return files.map(file -> file.getFileName().toString()).filter(name -> name.startsWith(prefix)).sorted().collect(Collectors.toList());
    }
  }

  private static byte[] sha256(Path file) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
  }

}
