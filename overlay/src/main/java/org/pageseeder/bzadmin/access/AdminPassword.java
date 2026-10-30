package org.pageseeder.bzadmin.access;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.jspecify.annotations.Nullable;

/**
 * The password of the fallback login ({@link AdminFilter#PASSWORD}): a password hash,
 * PBKDF2-HMAC-SHA256 from the JDK, in the PHC string format
 *
 * <pre>$pbkdf2-sha256$i=&lt;iterations&gt;$&lt;salt&gt;$&lt;hash&gt;</pre>
 *
 * <p>with the salt (16 bytes) and the hash (32 bytes) in base64 without padding. Hashes need at
 * least {@value #MIN_ITERATIONS} iterations; new ones have {@value #ITERATIONS}.
 *
 * <p>Or, for local development, the password itself: a value that does not start like a PHC string
 * ({@code $<scheme>$}) is a plain password ({@link #isHash(String)}).
 *
 * <p>To create a hash, run this class with the overlay JAR on the classpath, and type the password:
 *
 * <pre>java -cp pso-bzadmin-&lt;version&gt;.jar org.pageseeder.bzadmin.access.AdminPassword</pre>
 *
 * @author Christophe Lauret
 * @version 0.14.0
 */
public final class AdminPassword {

  /** The iterations of new hashes (OWASP recommendation for PBKDF2-HMAC-SHA256, 2023). */
  public static final int ITERATIONS = 600_000;

  /** The fewest iterations accepted. */
  public static final int MIN_ITERATIONS = 100_000;

  /** The most iterations accepted, so that a typing mistake cannot make each request take minutes. */
  static final int MAX_ITERATIONS = 10_000_000;

  private static final String PREFIX = "$pbkdf2-sha256$i=";

  /** The start of a PHC string: a scheme between dollars. */
  private static final java.util.regex.Pattern PHC = java.util.regex.Pattern.compile("^\\$[a-z0-9-]{1,32}\\$");

  private static final int SALT_LENGTH = 16;

  private static final int HASH_LENGTH = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  private AdminPassword() {
  }

  /**
   * @param password The password, which this method does not clear.
   * @return a new hash of the password, with a random salt and {@value #ITERATIONS} iterations.
   */
  public static String hash(char[] password) {
    return hash(password, ITERATIONS);
  }

  /**
   * @param password   The password, which this method does not clear.
   * @param iterations The iterations, at least {@value #MIN_ITERATIONS}.
   * @return a new hash of the password, with a random salt.
   */
  public static String hash(char[] password, int iterations) {
    if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
      throw new IllegalArgumentException("Iterations must be between " + MIN_ITERATIONS + " and " + MAX_ITERATIONS);
    }
    byte[] salt = new byte[SALT_LENGTH];
    RANDOM.nextBytes(salt);
    Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
    return PREFIX + iterations + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(pbkdf2(password, salt, iterations));
  }

  /**
   * @param password The configured password.
   * @return whether it is meant as a hash (it starts like a PHC string), valid or not: such a value
   *   is never used as a plain password, so that a mistyped hash does not become the password.
   */
  public static boolean isHash(@Nullable String password) {
    return password != null && PHC.matcher(password).find();
  }

  /**
   * @param hash A hash.
   * @return whether it is a hash in the expected format, with the accepted iterations.
   */
  public static boolean isValid(@Nullable String hash) {
    return parse(hash) != null;
  }

  /**
   * @param password   The password to check, which this method does not clear.
   * @param configured The configured password: a hash, or a plain password ({@link #isHash(String)}).
   * @return whether the password matches; {@code false} for a hash that is not valid, or without
   *   configured password.
   */
  public static boolean verify(char[] password, @Nullable String configured) {
    if (configured == null) return false;
    if (isHash(configured)) return matches(password, configured);
    // Digests of the same length, so that the comparison does not tell the length of the password
    return MessageDigest.isEqual(sha256(new String(password)), sha256(configured));
  }

  private static byte[] sha256(String text) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException ex) {
      // Every Java runtime has it
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  /**
   * Compares in constant time for a given hash.
   *
   * @param password The password to check, which this method does not clear.
   * @param hash     The hash.
   * @return whether the password has this hash; {@code false} if the hash is not valid.
   */
  public static boolean matches(char[] password, @Nullable String hash) {
    Parsed parsed = parse(hash);
    if (parsed == null) return false;
    return MessageDigest.isEqual(pbkdf2(password, parsed.salt, parsed.iterations), parsed.hash);
  }

  private static @Nullable Parsed parse(@Nullable String hash) {
    if (hash == null || !hash.startsWith(PREFIX)) return null;
    String[] parts = hash.substring(PREFIX.length()).split("\\$", -1);
    if (parts.length != 3 || !parts[0].matches("[1-9]\\d{0,8}")) return null;
    int iterations = Integer.parseInt(parts[0]);
    if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) return null;
    try {
      Base64.Decoder base64 = Base64.getDecoder();
      byte[] salt = base64.decode(parts[1]);
      byte[] bytes = base64.decode(parts[2]);
      if (salt.length < SALT_LENGTH || bytes.length != HASH_LENGTH) return null;
      return new Parsed(iterations, salt, bytes);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_LENGTH * 8);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException ex) {
      // Every Java 8+ runtime has it
      throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", ex);
    } finally {
      spec.clearPassword();
    }
  }

  private static final class Parsed {

    final int iterations;

    final byte[] salt;

    final byte[] hash;

    Parsed(int iterations, byte[] salt, byte[] hash) {
      this.iterations = iterations;
      this.salt = salt;
      this.hash = hash;
    }

  }

  /**
   * Prints the hash of a password typed twice on the console (not echoed), for
   * {@link AdminFilter#PASSWORD}.
   *
   * @param args none
   */
  public static void main(String[] args) {
    Console console = System.console();
    if (console == null) {
      fail("No console: run this command in a terminal");
      return;
    }
    char[] password = console.readPassword("Password: ");
    char[] again = console.readPassword("Password again: ");
    try {
      if (password == null || password.length == 0) {
        fail("No password");
      } else if (!Arrays.equals(password, again)) {
        fail("The passwords differ");
      } else {
        console.printf("%s%n", hash(password));
        console.flush();
      }
    } finally {
      if (password != null) Arrays.fill(password, '\0');
      if (again != null) Arrays.fill(again, '\0');
    }
  }

  /**
   * Prints the error of the command and exits with status 1.
   */
  @SuppressWarnings("java:S106") // A command-line tool: its errors go to the standard error, not to a logger
  private static void fail(String message) {
    System.err.println(message);
    System.exit(1);
  }

}
