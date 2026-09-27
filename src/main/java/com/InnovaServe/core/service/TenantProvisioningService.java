package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.Permission;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {
  private final TenantRepository tenants;
  private final StaffRoleRepository roles;
  private final StaffUserRepository users;
  private final PasswordEncoder passwordEncoder;

  public TenantProvisioningService(
      TenantRepository tenants,
      StaffRoleRepository roles,
      StaffUserRepository users,
      PasswordEncoder passwordEncoder) {
    this.tenants = tenants;
    this.roles = roles;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public ProvisionedTenant create(ProvisionTenant request) {
    Tenant tenant =
        tenants.save(new Tenant(request.name().trim(), request.gstin(), request.address()));
    List<String> permissions =
        java.util.Arrays.stream(Permission.values()).map(Enum::name).sorted().toList();
    StaffRole ownerRole = roles.save(new StaffRole(tenant.getId(), "OWNER", permissions));
    StaffUser owner =
        users.save(
            new StaffUser(
                tenant.getId(),
                request.ownerName().trim(),
                request.ownerPhone().trim(),
                request.ownerEmail(),
                passwordEncoder.encode(request.ownerPassword()),
                passwordEncoder.encode(request.ownerPin()),
                ownerRole.getId()));
    return new ProvisionedTenant(tenant, owner, ownerRole);
  }

  public record ProvisionTenant(
      String name,
      String gstin,
      String address,
      String ownerName,
      String ownerPhone,
      String ownerEmail,
      String ownerPassword,
      String ownerPin) {}

  public record ProvisionedTenant(Tenant tenant, StaffUser owner, StaffRole ownerRole) {}
}
