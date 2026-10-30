package org.pageseeder.bzadmin.access;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;

/**
 * The fallback login of {@link AdminFilter}: BASIC credentials checked against the user name and
 * password of the global configuration ({@link AdminFilter#USERNAME}, {@link AdminFilter#PASSWORD}).
 *
 * <p>Checking a password against a hash is slow by design ({@link AdminPassword}), and browsers send
 * the credentials with every request: the last credentials verified are remembered as a keyed
 * digest (the key is random and only in memory), for the same configured user name and password.
 */
final class FallbackLogin {

  private final byte[] key = new byte[32];

  /** The last credentials verified, or {@code null}. */
  private final AtomicReference<@Nullable Verified> verified = new AtomicReference<>();

  FallbackLogin() {
    new SecureRandom().nextBytes(this.key);
  }

  /**
   * @param req        The request.
   * @param username   The user name of the configuration.
   * @param configured The password of the configuration: a valid hash, or a plain password.
   * @return the access of the administrator, or {@code null} unless the request has their BASIC
   *   credentials.
   */
  @Nullable AdminAccess authenticate(HttpServletRequest req, String username, String configured) {
    byte[] credentials = credentials(req);
    if (credentials == null) return null;
    String decoded = new String(credentials, StandardCharsets.UTF_8);
    int colon = decoded.indexOf(':');
    if (colon < 0) return null;
    byte[] digest = digest(configured, credentials);
    Verified last = this.verified.get();
    boolean valid;
    if (last != null && last.username.equals(username) && last.configured.equals(configured)
        && MessageDigest.isEqual(last.digest, digest)) {
      valid = true;
    } else {
      // Both checks always run, so that the time does not tell whether the user name is right
      boolean user = MessageDigest.isEqual(decoded.substring(0, colon).getBytes(StandardCharsets.UTF_8),
          username.getBytes(StandardCharsets.UTF_8));
      boolean password = AdminPassword.verify(decoded.substring(colon + 1).toCharArray(), configured);
      valid = user && password;
      if (valid) this.verified.set(new Verified(username, configured, digest));
    }
    return valid ? AdminAccess.of(username, AdminPermission.OPERATE) : null;
  }

  /**
   * @return whether the request has BASIC credentials, valid or not.
   */
  static boolean hasCredentials(HttpServletRequest req) {
    return credentials(req) != null;
  }

  /**
   * @return the decoded BASIC credentials of the request, or {@code null} without BASIC credentials
   *   (an empty array is credentials: empty ones).
   */
  private static byte @Nullable [] credentials(HttpServletRequest req) {
    String header = req.getHeader("Authorization");
    if (header == null || header.length() < 6 || !header.substring(0, 6).toLowerCase(Locale.ROOT).equals("basic ")) return null;
    try {
      return Base64.getDecoder().decode(header.substring(6).trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private byte[] digest(String configured, byte[] credentials) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(this.key, "HmacSHA256"));
      mac.update(configured.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      return mac.doFinal(credentials);
    } catch (GeneralSecurityException ex) {
      // Every Java runtime has it
      throw new IllegalStateException("HmacSHA256 is not available", ex);
    }
  }

  private static final class Verified {

    final String username;

    final String configured;

    final byte[] digest;

    Verified(String username, String configured, byte[] digest) {
      this.username = username;
      this.configured = configured;
      this.digest = digest;
    }

  }

}
