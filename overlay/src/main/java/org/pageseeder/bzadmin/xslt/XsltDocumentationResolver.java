package org.pageseeder.bzadmin.xslt;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;

import org.pageseeder.berlioz.xslt.StylesheetLocation;
import org.pageseeder.berlioz.xslt.XsltResolvers;
import org.pageseeder.bzadmin.xslt.XsltDocumentationException.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import org.jspecify.annotations.Nullable;

/**
 * Resolves the stylesheets referenced while documenting a stylesheet, as Berlioz resolves them when
 * it compiles stylesheets, but only for reading them as XML.
 *
 * <p>References are located by {@link XsltResolvers#locate(String, String, ClassLoader)}, the
 * policy of the renderer: {@code classpath:} references to the resource found by the application
 * classloader, so that relative references inside it resolve against its actual {@code jar:} or
 * {@code file:} URL, other references against the URI of the referencing stylesheet, and only
 * local resources. The documentation then confines them further, as it shows their source.
 *
 * <p>Only stylesheets ({@code .xsl} or {@code .xslt}) can be read: files in the XSLT folder of the
 * application, and resources on the application classpath, in a JAR or in a directory (such as
 * {@code WEB-INF/classes}) where a {@code classpath:} reference was found. Any other scheme, including network
 * URLs, is denied before anything is read. DTDs and external entities are not loaded.
 *
 * <p>Not thread-safe: use one instance per documentation, as it counts the stylesheets it loads.
 */
final class XsltDocumentationResolver implements URIResolver {

  /**
   * The maximum number of stylesheets in one documentation.
   */
  static final int MAX_STYLESHEETS = 256;

  /** The XSLT folder, as specified. */
  private final Path folder;

  /** The real path of the XSLT folder: the base of the URIs of the stylesheets it contains. */
  private final Path realFolder;

  /** The URI of the real XSLT folder, in the same form as the URLs of the stylesheets. */
  private final String folderUri;

  private final ClassLoader loader;

  private final Set<String> loaded = new HashSet<>();

  /** The real paths of the classpath directories where classpath references were found. */
  private final Set<Path> classpathFolders = new HashSet<>();

  /**
   * @param folder The XSLT folder of the application (it may not exist).
   * @param loader The classloader of the application, for classpath references.
   *
   * @throws IOException If the real path of the folder cannot be determined.
   */
  XsltDocumentationResolver(Path folder, ClassLoader loader) throws IOException {
    this.folder = folder.toAbsolutePath().normalize();
    this.realFolder = Files.isDirectory(folder) ? folder.toRealPath() : this.folder;
    this.folderUri = this.realFolder.toUri().toURL().toString();
    this.loader = loader;
  }

  /**
   * @return the URI of the XSLT folder, ending with '/'.
   */
  String folderUri() {
    return this.folderUri;
  }

  /**
   * Returns the source of a stylesheet in the XSLT folder.
   *
   * @param stylesheet The stylesheet to document.
   *
   * @return the source to read it from
   * @throws XsltDocumentationException If it cannot be read.
   */
  Source source(Path stylesheet) throws XsltDocumentationException {
    return load(checkFile(stylesheet));
  }

  @Override
  public Source resolve(String href, @Nullable String base) throws XsltDocumentationException {
    return load(locate(href, base));
  }

  /**
   * Returns the displayed path of a stylesheet: relative to the XSLT folder prefixed with 'xslt/'
   * (like the paths of the XSLT page), or the JAR file name and entry.
   */
  String path(URL url) {
    String uri = url.toString();
    if (uri.startsWith(this.folderUri)) return "xslt/" + uri.substring(this.folderUri.length());
    int separator = uri.indexOf("!/");
    if (uri.startsWith("jar:") && separator > 0) {
      String jar = uri.substring(0, separator);
      return jar.substring(jar.lastIndexOf('/') + 1) + uri.substring(separator);
    }
    return uri;
  }

  private URL locate(String href, @Nullable String base) throws XsltDocumentationException {
    String resource;
    try {
      resource = StylesheetLocation.extractClasspathReference(href);
    } catch (IllegalArgumentException ex) {
      throw new XsltDocumentationException(Type.DENIED, "Invalid classpath reference '" + href + "'", ex);
    }
    checkExtension(href);
    URL url;
    try {
      // Berlioz's own policy: classpath references, relative references and local resources only
      url = XsltResolvers.locate(href, base, this.loader);
    } catch (TransformerException ex) {
      // Berlioz messages may include the absolute base URL, so they are not shown
      if (resource != null) throw new XsltDocumentationException(Type.NOT_FOUND, "'" + href + "' was not found on the classpath", ex);
      throw new XsltDocumentationException(Type.DENIED, "'" + href + "' is not a local stylesheet reference"
          + ": only stylesheets in the XSLT folder or on the classpath can be documented", ex);
    }
    if (resource != null && "file".equalsIgnoreCase(url.getProtocol())) addClasspathFolder(url, resource);
    return check(url, href);
  }

  /**
   * Confines the local resources that Berlioz accepts to the XSLT folder and the classpath.
   */
  private URL check(URL url, String href) throws XsltDocumentationException {
    if ("file".equalsIgnoreCase(url.getProtocol())) {
      Path path = toPath(url, href);
      Path real = realPath(path);
      for (Path classpathFolder : this.classpathFolders) {
        if (real.startsWith(classpathFolder)) return checkClasspathFile(real, classpathFolder);
      }
      return checkFile(path);
    }
    // Berlioz only returns 'file:' and 'jar:file:' URLs
    return checkClasspathResource(url, href);
  }

