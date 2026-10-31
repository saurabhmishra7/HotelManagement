package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.RoleType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultStaffRoleSeeder {
  private final TenantRepository tenants;
  private final StaffRoleRepository roles;

  public DefaultStaffRoleSeeder(TenantRepository tenants, StaffRoleRepository roles) {
    this.tenants = tenants;
    this.roles = roles;
  }

  @Transactional
  public void ensureForTenant(UUID tenantId) {
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    Set<String> existing = roles.findAllByTenantIdOrderByName(tenantId).stream()
        .map(role -> role.getName().toUpperCase(Locale.ROOT))
        .collect(Collectors.toSet());
    List<StaffRole> missing = Arrays.stream(RoleType.values())
        .filter(type -> type != RoleType.OWNER && !existing.contains(type.name()))
        .map(type -> new StaffRole(tenantId, type.name(),
            type.defaultPermissions().stream().map(Enum::name).sorted().toList()))
        .toList();
    if (!missing.isEmpty()) roles.saveAll(missing);
  }
}
