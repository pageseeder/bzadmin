package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

/**
 * The stylesheets of the application as Berlioz resolves them: the files in the XSLT folder, and
 * the stylesheets of JAR overlays (<code>META-INF/berlioz/xslt/</code> on the classpath), which
 * Berlioz uses when the XSLT folder has no file at the same path.
 *
 * <p>Paths are relative to the private folder of the application, starting with "xslt/": the path
 * of a classpath stylesheet is its resource name without <code>META-INF/berlioz/</code>.
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class XsltFiles {

  /** The classpath prefix of the stylesheets of JAR overlays, mapped to the XSLT folder. */
  public static final String CLASSPATH_FOLDER = "META-INF/berlioz/";

  private static final String XSLT_RESOURCES = CLASSPATH_FOLDER + "xslt/";

  /** The resource that identifies a JAR overlay without stylesheet directory entries. */
  private static final String SERVICES_RESOURCE = CLASSPATH_FOLDER + "services.xml";

  /**
   * Where a stylesheet is found.
   *
   * @author Christophe Lauret
   * @version 0.14.0
   */
  public enum Source {

    /** In the XSLT folder of the application only. */
    FILE("file"),

    /** On the classpath only. */
    CLASSPATH("classpath"),

    /** In the XSLT folder, which hides the classpath stylesheet at the same path. */
    OVERRIDE("override");

    private final String code;

    Source(String code) {
      this.code = code;
    }

    /**
     * @return the value of the <code>source</code> attribute.
     */
    public String code() {
      return this.code;
    }
  }

  private XsltFiles() {
  }

  /**
   * Returns the resource name of the classpath stylesheet corresponding to a path.
   *
   * @param path The path of a stylesheet, starting with "xslt/".
   * @return the resource name.
   */
  public static String toResource(String path) {
    return CLASSPATH_FOLDER + path;
  }

  /**
   * Lists the stylesheets of the application.
   *
   * @param folder The XSLT folder of the application (it may not exist).
   * @param loader The classloader of the application.
   *
   * @return the source of each stylesheet by path, sorted by path.
   * @throws IOException If the XSLT folder or a classpath location cannot be read.
   */
  public static Map<String, Source> list(Path folder, ClassLoader loader) throws IOException {
    Map<String, Source> files = new TreeMap<>();
    for (String path : classpath(loader)) {
      files.put(path, Source.CLASSPATH);
    }
    if (Files.isDirectory(folder)) {
      try (Stream<Path> paths = Files.walk(folder)) {
        paths.filter(p -> Files.isRegularFile(p) && XsltDocumenter.isSupported(p.getFileName().toString()))
            .map(p -> "xslt/" + folder.relativize(p).toString().replace('\\', '/'))
            .forEach(path -> files.merge(path, Source.FILE, (classpath, file) -> Source.OVERRIDE));
      }
    }
    return files;
  }

  /**
   * @return the paths of the classpath stylesheets in JARs and classpath directories.
   */
  private static Set<String> classpath(ClassLoader loader) throws IOException {
    Set<Path> roots = new HashSet<>();
    addRoots(loader, XSLT_RESOURCES, roots);
    addRoots(loader, SERVICES_RESOURCE, roots);
    Set<String> paths = new HashSet<>();
    for (Path root : roots) {
      if (Files.isDirectory(root)) {
        Path xslt = root.resolve(XSLT_RESOURCES);
        if (!Files.isDirectory(xslt)) continue;
        try (Stream<Path> files = Files.walk(xslt)) {
          files.filter(Files::isRegularFile)
              .map(p -> root.relativize(p).toString().replace('\\', '/'))
              .forEach(entry -> addStylesheet(entry, paths));
        }
      } else if (Files.isRegularFile(root)) {
        try (JarFile jar = new JarFile(root.toFile())) {
          for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory()) addStylesheet(entry.getName(), paths);
          }
        }
      }
    }
    // Only those the classloader provides, as for the documentation
    paths.removeIf(path -> loader.getResource(toResource(path)) == null);
    return paths;
  }

  private static void addStylesheet(String entry, Set<String> paths) {
    if (entry.startsWith(XSLT_RESOURCES) && XsltDocumenter.isSupported(entry) && !entry.contains("/../")) {
      paths.add(entry.substring(CLASSPATH_FOLDER.length()));
    }
  }

  /**
   * Adds the classpath roots (JAR files or directories) that contain a resource.
   */
  private static void addRoots(ClassLoader loader, String resource, Set<Path> roots) throws IOException {
    for (URL url : Collections.list(loader.getResources(resource))) {
      Path root = toRoot(url, resource);
      if (root != null) roots.add(root);
    }
  }

  /**
   * @return the JAR file or the directory of a resource URL, or <code>null</code> for other URLs.
   */
  private static @Nullable Path toRoot(URL url, String resource) {
    try {
      String uri = url.toString();
      if ("jar".equalsIgnoreCase(url.getProtocol())) {
        int separator = uri.indexOf("!/");
        if (separator < 0) return null;
        URL jar = new URL(uri.substring("jar:".length(), separator));
        return "file".equalsIgnoreCase(jar.getProtocol()) ? Paths.get(jar.toURI()) : null;
      }
      if ("file".equalsIgnoreCase(url.getProtocol())) {
        Path path = Paths.get(url.toURI());
        for (int i = Paths.get(resource).getNameCount(); i > 0 && path != null; i--) {
          path = path.getParent();
        }
        return path;
      }
    } catch (IOException | URISyntaxException | IllegalArgumentException ex) {
      // Not a location that can be listed
    }
    return null;
  }

}