  /**
   * Records the classpath directory of a classpath resource found in the file system.
   */
  private void addClasspathFolder(URL url, String resource) throws XsltDocumentationException {
    Path classpathFolder = realPath(toPath(url, resource));
    for (int i = Paths.get(resource).getNameCount(); i > 0 && classpathFolder != null; i--) {
      classpathFolder = classpathFolder.getParent();
    }
    if (classpathFolder != null) this.classpathFolders.add(classpathFolder);
  }

  /**
   * A file in a classpath directory is accepted only if the classloader provides it.
   */
  private URL checkClasspathFile(Path path, Path classpathFolder) throws XsltDocumentationException {
    String entry = classpathFolder.relativize(path).toString().replace('\\', '/');
    try {
      if (Files.isRegularFile(path)) {
        Path real = path.toRealPath();
        for (URL resource : Collections.list(this.loader.getResources(entry))) {
          if ("file".equalsIgnoreCase(resource.getProtocol()) && toPath(resource, entry).toRealPath().equals(real)) {
            return real.toUri().toURL();
          }
        }
      }
    } catch (IOException ex) {
      throw new XsltDocumentationException(Type.NOT_FOUND, "'classpath:" + entry + "' cannot be read", ex);
    }
    throw new XsltDocumentationException(Type.NOT_FOUND, "'classpath:" + entry + "' was not found on the classpath");
  }

  /**
   * @return the real path of an existing file, or the path itself.
   */
  private static Path realPath(Path path) {
    try {
      return Files.exists(path) ? path.toRealPath() : path;
    } catch (IOException ex) {
      return path;
    }
  }

  private static Path toPath(URL url, String href) throws XsltDocumentationException {
    try {
      return Paths.get(url.toURI()).toAbsolutePath().normalize();
    } catch (URISyntaxException | IllegalArgumentException ex) {
      throw new XsltDocumentationException(Type.DENIED, "Unsupported file reference '" + href + "'", ex);
    }
  }

  private URL checkFile(Path file) throws XsltDocumentationException {
    Path path = file.toAbsolutePath().normalize();
    if (!path.startsWith(this.folder) && !path.startsWith(this.realFolder)) {
      throw new XsltDocumentationException(Type.DENIED, "'" + file.getFileName() + "' is outside the XSLT folder");
    }
    if (!Files.isRegularFile(path)) {
      throw new XsltDocumentationException(Type.NOT_FOUND, "'" + displayed(path) + "' was not found");
    }
    try {
      // A symbolic link must not lead outside the folder
      Path real = path.toRealPath();
      if (!real.startsWith(this.realFolder)) {
        throw new XsltDocumentationException(Type.DENIED, "'" + file.getFileName() + "' is outside the XSLT folder");
      }
      return real.toUri().toURL();
    } catch (IOException ex) {
      throw new XsltDocumentationException(Type.NOT_FOUND, "'" + displayed(path) + "' cannot be read", ex);
    }
  }

  /**
   * A JAR entry is accepted only if the classloader provides it, so that any JAR entry referenced
   * by an absolute URL cannot be read.
   */
  private URL checkClasspathResource(URL url, String href) throws XsltDocumentationException {
    String uri = url.toString();
    int separator = uri.indexOf("!/");
    if (separator < 0) throw new XsltDocumentationException(Type.DENIED, "Unsupported JAR reference '" + href + "'");
    String entry = uri.substring(separator + 2);
    try {
      for (URL resource : Collections.list(this.loader.getResources(entry))) {
        if (resource.toString().equals(uri)) return resource;
      }
    } catch (IOException ex) {
      throw new XsltDocumentationException(Type.NOT_FOUND, "'" + path(url) + "' cannot be read", ex);
    }
    throw new XsltDocumentationException(Type.NOT_FOUND, "'" + path(url) + "' was not found on the classpath");
  }

  private static void checkExtension(String href) throws XsltDocumentationException {
    String name = href.toLowerCase(Locale.ROOT);
    if (!name.endsWith(".xsl") && !name.endsWith(".xslt")) {
      throw new XsltDocumentationException(Type.DENIED, "'" + href + "' is not a stylesheet (.xsl or .xslt)");
    }
  }

  private Source load(URL url) throws XsltDocumentationException {
    if (this.loaded.add(url.toString()) && this.loaded.size() > MAX_STYLESHEETS) {
      throw new XsltDocumentationException(Type.LIMIT, "More than " + MAX_STYLESHEETS + " stylesheets to document");
    }
    return new SAXSource(newReader(), new InputSource(url.toString()));
  }

  private String displayed(Path path) {
    Path base = path.startsWith(this.realFolder) ? this.realFolder : this.folder;
    return "xslt/" + base.relativize(path).toString().replace('\\', '/');
  }

  private static XMLReader newReader() throws XsltDocumentationException {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newSAXParser().getXMLReader();
    } catch (ParserConfigurationException | SAXException ex) {
      throw new XsltDocumentationException(Type.DENIED, "No secure XML parser available", ex);
    }
  }

}
