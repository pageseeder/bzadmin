package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Test;

/**
 * The PSML pages render the output of {@code ListPsmlFolder} and {@code GetPsmlDocument}: links
 * within the context path, ranges, escaped source and expected failures.
 */
final class PsmlXsltTest {

  @Test
  void rendersAFolderWithLinksInTheContext() throws Exception {
    String html = page("bzadmin-files", "files", "ok",
        "<psml-folder root='psml' path='/content' offset='0' limit='2' total='3' folders='1' truncated='false'>"
        + "<entry type='folder' name='news' path='/content/news' modified='2026-10-23T01:02:03Z'/>"
        + "<entry type='document' name='home.psml' path='/content/home.psml' size='1234' modified='2026-10-23T01:02:03.456Z'/>"
        + "</psml-folder>");
    assertTrue(html.contains("href=\"/ctx/bzadmin/files.html?root=psml&amp;path=%2Fcontent%2Fnews\""), html);
    assertTrue(html.contains("href=\"/ctx/bzadmin/files/document.html?root=psml&amp;path=%2Fcontent%2Fhome.psml\""), html);
    assertTrue(html.contains("data-folders=\"/ctx/bzadmin/files/folders.html\""), "Tree loaded on demand");
    assertTrue(html.contains("data-path=\"/content\""), "Folder to reveal in the tree");
    assertTrue(html.contains("Entries 1–2 of 3"), "Range");
    assertTrue(html.contains("path=%2Fcontent&amp;offset=2\">Next"), "Next range");
    assertFalse(html.contains(">Previous"), "No previous range");
  }

  @Test
  void reportsATruncatedFolder() throws Exception {
    String html = page("bzadmin-files", "files", "ok",
        "<psml-folder root='psml' path='/' offset='0' limit='1000' total='10000' folders='0' truncated='true'/>");
    assertTrue(html.contains("too many entries: only the first 10000"), html);
  }

  @Test
  void reportsAMissingRoot() throws Exception {
    String html = page("bzadmin-files", "files", "ok",
        "<psml-folder root='psml' path='/' exists='false' offset='0' limit='1000' total='0' folders='0' truncated='false'/>");
    assertTrue(html.contains("no PSML folder"), html);
    assertFalse(html.contains("directory-tree"), html);
  }

  /**
   * An unreadable folder is forbidden content with an element: not the page of a denial.
   */
  @Test
  void rendersAnUnreadableFolder() throws Exception {
    String html = page("bzadmin-files", "files", "forbidden",
        "<no-psml-folder root='psml' path='/secret' problem='unreadable'/>");
    assertTrue(html.contains("This folder cannot be read."), html);
    assertFalse(html.contains("bzadmin-denied"), html);
  }

  @Test
  void rendersADenialWithoutContent() throws Exception {
    String html = page("bzadmin-files", "files", "forbidden", "");
    assertTrue(html.contains("bzadmin-denied"), html);
    assertFalse(html.contains("PSML"), html);
  }

  @Test
  void listsTheFoldersOfTheTree() throws Exception {
    String html = page("bzadmin-files-folders", "files-folders", "ok",
        "<psml-folder root='psml' path='/content' offset='0' limit='1' total='3' folders='3' truncated='false'>"
        + "<entry type='folder' name='news' path='/content/news' modified='2026-10-23T01:02:03Z'/>"
        + "</psml-folder>");
    assertTrue(html.contains("<details data-path=\"/content/news\">"), html);
    assertTrue(html.contains("2 more folders"), html);
  }

  @Test
  void escapesTheSourceOfADocument() throws Exception {
    String html = page("bzadmin-files-document", "files-document", "ok",
        "<psml-document root='psml' path='/content/home.psml' name='home.psml' size='99' modified='2026-10-23T01:02:03Z'"
        + " status='malformed' message='Unexpected &lt;b&gt;' line='3' column='4' sections='0' fragments='0'>"
        + "<source size='2000000' truncated='true'>&lt;script&gt;alert(1)&lt;/script&gt;</source></psml-document>");
    assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), html);
    assertFalse(html.contains("<script>alert"), html);
    assertTrue(html.contains("not well-formed (line 3, column 4): Unexpected &lt;b&gt;"), html);
    assertTrue(html.contains("Only the start of this document is shown"), html);
    assertTrue(html.contains("href=\"/ctx/bzadmin/files.html?root=psml&amp;path=%2Fcontent\""), "Link to its folder");
  }

  @Test
  void rendersAMissingDocument() throws Exception {
    String html = page("bzadmin-files-document", "files-document", "not-found",
        "<no-psml-document root='psml' path='/missing.psml' problem='not-found'/>");
    assertTrue(html.contains("No document at this path."), html);
  }

  private static String page(String service, String name, String status, String content) throws Exception {
    String xml = "<root service='" + service + "' group='bzadmin'>"
        + "<header><location path='/ctx/bzadmin/files.html'/><path context='/ctx' info='/bzadmin/files' extension='.html'/>"
        + "<berlioz version='0.14.4' mode='test'/></header>"
        + "<content generator='org.pageseeder.bzadmin.psml.ListPsmlFolder' name='" + name + "' target='main' status='" + status + "'>"
        + content + "</content></root>";
    return OverlayXslt.transform(new StreamSource(new StringReader(xml)));
  }

}
