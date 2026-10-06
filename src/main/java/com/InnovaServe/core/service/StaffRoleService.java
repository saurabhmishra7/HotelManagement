package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.core.security.RoleType;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffRoleService {
  private final StaffRoleRepository roles;
  private final TenantContext tenant;

  public StaffRoleService(StaffRoleRepository roles, TenantContext tenant) {
    this.roles = roles;
    this.tenant = tenant;
  }

  public List<StaffRole> list() {
    return roles.findAllByTenantIdOrderByName(tenant.tenantId());
  }

  public StaffRole get(UUID roleId) {
    return roles.findByTenantIdAndId(tenant.tenantId(), roleId)
        .orElseThrow(() -> new NoSuchElementException("Role not found"));
  }

  @Transactional
  public StaffRole create(RoleType roleType, List<Permission> requestedPermissions) {
    if (roleType == null) {
      throw new IllegalArgumentException("Role name is required");
    }

    Set<Permission> allowed = roleType.defaultPermissions();
    Set<Permission> selected =
        requestedPermissions == null ? allowed : Set.copyOf(requestedPermissions);

    List<StaffRole> existing =
        roles.findAllByTenantIdAndNameOrderById(tenant.tenantId(), roleType.name());
    if (!existing.isEmpty()) {
      return existing.get(0);
    }

    List<String> stored = selected.stream().map(Enum::name).sorted().toList();
    return roles.save(new StaffRole(tenant.tenantId(), roleType.name(), stored));
  }

  @Transactional
  public StaffRole update(UUID roleId, List<Permission> requestedPermissions) {
    if (requestedPermissions == null) {
      throw new IllegalArgumentException("Permissions are required");
    }
    StaffRole role =
        roles.findByTenantIdAndId(tenant.tenantId(), roleId)
            .orElseThrow(() -> new NoSuchElementException("Role not found"));
    role.setPermissions(requestedPermissions.stream().map(Enum::name).distinct().sorted().toList());
    return role;
  }
}
