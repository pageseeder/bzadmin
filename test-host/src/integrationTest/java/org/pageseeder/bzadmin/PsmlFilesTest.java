package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.pageseeder.bzadmin.PackagedHostTest.parse;
import static org.pageseeder.bzadmin.PackagedHostTest.xpath;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;

import org.jspecify.annotations.Nullable;

/**
 * The PSML pages (M3.2) over the overlay's repository, with bounded work: one folder at a time,
 * ranges of entries, folders on demand for the tree, the metadata and escaped source of a document.
 *
 * <p>The fixtures are written to the PSML folder of the deployed test host ({@code WEB-INF/psml}),
 * under {@code /fixture}; the host's own PSML files are next to them.
 */
final class PsmlFilesTest {

  /** {@code PsmlRoot.MAX_ENTRIES}: entries per listing. */
  private static final int MAX_ENTRIES = 1000;

  /** {@code PsmlRoot.MAX_SCAN}: directory entries read per listing. */
  private static final int MAX_SCAN = 10_000;

  /** {@code PsmlRoot.MAX_SOURCE_BYTES}. */
  private static final int MAX_SOURCE_BYTES = 1024 * 1024;

  private static @Nullable BerliozHost host;

  private static Path psml;

  @BeforeAll
  static void start() throws Exception {
    host = BerliozHost.start("/");
    psml = host.webInf().resolve("psml");
    Path fixture = Files.createDirectories(psml.resolve("fixture"));
    Path outside = Files.createDirectories(host.webInf().resolve("psml-outside"));
    Files.writeString(outside.resolve("secret.psml"), "<document>fixture-secret-outside-psml</document>");
    Files.writeString(fixture.resolve("doc.psml"), "<document type='fixture' level='portable'>"
        + "<documentinfo><uri docid='fixture-doc'><displaytitle>Fixture &amp; doc</displaytitle></uri></documentinfo>"
        + "<section id='s'><fragment id='f'><para><![CDATA[<script>alert(1)</script>]]></para></fragment></section></document>");
    Files.writeString(fixture.resolve("xxe.psml"), "<!DOCTYPE document [<!ENTITY x SYSTEM '"
        + outside.resolve("secret.psml").toUri() + "'>]><document>&x;</document>");
    Files.writeString(fixture.resolve("big.psml"), "<document>" + "x".repeat(MAX_SOURCE_BYTES + 100) + "</document>");
    Files.writeString(fixture.resolve("notes.txt"), "fixture-secret-not-psml");
    Files.createSymbolicLink(fixture.resolve("link"), outside);
    Files.createSymbolicLink(fixture.resolve("link.psml"), outside.resolve("secret.psml"));
    Path many = Files.createDirectories(fixture.resolve("many"));
    for (int i = 0; i <= MAX_ENTRIES; i++) Files.writeString(many.resolve(String.format("doc-%04d.psml", i)), "<document/>");
    Path huge = Files.createDirectories(fixture.resolve("huge"));
    for (int i = 0; i < MAX_SCAN + 5; i++) Files.createFile(huge.resolve(String.format("doc-%05d.psml", i)));
    Path folders = Files.createDirectories(fixture.resolve("folders"));
    for (int i = 0; i <= MAX_ENTRIES; i++) Files.createDirectory(folders.resolve(String.format("folder-%04d", i)));
    Files.writeString(folders.resolve("doc.psml"), "<document/>");
  }

  @AfterAll
  static void stop() throws Exception {
    if (host != null) host.close();
  }

  @Test
  void listsTheHostFiles() throws Exception {
    Document xml = parse(host.get("/bzadmin/files.src").body());
    assertEquals("/", xpath(xml, "//psml-folder/@path"));
    assertEquals("folder", xpath(xml, "//psml-folder/entry[@name='content']/@type"));
    Document content = parse(host.get("/bzadmin/files.src?path=/content").body());
    assertEquals("/content/home.psml", xpath(content, "//entry[@name='home.psml']/@path"));
    assertEquals("sub-folder", xpath(content, "//entry[1]/@name"), "Folders first");
  }

