package org.pageseeder.bzadmin.xslt;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.pageseeder.berlioz.content.Environment;
import org.pageseeder.berlioz.error.InvalidParameterException;
import org.pageseeder.berlioz.xslt.StylesheetLocation;
import org.pageseeder.berlioz.xslt.StylesheetSourceKind;

/**
 * The stylesheet that a <code>path</code> parameter refers to, as Berlioz selects group
 * stylesheets ({@link StylesheetLocation#select}): the file in the XSLT folder, or when there is
 * none (not following links), the stylesheet of a JAR overlay (<code>META-INF/berlioz/</code> +
 * path on the classpath).
 */
final class XsltStylesheet {

  /** The XSLT folder of the application. */
  final Path folder;

  /** The classloader of the application. */
  final ClassLoader loader;

  /** The file in the XSLT folder, or <code>null</code> for a classpath stylesheet. */
  final @Nullable Path file;

  /** The classpath resource, or <code>null</code> for a file in the XSLT folder. */
  final @Nullable String resource;

  private XsltStylesheet(Path folder, ClassLoader loader, @Nullable Path file, @Nullable String resource) {
    this.folder = folder;
    this.loader = loader;
    this.file = file;
    this.resource = resource;
  }

  /**
   * @param env  The environment of the application.
   * @param path The path of the stylesheet, starting with "xslt/" (as listed by {@link ListXsltFiles}).
   *
   * @return the stylesheet at this path.
   * @throws InvalidParameterException if the path is not a stylesheet in the XSLT folder or on the classpath.
   */
  static XsltStylesheet locate(Environment env, String path) {
    Path folder = env.getPrivateFile("xslt").toPath().toAbsolutePath().normalize();
    Path stylesheet = env.getPrivateFile(path).toPath().toAbsolutePath().normalize();
    if (!stylesheet.startsWith(folder) || !XsltDocumenter.isSupported(path)) {
      throw invalid(path);
    }
    ClassLoader loader = StylesheetLocation.resolveApplicationClassLoader();
    // Selected as Berlioz selects group stylesheets
    StylesheetLocation location = StylesheetLocation.select(env.getPrivateFolder().toPath(), path);
    if (location.kind() == StylesheetSourceKind.CLASSPATH) {
      String resource = XsltFiles.toResource("xslt/" + folder.relativize(stylesheet).toString().replace('\\', '/'));
      return new XsltStylesheet(folder, loader, null, resource);
    }
    if (!Files.isRegularFile(stylesheet)) throw invalid(path);
    return new XsltStylesheet(folder, loader, stylesheet, null);
  }

  private static InvalidParameterException invalid(String path) {
    return InvalidParameterException.constraintFailed("path", path, "must reference an existing stylesheet in the XSLT folder or on the classpath");
  }

}
