package org.pageseeder.bzadmin.psml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pageseeder.bzadmin.io.Utf8;
import org.pageseeder.bzadmin.psml.PsmlException.Problem;

/**
 * The path policy, bounds and failures of the PSML repository, on a temporary directory.
 */
final class PsmlRepositoryTest {

  private static final String PSML = "<?xml version=\"1.0\"?>\n"
      + "<document type=\"article\" level=\"portable\">\n"
      + "  <documentinfo><uri docid=\"home\" title=\"Home\"><displaytitle> Welcome home </displaytitle></uri></documentinfo>\n"
      + "  <section id=\"title\"><fragment id=\"1\"><heading level=\"1\">Home</heading></fragment></section>\n"
      + "  <section id=\"content\"><xref-fragment id=\"2\"/><properties-fragment id=\"3\"/></section>\n"
      + "</document>\n";

  @TempDir
  Path temp;

  private Path directory;

  private PsmlRoot root;

  @BeforeEach
  void init() throws IOException {
    this.directory = Files.createDirectory(this.temp.resolve("psml"));
    this.root = PsmlRepository.of(Map.of("psml", this.directory)).root("psml");
  }

  // Registered roots

  @Test
  void requestsNameRegisteredRoots() {
    PsmlRepository repository = PsmlRepository.of(Map.of("psml", this.directory));
    assertEquals(List.of("psml"), List.copyOf(repository.names()));
    assertNull(repository.root("other"));
    assertNull(repository.root(this.directory.toString()));
  }

  @Test
  void missingRootIsNotFound() {
    PsmlRoot missing = PsmlRepository.of(Map.of("psml", this.temp.resolve("none"))).root("psml");
    assertProblem(Problem.NOT_FOUND, () -> missing.list("/", 0, 10));
    assertFalse(Files.exists(this.temp.resolve("none")), "The root is not created");
  }

  // Path policy

  @ParameterizedTest
  @ValueSource(strings = {"..", "/..", "../psml", "/content/../..", "content/./a.psml", ".", "/.hidden",
      "content/.git", "a//b", "//a", "a\\b", "..\\a", "c:", "c:/a", "a\u0000b", "a\nb"})
  void rejectsInvalidPaths(String path) throws IOException {
    Files.createDirectories(this.directory.resolve("content"));
    assertProblem(Problem.INVALID_PATH, () -> this.root.list(path, 0, 10));
    assertProblem(Problem.INVALID_PATH, () -> this.root.document(path));
    assertProblem(Problem.INVALID_PATH, () -> this.root.source(path));
  }

  @Test
  void rejectsLongAndDeepPaths() {
    assertProblem(Problem.INVALID_PATH, () -> this.root.list("a".repeat(PsmlRoot.MAX_PATH_LENGTH + 1), 0, 10));
    assertProblem(Problem.INVALID_PATH, () -> this.root.list("/a".repeat(PsmlRoot.MAX_DEPTH + 1), 0, 10));
  }

  @Test
  void acceptsLeadingAndTrailingSlashes() throws Exception {
    write("content/home.psml", PSML);
    assertEquals("/", this.root.list("", 0, 10).path());
    assertEquals("/", this.root.list("/", 0, 10).path());
    assertEquals("/content", this.root.list("content/", 0, 10).path());
    assertEquals("/content", this.root.list("/content", 0, 10).path());
    assertEquals("/content/home.psml", this.root.document("content/home.psml").path());
  }

