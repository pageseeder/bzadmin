package org.pageseeder.bzadmin.redirect;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Reads the configuration file of a filter as Berlioz resolves it ({@code new File(WEB-INF, config)}),
 * but only within {@code WEB-INF}, links resolved, and within Berlioz's size limit.
 *
 * @author Christophe Lauret
 * @version 0.14.1
 */
final class MappingFile {

  /** The size limit of Berlioz's {@code ConfigLoader}: it loads no rule from a larger file. */
  static final int MAX_BYTES = 1_000_000;

  /** Why a file was not read. */
  enum Status {
    MISSING("missing"),
    NO_REAL_PATH("no-real-path"),
    OUTSIDE_WEB_INF("outside-web-inf"),
    NOT_FILE("not-file"),
    TOO_LARGE("too-large"),
    IO_ERROR("io-error"),
    READ("read");

    final String id;

    Status(String id) {
      this.id = id;
    }
  }

  final Status status;

  final byte @Nullable [] bytes;

  final @Nullable String message;

  private MappingFile(Status status, byte @Nullable [] bytes, @Nullable String message) {
    this.status = status;
    this.bytes = bytes;
    this.message = message;
  }

  private static MappingFile not(Status status) {
    return new MappingFile(status, null, null);
  }

  /**
   * @param root   The real path of the context, from {@code ServletContext.getRealPath("/")};
   *               {@code null} when the application is not deployed as a directory.
   * @param config The {@code config} parameter of the filter.
   * @return the file read, or why not.
   */
  static MappingFile read(@Nullable String root, String config) {
    if (root == null) return not(Status.NO_REAL_PATH);
    File webInf = new File(root, "WEB-INF");
    Path file = new File(webInf, config).toPath().normalize();
    // As Berlioz: a file that does not exist, following links
    if (!Files.exists(file)) return not(Status.MISSING);
    try {
      Path real = file.toRealPath();
      if (!real.startsWith(webInf.toPath().toRealPath())) return not(Status.OUTSIDE_WEB_INF);
      if (!Files.isRegularFile(real)) return not(Status.NOT_FILE);
      if (Files.size(real) > MAX_BYTES) return not(Status.TOO_LARGE);
      try (InputStream in = Files.newInputStream(real)) {
        // The file can grow after its size was checked
        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) return not(Status.TOO_LARGE);
        return new MappingFile(Status.READ, bytes, null);
      }
    } catch (NoSuchFileException ex) {
      return not(Status.MISSING);
    } catch (IOException | SecurityException ex) {
      return new MappingFile(Status.IO_ERROR, null, ex.getClass().getSimpleName());
    }
  }

}
