package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.repository.PlatformAdminRepository;
import com.InnovaServe.platform.security.PlatformRole;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminService {
  private final PlatformAdminRepository admins;
  private final PasswordEncoder passwords;
  private final PlatformAuditService audit;

  public PlatformAdminService(
      PlatformAdminRepository admins, PasswordEncoder passwords, PlatformAuditService audit) {
    this.admins = admins;
    this.passwords = passwords;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    return admins.findAllByOrderByNameAsc().stream().map(PlatformAdminService::view).toList();
  }

  @Transactional
  public Map<String, Object> create(
      String name, String email, String password, String roleName, UUID actingAdminId) {
    validateNameEmail(name, email);
    if (password == null || password.length() < 16)
      throw new IllegalArgumentException("Platform admin password must be at least 16 characters");
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (admins.existsByEmailIgnoreCase(normalizedEmail))
      throw new IllegalArgumentException("Platform admin email already exists");
    PlatformRole role = parseRole(roleName);
    PlatformAdmin saved =
        admins.save(
            new PlatformAdmin(name, normalizedEmail, passwords.encode(password), role));
    audit.record(actingAdminId, "PLATFORM_ADMIN_CREATED", "platform_admin", saved.getId(), null, view(saved));
    return view(saved);
  }

  @Transactional
  public Map<String, Object> update(
      UUID id, String roleName, Boolean active, UUID actingAdminId) {
    List<PlatformAdmin> lockedAdmins = admins.lockAllAdmins();
    PlatformAdmin target =
        lockedAdmins.stream()
            .filter(admin -> admin.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("Platform admin not found"));
    Map<String, Object> before = view(target);
    PlatformRole nextRole = roleName == null ? target.roleType() : parseRole(roleName);
    boolean nextActive = active == null ? target.isActive() : active;
    boolean removesOwner =
        target.isActive()
            && target.roleType() == PlatformRole.OWNER
            && (!nextActive || nextRole != PlatformRole.OWNER);
    if (removesOwner && lockedAdmins.stream()
            .filter(PlatformAdmin::isActive)
            .filter(admin -> admin.roleType() == PlatformRole.OWNER)
            .count() <= 1) {
      throw new IllegalStateException("At least one active platform owner must remain");
    }
    target.changeRole(nextRole);
    target.setActive(nextActive);
    audit.record(actingAdminId, "PLATFORM_ADMIN_UPDATED", "platform_admin", id, before, view(target));
    return view(target);
  }

  public static Map<String, Object> view(PlatformAdmin admin) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", admin.getId());
    result.put("name", admin.getName());
    result.put("email", admin.getEmail());
    result.put("role", admin.getRole());
    result.put("active", admin.isActive());
    result.put("created_at", admin.getCreatedAt());
    return result;
  }

  private static void validateNameEmail(String name, String email) {
    if (name == null || name.isBlank() || name.trim().length() > 100)
      throw new IllegalArgumentException("Name is required and must be at most 100 characters");
    if (email == null || email.isBlank() || email.trim().length() > 150 || !email.contains("@"))
      throw new IllegalArgumentException("A valid email is required");
  }

  private static PlatformRole parseRole(String roleName) {
    try {
      return PlatformRole.valueOf(roleName.trim().toUpperCase(Locale.ROOT));
    } catch (Exception e) {
      throw new IllegalArgumentException("Role must be OWNER, TENANT_MANAGER, or PLAN_MANAGER");
    }
  }
}
