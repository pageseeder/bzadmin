package org.pageseeder.bzadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The static files of the overlay (versioned bundles, and the fonts and images of the stylesheet)
 * as a browser loads them from the packaged host.
 */
final class StaticFiles {

  private static final Pattern URL = Pattern.compile("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");

  private StaticFiles() {
  }

  /**
   * Loads the bundles linked from an admin page, then the files referenced by the stylesheet, and
   * asserts that each one is served.
   *
   * @return the paths of the files loaded, relative to the context path and without leading '/'.
   */
  static Set<String> load(BerliozHost host) throws IOException, InterruptedException {
    String html = host.get("/bzadmin/config.html").body();
    Matcher bundle = Pattern.compile("(?:href|src)=\"" + Pattern.quote(host.contextPrefix()) + "/((?:js|css)/[^\"]+)\"").matcher(html);
    Set<String> loaded = new TreeSet<>();
    while (bundle.find()) loaded.add(bundle.group(1));
    String version = System.getProperty("bzadmin.version");
    assertTrue(loaded.contains("js/bzadmin-" + version + ".min.js"), "Script of this version: " + loaded);
    assertTrue(loaded.contains("css/bzadmin-" + version + ".min.css"), "Stylesheet of this version: " + loaded);
    for (String path : new TreeSet<>(loaded)) {
      HttpResponse<String> response = host.get("/" + path);
      assertEquals(200, response.statusCode(), path);
      String type = response.headers().firstValue("Content-Type").orElse("");
      assertTrue(type.contains(path.endsWith(".css") ? "text/css" : "javascript"), path + ": " + type);
      if (!path.endsWith(".css")) continue;
      Matcher url = URL.matcher(response.body());
      while (url.find()) {
        String reference = url.group(1);
        if (reference.startsWith("data:")) continue;
        assertFalse(reference.startsWith("/"), "Root-relative reference in " + path + ": " + reference);
        String resolved = URI.create("/" + path).resolve(reference).getPath().substring(1);
        if (loaded.add(resolved)) {
          HttpResponse<String> file = host.get("/" + resolved);
          assertEquals(200, file.statusCode(), resolved + " referenced by " + path);
          assertFalse(file.body().isEmpty(), resolved);
        }
      }
    }
    return loaded;
  }

  /**
   * @return the static files packaged in the overlay JAR, relative to {@code META-INF/resources/}.
   */
  static Set<String> packaged(BerliozHost host) throws IOException {
    Set<String> files = new TreeSet<>();
    try (DirectoryStream<Path> jars = Files.newDirectoryStream(host.webInf().resolve("lib"), "pso-bzadmin-[0-9]*.jar")) {
      for (Path jar : jars) {
        try (JarFile file = new JarFile(jar.toFile())) {
          file.stream().map(JarEntry::getName)
              .filter(name -> name.startsWith("META-INF/resources/") && !name.endsWith("/"))
              .forEach(name -> files.add(name.substring("META-INF/resources/".length())));
        }
      }
    }
    return files;
  }

}
