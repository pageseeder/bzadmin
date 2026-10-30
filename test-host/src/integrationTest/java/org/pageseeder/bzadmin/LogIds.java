package org.pageseeder.bzadmin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The IDs of log files, as the overlay computes them: the first 16 hexadecimal digits of the
 * SHA-256 digest of the real path of the file.
 */
final class LogIds {

  private LogIds() {
  }

  /**
   * @param name The name of a file in the log directory of the test host.
   * @return the ID of that file.
   */
  static String fixture(String name) {
    try {
      return of(Paths.get(System.getProperty("bzadmin.test.logs")).toRealPath().resolve(name));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * @param path The path of a file, as is (a link is not resolved).
   * @return the ID that the file would have with this path.
   */
  static String of(Path path) {
    byte[] digest;
    try {
      digest = MessageDigest.getInstance("SHA-256").digest(path.toString().getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
    StringBuilder hex = new StringBuilder();
    for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
    return hex.toString();
  }

}
