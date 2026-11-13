package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The status of the fallback login on the Config page, and the checks of the password to hash.
 */
final class AdminLoginTest {

  private static final String REFERENCE = "$pbkdf2-sha256$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4";

  @Test
  void tellsHowThePasswordIsConfigured() {
    assertEquals("none", GetAdminLogin.status(null, null));
    assertEquals("none", GetAdminLogin.status("", ""));
    assertEquals("incomplete", GetAdminLogin.status("admin", null));
    assertEquals("incomplete", GetAdminLogin.status(null, "secret"));
    assertEquals("incomplete", GetAdminLogin.status("ad:min", REFERENCE));
    assertEquals("plain", GetAdminLogin.status("admin", "secret"));
    assertEquals("hash", GetAdminLogin.status("admin", REFERENCE));
    assertEquals("invalid", GetAdminLogin.status("admin", "$pbkdf2-sha256$i=10$AAEC$AAEC"));
    assertEquals("invalid", GetAdminLogin.status("admin", "$argon2id$v=19$m=65536"));
  }

  @Test
  void checksThePasswordToHash() {
    assertEquals("missing", HashAdminPassword.problem(null, null));
    assertEquals("missing", HashAdminPassword.problem("", ""));
    assertEquals("mismatch", HashAdminPassword.problem("secret", "Secret"));
    assertEquals("mismatch", HashAdminPassword.problem("secret", null));
    String longest = "x".repeat(HashAdminPassword.MAX_LENGTH);
    assertNull(HashAdminPassword.problem(longest, longest));
    assertEquals("too-long", HashAdminPassword.problem(longest + "x", longest + "x"));
    // Spaces are part of the password, as in BASIC credentials
    assertNull(HashAdminPassword.problem(" secret ", " secret "));
  }

}