  @Test
  void listsOnlyFoldersAndDocuments() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/files.src?path=/fixture");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    String[] expected = {"folders", "huge", "many", "big.psml", "doc.psml", "xxe.psml"};
    assertEquals(String.valueOf(expected.length), xpath(xml, "count(//entry)"), "No link, hidden or other file");
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], xpath(xml, "//entry[" + (i + 1) + "]/@name"), "Folders first, then by name");
    }
  }

  @Test
  void listsInJson() throws Exception {
    String json = host.get("/bzadmin/files.json?path=/fixture/many&limit=1").body();
    assertTrue(json.contains("\"path\":\"/fixture/many\""), json);
    assertTrue(json.contains("\"total\":1001"), json);
    assertTrue(json.contains("\"entries\":[{\"type\":\"document\",\"name\":\"doc-0000.psml\",\"path\":\"/fixture/many/doc-0000.psml\""), json);
  }

  @Test
  void boundsTheEntriesOfAListing() throws Exception {
    Document first = parse(host.get("/bzadmin/files.src?path=/fixture/many&limit=5000").body());
    assertEquals(String.valueOf(MAX_ENTRIES), xpath(first, "count(//entry)"), "Clamped to the maximum");
    assertEquals(String.valueOf(MAX_ENTRIES + 1), xpath(first, "//psml-folder/@total"));
    assertEquals("false", xpath(first, "//psml-folder/@truncated"));
    Document next = parse(host.get("/bzadmin/files.src?path=/fixture/many&offset=1000").body());
    assertEquals("1", xpath(next, "count(//entry)"));
    assertEquals("doc-1000.psml", xpath(next, "//entry/@name"));
    String html = host.get("/bzadmin/files.html?path=/fixture/many").body();
    assertTrue(html.contains("Entries 1–1000 of 1001"), "The range is shown");
    assertTrue(html.contains("path=%2Ffixture%2Fmany&amp;offset=1000\">Next"), "Link to the next range");
  }

  @Test
  void boundsTheDirectoryEntriesRead() throws Exception {
    Document xml = parse(host.get("/bzadmin/files.src?path=/fixture/huge").body());
    assertEquals("true", xpath(xml, "//psml-folder/@truncated"));
    assertEquals(String.valueOf(MAX_SCAN), xpath(xml, "//psml-folder/@total"));
    assertEquals(String.valueOf(MAX_ENTRIES), xpath(xml, "count(//entry)"));
    assertTrue(host.get("/bzadmin/files.html?path=/fixture/huge").body().contains("too many entries"), "Truncation is reported");
  }

  @Test
  void listsTheFoldersOfTheTreeOnDemand() throws Exception {
    Document xml = parse(host.get("/bzadmin/files/folders.src?path=/fixture").body());
    assertEquals("3", xpath(xml, "count(//entry)"));
    assertEquals("0", xpath(xml, "count(//entry[@type != 'folder'])"), "Folders only");
    Document bounded = parse(host.get("/bzadmin/files/folders.src?path=/fixture/folders").body());
    assertEquals(String.valueOf(MAX_ENTRIES), xpath(bounded, "count(//entry)"));
    assertEquals(String.valueOf(MAX_ENTRIES + 1), xpath(bounded, "//psml-folder/@total"), "The folders, not the document");
    String html = host.get("/bzadmin/files/folders.html?path=/fixture/folders").body();
    assertTrue(html.contains("<details data-path=\"/fixture/folders/folder-0000\">"), html);
    assertTrue(html.contains("1 more folders"), "The folders not listed are reported");
    assertTrue(host.get("/bzadmin/files/folders.html?path=/fixture/folders&folders=false").body().contains("1 more folders"),
        "The service parameter takes precedence");
  }

  @Test
  void readsADocument() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/files/document.src?path=/fixture/doc.psml");
    assertEquals(200, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals("ok", xpath(xml, "//psml-document/@status"));
    assertEquals("Fixture & doc", xpath(xml, "//psml-document/@title"));
    assertEquals("fixture-doc", xpath(xml, "//psml-document/@docid"));
    assertEquals("1", xpath(xml, "//psml-document/@fragments"));
    assertTrue(xpath(xml, "//psml-document/source").contains("<![CDATA[<script>alert(1)</script>]]>"), "The source as text");
    String html = host.get("/bzadmin/files/document.html?path=/fixture/doc.psml").body();
    assertTrue(html.contains("&lt;![CDATA[&lt;script&gt;alert(1)&lt;/script&gt;]]&gt;"), "The source is escaped");
    assertFalse(html.contains("<script>alert"), html);
    assertTrue(html.contains("href=\"/bzadmin/files.html?root=psml&amp;path=%2Ffixture\""), "Link to its folder");
    String json = host.get("/bzadmin/files/document.json?path=/fixture/doc.psml").body();
    assertTrue(json.contains("\"title\":\"Fixture & doc\""), json);
  }

  @Test
  void boundsTheSource() throws Exception {
    Document xml = parse(host.get("/bzadmin/files/document.src?path=/fixture/big.psml").body());
    assertEquals("true", xpath(xml, "//psml-document/source/@truncated"));
    assertEquals(String.valueOf(MAX_SOURCE_BYTES), xpath(xml, "string-length(//psml-document/source)"));
    assertTrue(host.get("/bzadmin/files/document.html?path=/fixture/big.psml").body().contains("Only the start of this document"));
  }

  @Test
  void neverExpandsEntities() throws Exception {
    HttpResponse<String> response = host.get("/bzadmin/files/document.src?path=/fixture/xxe.psml");
    assertEquals(200, response.statusCode());
    assertEquals("doctype", xpath(parse(response.body()), "//psml-document/@status"));
    assertFalse(response.body().contains("fixture-secret"), response.body());
  }

  @ParameterizedTest
  @CsvSource({
      "/bzadmin/files, ../psml-outside, 400, no-psml-folder, invalid-path",
      "/bzadmin/files, /fixture/.., 400, no-psml-folder, invalid-path",
      "/bzadmin/files, /fixture/link, 404, no-psml-folder, not-found",
      "/bzadmin/files, /fixture/doc.psml, 404, no-psml-folder, not-found",
      "/bzadmin/files, /missing, 404, no-psml-folder, not-found",
      "/bzadmin/files/folders, /fixture/link, 404, no-psml-folder, not-found",
      "/bzadmin/files/document, /fixture/link.psml, 404, no-psml-document, not-found",
      "/bzadmin/files/document, /fixture/link/secret.psml, 404, no-psml-document, not-found",
      "/bzadmin/files/document, /fixture/notes.txt, 404, no-psml-document, not-found",
      "/bzadmin/files/document, /fixture, 404, no-psml-document, not-found",
      "/bzadmin/files/document, /../psml-outside/secret.psml, 400, no-psml-document, invalid-path",
      "/bzadmin/files/document, /fixture/.hidden.psml, 400, no-psml-document, invalid-path"})
  void readsOnlyTheRoot(String route, String path, int status, String element, String problem) throws Exception {
    HttpResponse<String> response = host.get(route + ".src?path=" + encode(path));
    assertEquals(status, response.statusCode(), response.body());
    Document xml = parse(response.body());
    assertEquals(problem, xpath(xml, "//" + element + "/@problem"), path);
    HttpResponse<String> html = host.get(route + ".html?path=" + encode(path));
    assertEquals(status, html.statusCode());
    assertFalse(html.body().contains("fixture-secret"), html.body());
    assertTrue(html.body().contains("alert callout"), "The page reports the problem");
  }

  @Test
  void requiresAKnownRoot() throws Exception {
    for (String route : new String[]{"/bzadmin/files", "/bzadmin/files/folders", "/bzadmin/files/document"}) {
      HttpResponse<String> response = host.get(route + ".src?root=other&path=/fixture/doc.psml");
      assertEquals(404, response.statusCode(), route);
      assertEquals("other", xpath(parse(response.body()), "//no-psml-root/@name"), route);
    }
  }

  @Test
  void requiresADocumentPath() throws Exception {
    assertEquals(400, host.get("/bzadmin/files/document.src").statusCode());
  }

  /**
   * A host without PSML folder: the PSML page says so, and other paths are not found.
   */
  @Test
  void showsAMissingRoot() throws Exception {
    Path moved = psml.resolveSibling("psml-moved");
    Files.move(psml, moved);
    try {
      HttpResponse<String> response = host.get("/bzadmin/files.src");
      assertEquals(200, response.statusCode());
      assertEquals("false", xpath(parse(response.body()), "//psml-folder/@exists"));
      assertTrue(host.get("/bzadmin/files.html").body().contains("no PSML folder"));
      assertEquals(404, host.get("/bzadmin/files.src?path=/fixture").statusCode());
    } finally {
      Files.move(moved, psml);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

}
