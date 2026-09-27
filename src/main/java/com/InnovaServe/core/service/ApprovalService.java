package com.InnovaServe.core.service;

import com.InnovaServe.core.repository.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;

  public ApprovalService(
      StaffUserRepository u, StaffRoleRepository r, TenantContext t, PasswordEncoder e) {
    users = u;
    roles = r;
    tenant = t;
    encoder = e;
  }

  public void require(String pin, String permission) {
    UUID tid = tenant.tenantId(), uid = tenant.userId();
    var user =
        users
            .findByTenantIdAndId(tid, uid)
            .orElseThrow(() -> new NoSuchElementException("User not found"));
    if (!user.isActive() || user.getPinHash() == null || !encoder.matches(pin, user.getPinHash()))
      throw new IllegalArgumentException("Invalid approval PIN");
    if (user.getRoleId() == null) throw new SecurityException("Approval permission denied");
    var role =
        roles
            .findByTenantIdAndId(tid, user.getRoleId())
            .orElseThrow(() -> new SecurityException("Approval permission denied"));
    if (role.getPermissions() == null
        || (!role.getPermissions().contains(permission) && !role.getPermissions().contains("*")))
      throw new SecurityException("Approval permission denied");
  }
}
