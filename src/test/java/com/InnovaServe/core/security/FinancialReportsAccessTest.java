package com.InnovaServe.core.security;

import com.InnovaServe.core.entity.StaffRole;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinancialReportsAccessTest {
  private Set<String> permissions(String name, String... values) {
    return StaffAccess.permissions(List.of(new StaffRole(UUID.randomUUID(), name, List.of(values))));
  }

  @Test void onlyOwnerGetsReportsImplicitly() {
    assertTrue(permissions("OWNER").contains("FINANCIAL_REPORTS_READ"));
    assertFalse(permissions("HOTEL_ADMIN").contains("FINANCIAL_REPORTS_READ"));
    assertFalse(permissions("GENERAL_MANAGER", "*").contains("FINANCIAL_REPORTS_READ"));
    assertTrue(permissions("HOTEL_ADMIN").contains("ROLE_MANAGE"));
  }

  @Test void explicitPermissionAndMultipleRolesAreSupported() {
    assertTrue(permissions("ACCOUNTANT", "FINANCIAL_REPORTS_READ").contains("FINANCIAL_REPORTS_READ"));
    var roles = List.of(new StaffRole(UUID.randomUUID(), "WAITER", List.of("ORDER_READ")),
        new StaffRole(UUID.randomUUID(), "ACCOUNTANT", List.of("FINANCIAL_REPORTS_READ")));
    assertTrue(StaffAccess.permissions(roles).containsAll(Set.of("ORDER_READ", "FINANCIAL_REPORTS_READ")));
  }

  @Test void nonOwnerPresetsDoNotAcquireReports() {
    for (RoleType role : RoleType.values()) {
      assertEquals(role == RoleType.OWNER, role.defaultPermissions().contains(Permission.FINANCIAL_REPORTS_READ), role.name());
    }
  }
}