  @Test
  void readsOnlyFoldersAndDocuments() throws Exception {
    write("content/home.psml", PSML);
    write("content/data.xml", PSML);
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("content/home.psml", 0, 10));
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("content/missing", 0, 10));
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("content/home.psml/a", 0, 10));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("/"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/data.xml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.source("content/data.xml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/missing.psml"));
  }

  // Symbolic links

  @Test
  void doesNotFollowLinksOutOfTheRoot() throws Exception {
    Path outside = Files.createDirectory(this.temp.resolve("outside"));
    Files.writeString(outside.resolve("secret.psml"), "<document><secret/></document>");
    link("content/out", outside);
    link("content/secret.psml", outside.resolve("secret.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("content/out", 0, 10));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/out/secret.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.source("content/out/secret.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/secret.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.source("content/secret.psml"));
    assertEquals(List.of(), names(this.root.list("content", 0, 10)));
  }

  @Test
  void doesNotFollowLinksInTheRoot() throws Exception {
    write("content/home.psml", PSML);
    link("content/alias.psml", this.directory.resolve("content/home.psml"));
    link("alias", this.directory.resolve("content"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/alias.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("alias", 0, 10));
    assertEquals(List.of("content"), names(this.root.list("/", 0, 10)));
    assertEquals(List.of("home.psml"), names(this.root.list("content", 0, 10)));
  }

  @Test
  void rootCanBeALink() throws Exception {
    write("content/home.psml", PSML);
    Path alias = this.temp.resolve("alias");
    assumeTrue(createLink(alias, this.directory));
    PsmlRoot linked = PsmlRepository.of(Map.of("psml", alias)).root("psml");
    assertEquals(List.of("home.psml"), names(linked.list("content", 0, 10)));
    assertEquals("Welcome home", linked.document("content/home.psml").title());
  }

  // Listings

  @Test
  void listsFoldersThenDocumentsByName() throws Exception {
    write("b.psml", PSML);
    write("A.psml", PSML);
    write("notes.txt", "notes");
    write(".hidden.psml", PSML);
    write(".psml", PSML);
    Files.createDirectories(this.directory.resolve("z-folder"));
    Files.createDirectories(this.directory.resolve("B-folder"));
    Files.createDirectories(this.directory.resolve(".git"));
    PsmlListing listing = this.root.list("/", 0, 10);
    assertEquals(List.of("B-folder", "z-folder", "A.psml", "b.psml"), names(listing));
    assertEquals(4, listing.total());
    assertFalse(listing.truncated());
    PsmlListing.Entry folder = listing.entries().get(0);
    assertTrue(folder.isFolder());
    assertEquals("/B-folder", folder.path());
    PsmlListing.Entry document = listing.entries().get(2);
    assertFalse(document.isFolder());
    assertEquals("/A.psml", document.path());
    assertEquals(PSML.length(), document.size());
    assertTrue(document.modified() > 0);
  }

  @Test
  void listsRanges() throws Exception {
    for (int i = 0; i < 25; i++) write(String.format("doc-%02d.psml", i), PSML);
    PsmlListing listing = this.root.list("/", 10, 5);
    assertEquals(List.of("doc-10.psml", "doc-11.psml", "doc-12.psml", "doc-13.psml", "doc-14.psml"), names(listing));
    assertEquals(10, listing.offset());
    assertEquals(25, listing.total());
    assertEquals(List.of("doc-24.psml"), names(this.root.list("/", 24, 5)));
    assertEquals(List.of(), names(this.root.list("/", 100, 5)));
    assertEquals(0, this.root.list("/", -1, 5).offset());
    assertEquals(1, this.root.list("/", 0, 0).entries().size());
  }

  @Test
  void boundsEntriesPerListing() throws Exception {
    for (int i = 0; i <= PsmlRoot.MAX_ENTRIES; i++) Files.createFile(this.directory.resolve("doc-" + i + ".psml"));
    PsmlListing listing = this.root.list("/", 0, Integer.MAX_VALUE);
    assertEquals(PsmlRoot.MAX_ENTRIES, listing.entries().size());
    assertEquals(PsmlRoot.MAX_ENTRIES + 1, listing.total());
    assertFalse(listing.truncated());
  }

  @Test
  void boundsEntriesReadPerDirectory() throws Exception {
    for (int i = 0; i < PsmlRoot.MAX_SCAN + 5; i++) Files.createFile(this.directory.resolve("doc-" + i + ".psml"));
    PsmlListing listing = this.root.list("/", 0, 10);
    assertTrue(listing.truncated());
    assertEquals(PsmlRoot.MAX_SCAN, listing.total());
    assertEquals(10, listing.entries().size());
  }

  // Metadata

  @Test
  void readsMetadata() throws Exception {
    write("content/home.psml", PSML);
    PsmlDocument document = this.root.document("/content/home.psml");
    assertEquals(PsmlDocument.Status.OK, document.status());
    assertEquals("psml", document.root());
    assertEquals("home.psml", document.name());
    assertEquals(PSML.length(), document.size());
    assertEquals("document", document.element());
    assertEquals("article", document.type());
    assertEquals("portable", document.level());
    assertEquals("home", document.docid());
    assertEquals("Welcome home", document.title());
    assertEquals(2, document.sections());
    assertEquals(3, document.fragments());
    assertNull(document.message());
  }

  @Test
  void titleOfTheUriWithoutDisplayTitle() throws Exception {
    write("a.psml", "<document><documentinfo><uri title=\"A title\"><displaytitle> </displaytitle></uri></documentinfo></document>");
    assertEquals("A title", this.root.document("a.psml").title());
    write("b.psml", "<other><documentinfo><uri title=\"Nested\"/></documentinfo></other>");
    PsmlDocument other = this.root.document("b.psml");
    assertEquals("other", other.element());
    assertNull(other.type());
    assertEquals("Nested", other.title());
  }

  @Test
  void cutsLongValues() throws Exception {
    String title = "t".repeat(PsmlDocument.MAX_VALUE_LENGTH * 2);
    write("a.psml", "<document type=\"" + title + "\"><documentinfo><uri><displaytitle>" + title + "</displaytitle></uri></documentinfo></document>");
    PsmlDocument document = this.root.document("a.psml");
    assertEquals(PsmlDocument.MAX_VALUE_LENGTH + 1, document.type().length());
    assertEquals(PsmlDocument.MAX_VALUE_LENGTH + 1, document.title().length());
    assertTrue(document.title().endsWith("…"));
  }

  @Test
  void reportsMalformedDocuments() throws Exception {
    write("a.psml", "<document>\n  <section>\n</document>");
    PsmlDocument document = this.root.document("a.psml");
    assertEquals(PsmlDocument.Status.MALFORMED, document.status());
    assertEquals(3, document.line());
    assertTrue(document.column() > 0);
    assertTrue(document.message().contains("section"), document.message());
    assertFalse(document.message().startsWith("ParseError"), document.message());
    assertEquals("document", document.element());
    write("empty.psml", "");
    assertEquals(PsmlDocument.Status.MALFORMED, this.root.document("empty.psml").status());
    write("binary.psml", "\u0000\u0001\u0002");
    assertEquals(PsmlDocument.Status.MALFORMED, this.root.document("binary.psml").status());
  }

  @Test
  void doesNotResolveExternalEntities() throws Exception {
    Path secret = this.temp.resolve("secret.txt");
    Files.writeString(secret, "fixture-secret-content");
    write("xxe.psml", "<?xml version=\"1.0\"?>\n<!DOCTYPE document [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n"
        + "<document><documentinfo><uri title=\"&xxe;\"/></documentinfo></document>");
    PsmlDocument document = this.root.document("xxe.psml");
    assertEquals(PsmlDocument.Status.DOCTYPE, document.status());
    assertNull(document.title());
    assertFalse(this.root.source("xxe.psml").text().contains("fixture-secret-content"));
    // External DTD
    write("dtd.psml", "<!DOCTYPE document SYSTEM \"" + secret.toUri() + "\"><document/>");
    assertEquals(PsmlDocument.Status.DOCTYPE, this.root.document("dtd.psml").status());
    // An undeclared entity is an error, not a lookup
    write("undeclared.psml", "<document title=\"&xxe;\"/>");
    assertEquals(PsmlDocument.Status.MALFORMED, this.root.document("undeclared.psml").status());
  }

  @Test
  void doesNotExpandEntities() throws Exception {
    StringBuilder dtd = new StringBuilder("<!DOCTYPE document [<!ENTITY a0 \"lol\">");
    for (int i = 1; i < 10; i++) dtd.append("<!ENTITY a").append(i).append(" \"").append(("&a" + (i - 1) + ";").repeat(10)).append("\">");
    write("laughs.psml", dtd + "]><document type=\"&a9;\"/>");
    PsmlDocument document = this.root.document("laughs.psml");
    assertEquals(PsmlDocument.Status.DOCTYPE, document.status());
    assertNull(document.type());
  }

  @Test
  void boundsDepth() throws Exception {
    write("ok.psml", "<x>".repeat(PsmlDocument.MAX_XML_DEPTH) + "</x>".repeat(PsmlDocument.MAX_XML_DEPTH));
    assertEquals(PsmlDocument.Status.OK, this.root.document("ok.psml").status());
    int depth = PsmlDocument.MAX_XML_DEPTH + 1;
    write("deep.psml", "<x>".repeat(depth) + "</x>".repeat(depth));
    assertEquals(PsmlDocument.Status.TOO_DEEP, this.root.document("deep.psml").status());
    // Not even well-formed: it stops before the end
    write("deeper.psml", "<x>".repeat(100_000));
    assertEquals(PsmlDocument.Status.TOO_DEEP, this.root.document("deeper.psml").status());
  }

  @Test
  void boundsElements() throws Exception {
    write("ok.psml", "<document>" + "<section/>".repeat(PsmlDocument.MAX_ELEMENTS - 1) + "</document>");
    PsmlDocument ok = this.root.document("ok.psml");
    assertEquals(PsmlDocument.Status.OK, ok.status());
    assertEquals(PsmlDocument.MAX_ELEMENTS - 1, ok.sections());
    write("many.psml", "<document>" + "<section/>".repeat(PsmlDocument.MAX_ELEMENTS) + "</document>");
    PsmlDocument many = this.root.document("many.psml");
    assertEquals(PsmlDocument.Status.TOO_MANY_ELEMENTS, many.status());
    assertEquals(PsmlDocument.MAX_ELEMENTS - 1, many.sections());
  }

  @Test
  void boundsBytes() throws Exception {
    Path large = this.directory.resolve("large.psml");
    try (OutputStream out = Files.newOutputStream(large)) {
      out.write("<document><!--".getBytes(StandardCharsets.UTF_8));
      out.write(new byte[(int) PsmlDocument.MAX_BYTES]);
    }
    PsmlDocument document = this.root.document("large.psml");
    assertEquals(PsmlDocument.Status.TOO_LARGE, document.status());
    assertNull(document.element(), "Not parsed");
  }

  @Test
  void boundsBytesRead() throws Exception {
    // Larger than the bound once read: as if the file grew after its size was checked
    PsmlDocument document = new PsmlDocument("psml", "/a.psml", Files.readAttributes(this.directory, BasicFileAttributes.class));
    byte[] content = ("<document>" + " ".repeat((int) PsmlDocument.MAX_BYTES) + "</document>").getBytes(StandardCharsets.UTF_8);
    PsmlMetadataReader.read(new ByteArrayInputStream(content), document);
    assertEquals(PsmlDocument.Status.TOO_LARGE, document.status());
    assertEquals("document", document.element());
  }

  // Source

  @Test
  void readsSource() throws Exception {
    write("content/home.psml", PSML);
    PsmlSource source = this.root.source("content/home.psml");
    assertEquals(PSML, source.text());
    assertEquals("/content/home.psml", source.path());
    assertEquals(PSML.length(), source.size());
    assertFalse(source.truncated());
  }

  @Test
  void boundsSource() throws Exception {
    // A two-byte character across the bound is dropped, not replaced
    String text = "a".repeat(PsmlRoot.MAX_SOURCE_BYTES - 1) + "é" + "b".repeat(10);
    write("large.psml", text);
    PsmlSource source = this.root.source("large.psml");
    assertTrue(source.truncated());
    assertEquals(PsmlRoot.MAX_SOURCE_BYTES + 11, source.size());
    assertEquals("a".repeat(PsmlRoot.MAX_SOURCE_BYTES - 1), source.text());
    write("exact.psml", "a".repeat(PsmlRoot.MAX_SOURCE_BYTES));
    assertFalse(this.root.source("exact.psml").truncated());
  }

  @Test
  void replacesMalformedInput() {
    byte[] bytes = {'a', (byte) 0xC3, 'b', (byte) 0xC3};
    assertEquals("a\uFFFDb\uFFFD", Utf8.decode(ByteBuffer.wrap(bytes), false));
    assertEquals("a\uFFFDb", Utf8.decode(ByteBuffer.wrap(bytes), true));
  }

  // Failures

  @Test
  void unreadableFiles() throws Exception {
    assumePosixAndNotRoot();
    write("content/home.psml", PSML);
    Path file = this.directory.resolve("content/home.psml");
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
    try {
      assertProblem(Problem.UNREADABLE, () -> this.root.document("content/home.psml"));
      assertProblem(Problem.UNREADABLE, () -> this.root.source("content/home.psml"));
      assertEquals(List.of("home.psml"), names(this.root.list("content", 0, 10)));
    } finally {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }
  }

  @Test
  void unreadableFolders() throws Exception {
    assumePosixAndNotRoot();
    write("content/home.psml", PSML);
    Path folder = this.directory.resolve("content");
    Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("---------"));
    try {
      assertProblem(Problem.UNREADABLE, () -> this.root.list("content", 0, 10));
      assertProblem(Problem.UNREADABLE, () -> this.root.document("content/home.psml"));
    } finally {
      Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Test
  void deletedFiles() throws Exception {
    write("content/home.psml", PSML);
    assertEquals(List.of("home.psml"), names(this.root.list("content", 0, 10)));
    Files.delete(this.directory.resolve("content/home.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/home.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.source("content/home.psml"));
    Files.delete(this.directory.resolve("content"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.list("content", 0, 10));
  }

  @Test
  void replacedByLinks() throws Exception {
    Path outside = Files.createDirectory(this.temp.resolve("outside"));
    Files.writeString(outside.resolve("secret.psml"), "<document><secret/></document>");
    write("content/home.psml", PSML);
    Files.delete(this.directory.resolve("content/home.psml"));
    link("content/home.psml", outside.resolve("secret.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.document("content/home.psml"));
    assertProblem(Problem.NOT_FOUND, () -> this.root.source("content/home.psml"));
  }

  // Helpers

  private void write(String path, String content) throws IOException {
    Path file = this.directory.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private void link(String path, Path target) throws IOException {
    Path link = this.directory.resolve(path);
    Files.createDirectories(link.getParent());
    assumeTrue(createLink(link, target), "Symbolic links are not supported");
  }

  private static boolean createLink(Path link, Path target) {
    try {
      Files.createSymbolicLink(link, target);
      return true;
    } catch (IOException | UnsupportedOperationException ex) {
      return false;
    }
  }

  private static void assumePosixAndNotRoot() {
    assumeTrue(Files.getFileAttributeView(Path.of("."), PosixFileAttributeView.class) != null);
    assumeFalse("root".equals(System.getProperty("user.name")), "root reads every file");
  }

  private static List<String> names(PsmlListing listing) {
    return listing.entries().stream().map(PsmlListing.Entry::name).collect(Collectors.toList());
  }

  private static void assertProblem(Problem expected, Operation operation) {
    PsmlException ex = assertThrows(PsmlException.class, operation::run);
    assertEquals(expected, ex.problem(), ex.getMessage());
  }

  @FunctionalInterface
  private interface Operation {
    void run() throws Exception;
  }

}
