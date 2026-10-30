package org.pageseeder.bzadmin.xslt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pageseeder.berlioz.xslt.StylesheetLocation;
import org.pageseeder.bzadmin.xslt.XsltFiles.Source;

/**
 * Stylesheets of the XSLT folder and of JAR overlays, at the paths Berlioz maps them to.
 *
 * <p>The test classpath has the overlay and the fixture as JARs.
 */
final class XsltFilesTest {

  private static final ClassLoader LOADER = XsltFilesTest.class.getClassLoader();

  @TempDir
  Path temp;

  @Test
  void listsStylesheetsOfJarOverlays() throws Exception {
    Map<String, Source> files = XsltFiles.list(this.temp.resolve("none"), LOADER);
    assertEquals(Source.CLASSPATH, files.get("xslt/html/bzadmin.xsl"));
    assertEquals(Source.CLASSPATH, files.get("xslt/html/bzadmin/_functions.xsl"));
    assertEquals(Source.CLASSPATH, files.get("xslt/fixture/nested/leaf.xsl"));
    assertFalse(files.containsKey("xslt/html/default.xsl"));
  }

  @Test
  void filesOverrideTheClasspath() throws Exception {
    Path folder = Paths.get(System.getProperty("bzadmin.fixtures.webapp"), "WEB-INF/xslt");
    Map<String, Source> files = XsltFiles.list(folder, LOADER);
    assertEquals(Source.OVERRIDE, files.get("xslt/html/fixture.xsl"));
    assertEquals(Source.FILE, files.get("xslt/errors/missing-import.xsl"));
    assertEquals(Source.CLASSPATH, files.get("xslt/html/bzadmin.xsl"));
  }

  @Test
  void listsClasspathDirectoriesAndStylesheetsOnly() throws Exception {
    write("classes/META-INF/berlioz/xslt/html/dir.xsl");
    write("classes/META-INF/berlioz/xslt/html/dir/sub.xslt");
    write("classes/META-INF/berlioz/xslt/html/data.xml");
    write("classes/META-INF/berlioz/other.xsl");
    try (URLClassLoader loader = new URLClassLoader(new URL[]{this.temp.resolve("classes").toUri().toURL()}, null)) {
      Map<String, Source> files = XsltFiles.list(this.temp.resolve("none"), loader);
      assertEquals(Map.of("xslt/html/dir.xsl", Source.CLASSPATH, "xslt/html/dir/sub.xslt", Source.CLASSPATH), files);
    }
  }

  /**
   * The same stylesheet in two classpath locations is listed once, with the number of copies.
   */
  @Test
  void countsDuplicateClasspathStylesheets() throws Exception {
    write("first/META-INF/berlioz/xslt/html/dup.xsl");
    write("second/META-INF/berlioz/xslt/html/dup.xsl");
    write("second/META-INF/berlioz/xslt/html/single.xsl");
    URL[] urls = {this.temp.resolve("first").toUri().toURL(), this.temp.resolve("second").toUri().toURL()};
    try (URLClassLoader loader = new URLClassLoader(urls, null)) {
      Map<String, Source> files = XsltFiles.list(this.temp.resolve("none"), loader);
      assertEquals(Map.of("xslt/html/dup.xsl", Source.CLASSPATH, "xslt/html/single.xsl", Source.CLASSPATH), files);
      // Berlioz counts the copies on the application (context) classloader, as ListXsltFiles does
      Thread thread = Thread.currentThread();
      ClassLoader context = thread.getContextClassLoader();
      thread.setContextClassLoader(loader);
      try {
        assertEquals(2, StylesheetLocation.countClasspathCopies("xslt/html/dup.xsl"));
        assertEquals(1, StylesheetLocation.countClasspathCopies("xslt/html/single.xsl"));
        assertEquals(0, StylesheetLocation.countClasspathCopies("xslt/html/none.xsl"));
      } finally {
        thread.setContextClassLoader(context);
      }
    }
  }

  @Test
  void mapsPathsToResources() {
    assertEquals("META-INF/berlioz/xslt/html/bzadmin.xsl", XsltFiles.toResource("xslt/html/bzadmin.xsl"));
  }

  private void write(String path) throws IOException {
    Path file = this.temp.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'/>");
  }

}
