package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffUserService {
  private final StaffUserRepository users;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;
  private final StaffRoleRepository roles;

  public StaffUserService(
      StaffUserRepository u, TenantContext t, PasswordEncoder e, StaffRoleRepository roles) {
    users = u;
    tenant = t;
    encoder = e;
    this.roles = roles;
  }

  public List<StaffUser> list() {
    return users.findAllByTenantIdOrderByName(tenant.tenantId());
  }

  @Transactional
  public StaffUser create(
      String name, String phone, String email, String password, String pin, UUID role) {
    UUID tid = tenant.tenantId();
    if (users.existsByTenantIdAndPhone(tid, phone))
      throw new IllegalArgumentException("PhoneAlreadyExists");
    if (role != null && roles.findByTenantIdAndId(tid, role).isEmpty())
      throw new NoSuchElementException("Role not found");
    return users.save(
        new StaffUser(
            tid,
            name,
            phone,
            email,
            encoder.encode(password),
            pin == null ? null : encoder.encode(pin),
            role));
  }

  @Transactional
  public StaffUser update(UUID id, String name, UUID role, Boolean active) {
    if (role != null && roles.findByTenantIdAndId(tenant.tenantId(), role).isEmpty())
      throw new NoSuchElementException("Role not found");
    StaffUser u =
        users
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("User not found"));
    u.update(name, role, active);
    return u;
  }

  public boolean verifyPin(UUID id, String pin) {
    StaffUser u =
        users
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("User not found"));
    return u.getPinHash() != null && encoder.matches(pin, u.getPinHash());
  }

  public StaffUser authenticate(UUID tenantId, String identity, String password) {
    StaffUser user =
        users
            .findByTenantIdAndPhoneAndActiveTrue(tenantId, identity)
            .or(() -> users.findByTenantIdAndEmailAndActiveTrue(tenantId, identity))
            .orElseThrow(() -> new IllegalArgumentException("InvalidCredentials"));
    if (!encoder.matches(password, user.getPasswordHash()))
      throw new IllegalArgumentException("InvalidCredentials");
    return user;
  }
}
