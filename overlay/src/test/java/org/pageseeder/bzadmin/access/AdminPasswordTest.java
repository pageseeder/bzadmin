package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class AdminPasswordTest {

  /** Computed independently, with Python's {@code hashlib.pbkdf2_hmac}. */
  private static final String REFERENCE = "$pbkdf2-sha256$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4";

  @Test
  void matchesAReferenceHash() {
    assertTrue(AdminPassword.matches("correct horse battery staple".toCharArray(), REFERENCE));
    assertFalse(AdminPassword.matches("correct horse battery stapl".toCharArray(), REFERENCE));
    assertFalse(AdminPassword.matches(new char[0], REFERENCE));
  }

  @Test
  void hashesWithARandomSalt() {
    char[] password = "fixture-password".toCharArray();
    String first = AdminPassword.hash(password, AdminPassword.MIN_ITERATIONS);
    String second = AdminPassword.hash(password, AdminPassword.MIN_ITERATIONS);
    assertTrue(first.startsWith("$pbkdf2-sha256$i=100000$"), first);
    assertNotEquals(first, second);
    assertTrue(AdminPassword.matches(password, first));
    assertTrue(AdminPassword.matches(password, second));
    assertTrue(AdminPassword.hash(password).startsWith("$pbkdf2-sha256$i=600000$"));
  }

  @Test
  void refusesTooFewIterations() {
    char[] password = "x".toCharArray();
    assertThrows(IllegalArgumentException.class, () -> AdminPassword.hash(password, AdminPassword.MIN_ITERATIONS - 1));
    assertThrows(IllegalArgumentException.class, () -> AdminPassword.hash(password, AdminPassword.MAX_ITERATIONS + 1));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "correct horse battery staple",
      "pbkdf2-sha256$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4",
      "$pbkdf2-sha1$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4",
      "$pbkdf2-sha256$i=1000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4",
      "$pbkdf2-sha256$i=0100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4",
      "$pbkdf2-sha256$i=100000$AAECAw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4",
      "$pbkdf2-sha256$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3Cr",
      "$pbkdf2-sha256$i=100000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4$",
      "$pbkdf2-sha256$i=100000$AAEC*wQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4"})
  void rejectsOtherFormats(String hash) {
    assertFalse(AdminPassword.isValid(hash), hash);
    assertFalse(AdminPassword.matches("correct horse battery staple".toCharArray(), hash), hash);
  }

  @Test
  void detectsHashes() {
    assertTrue(AdminPassword.isHash(REFERENCE));
    // Meant as hashes, not valid ones: never plain passwords
    assertTrue(AdminPassword.isHash("$pbkdf2-sha256$i=1000$AAECAwQFBgcICQoLDA0ODw$SdScJfWXhGIJ8Nkud3CrZOHHXpS0zmxQkmXuZxddKh4"));
    assertTrue(AdminPassword.isHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"));
    assertTrue(AdminPassword.isHash("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"));
    for (String plain : new String[]{"", "secret", "correct horse battery staple", "$ecret", "pa$$word", "$ $", "$UPPER$x"}) {
      assertFalse(AdminPassword.isHash(plain), plain);
    }
    assertFalse(AdminPassword.isHash(null));
  }

  @Test
  void verifiesHashesAndPlainPasswords() {
    assertTrue(AdminPassword.verify("correct horse battery staple".toCharArray(), REFERENCE));
    assertFalse(AdminPassword.verify(REFERENCE.toCharArray(), REFERENCE), "A hash is not its own password");
    assertTrue(AdminPassword.verify("local-password".toCharArray(), "local-password"));
    assertFalse(AdminPassword.verify("local-passwor".toCharArray(), "local-password"));
    assertFalse(AdminPassword.verify("Local-password".toCharArray(), "local-password"));
    assertFalse(AdminPassword.verify(new char[0], "local-password"));
    assertFalse(AdminPassword.verify(new char[0], null), "No configured password");
    // A hash that is not valid matches nothing, not even itself
    String invalid = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    assertFalse(AdminPassword.verify(invalid.toCharArray(), invalid));
  }

  @Test
  void acceptsTheReference() {
    assertTrue(AdminPassword.isValid(REFERENCE));
    assertFalse(AdminPassword.isValid(null));
  }

}
