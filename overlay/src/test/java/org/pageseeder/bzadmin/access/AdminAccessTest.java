package org.pageseeder.bzadmin.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

final class AdminAccessTest {

  @Test
  void operateImpliesView() {
    AdminAccess access = AdminAccess.of("admin", AdminPermission.OPERATE);
    assertEquals(Set.of(AdminPermission.VIEW, AdminPermission.OPERATE), access.permissions());
    assertTrue(access.has(AdminPermission.VIEW));
  }

  @Test
  void viewDoesNotImplyOperate() {
    AdminAccess access = AdminAccess.of("viewer", AdminPermission.VIEW);
    assertTrue(access.has(AdminPermission.VIEW));
    assertFalse(access.has(AdminPermission.OPERATE));
  }

  @Test
  void grantsNothingWithoutPermission() {
    assertTrue(AdminAccess.of("user").permissions().isEmpty());
  }

  @Test
  void isImmutable() {
    Set<AdminPermission> permissions = AdminAccess.of("viewer", AdminPermission.VIEW).permissions();
    assertThrows(UnsupportedOperationException.class, () -> permissions.add(AdminPermission.OPERATE));
  }

  @Test
  void requiresNameAndPermissions() {
    assertThrows(NullPointerException.class, () -> AdminAccess.of(null, AdminPermission.VIEW));
    assertThrows(NullPointerException.class, () -> AdminAccess.of("admin", (AdminPermission) null));
  }

}
