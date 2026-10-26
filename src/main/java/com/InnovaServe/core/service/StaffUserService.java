package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.StaffRole;
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

  public List<Map<String, Object>> listWithRoles() {
    Map<UUID, StaffRole> byId = new HashMap<>();
    roles.findAllByTenantIdOrderByName(tenant.tenantId()).forEach(role -> byId.put(role.getId(), role));
    return list().stream().map(user -> {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", user.getId());
      result.put("name", user.getName());
      result.put("phone", user.getPhone());
      result.put("email", user.getEmail());
      result.put("active", user.isActive());
      result.put("role_id", user.getRoleId());
      result.put("role_ids", user.getRoleIds());
      var assigned = com.InnovaServe.core.security.StaffAccess.roles(user, byId);
      result.put("printer_access", com.InnovaServe.core.security.StaffAccess.permissions(assigned).contains("PRINTER_OPERATOR"));
      result.put("printer_access_inherited", com.InnovaServe.core.security.StaffAccess.permissions(
          assigned.stream().filter(r -> !"PRINTER_OPERATOR".equals(r.getName())).toList()).contains("PRINTER_OPERATOR"));
      result.put("role_names", com.InnovaServe.core.security.StaffAccess.roles(user, byId).stream().map(StaffRole::getName).toList());
      return result;
    }).toList();
  }

  public Map<String, Object> currentUser() {
    UUID tenantId = tenant.tenantId();
    StaffUser user =
        users
            .findByTenantIdAndId(tenantId, tenant.userId())
            .orElseThrow(() -> new NoSuchElementException("User not found"));
    List<StaffRole> assignedRoles = roles.findAllByTenantIdAndIdIn(tenantId, user.getRoleIds());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("id", user.getId());
    response.put("name", user.getName());
    response.put("phone", user.getPhone());
    response.put("email", user.getEmail());
    response.put("role_id", user.getRoleId());
    response.put("role_ids", user.getRoleIds());
    response.put("role_names", assignedRoles.stream().map(StaffRole::getName).sorted().toList());
    response.put("role_name", assignedRoles.stream().filter(role -> role.getId().equals(user.getRoleId()))
        .map(StaffRole::getName).findFirst().orElse("STAFF"));
    response.put("permissions", org.springframework.security.core.context.SecurityContextHolder
        .getContext().getAuthentication().getAuthorities().stream()
        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith("PERM_"))
        .map(authority -> authority.substring("PERM_".length()))
        .distinct().sorted().toList());
    return response;
  }

  @Transactional
  public StaffUser create(
      String name, String phone, String email, String password, String pin, UUID role) {
    return createWithRoles(name, phone, email, password, pin, role == null ? List.of() : List.of(role));
  }

  @Transactional
  public StaffUser createWithRoles(
      String name, String phone, String email, String password, String pin, Collection<UUID> selectedRoles) {
    UUID tid = tenant.tenantId();
    AccountValidation.requireMobile(phone);
    if (email != null && !email.isBlank()) AccountValidation.requireEmail(email);
    AccountValidation.requirePassword(password, 12);
    if (pin != null && !pin.matches("[0-9]{4,8}"))
      throw new IllegalArgumentException("Staff PIN must contain 4 to 8 digits");
    if (users.existsByTenantIdAndPhone(tid, phone))
      throw new IllegalArgumentException("PhoneAlreadyExists");
    Set<UUID> roleIds = validateRoles(selectedRoles);
    if (hasElevatedRole(roleIds) && !currentActorIsOwner())
      throw new IllegalStateException("Owner approval is required before assigning an elevated role");
    StaffUser user =
        new StaffUser(
            tid,
            name,
            phone,
            email,
            encoder.encode(password),
            pin == null ? null : encoder.encode(pin),
            roleIds.iterator().next());
    user.setRoleIds(roleIds);
    return users.save(user);
  }

  @Transactional
  public StaffUser update(UUID id, String name, UUID role, Boolean active) {
    return updateWithRoles(id, name, role == null ? null : List.of(role), active);
  }

  @Transactional
  public StaffUser updateWithRoles(UUID id, String name, Collection<UUID> selectedRoles, Boolean active) {
    Set<UUID> roleIds = selectedRoles == null ? null : validateRoles(selectedRoles);
    StaffUser u =
        users
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("User not found"));
    if (roleIds != null && hasElevatedRole(roleIds.stream()
        .filter(roleId -> !u.getRoleIds().contains(roleId)).toList()) && !currentActorIsOwner())
      throw new IllegalStateException("Owner approval is required before assigning an elevated role");
    u.update(name, null, active);
    if (roleIds != null) u.setRoleIds(roleIds);
    return u;
  }

  private Set<UUID> validateRoles(Collection<UUID> selected) {
    if (selected == null || selected.isEmpty() || selected.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Select at least one valid role");
    Set<UUID> ids = new LinkedHashSet<>(selected);
    if (roles.findAllByTenantIdAndIdIn(tenant.tenantId(), ids).size() != ids.size())
      throw new IllegalArgumentException("Choose roles belonging to this property");
    return ids;
  }

  @Transactional
  public void printerAccess(UUID id, boolean enabled) {
    var actor = users.findByTenantIdAndId(tenant.tenantId(), tenant.userId()).orElseThrow();
    boolean manager = roles.findAllByTenantIdAndIdIn(tenant.tenantId(), actor.getRoleIds()).stream()
        .anyMatch(r -> Set.of("OWNER","HOTEL_ADMIN","GENERAL_MANAGER","RESTAURANT_MANAGER").contains(r.getName()));
    if (!manager) throw new org.springframework.security.access.AccessDeniedException("Only an Owner or manager can assign printer access");
    var user = users.findByTenantIdAndId(tenant.tenantId(), id).orElseThrow(() -> new NoSuchElementException("Staff member not found"));
    var preset = roles.findAllByTenantIdOrderByName(tenant.tenantId()).stream()
        .filter(r -> "PRINTER_OPERATOR".equals(r.getName())).findFirst()
        .orElseGet(() -> roles.save(new StaffRole(tenant.tenantId(),"PRINTER_OPERATOR",List.of("PRINTER_OPERATOR"))));
    var next = new LinkedHashSet<>(user.getRoleIds());
    if (enabled) next.add(preset.getId()); else next.remove(preset.getId());
    if (next.isEmpty()) throw new IllegalArgumentException("Assign another role before removing this staff member's only role");
    if (!enabled && com.InnovaServe.core.security.StaffAccess.permissions(roles.findAllByTenantIdAndIdIn(tenant.tenantId(),next)).contains("PRINTER_OPERATOR"))
      throw new IllegalArgumentException("Printer access is included in another assigned role; edit that role's permissions");
    user.setRoleIds(next);
  }

  private boolean hasElevatedRole(Collection<UUID> roleIds) {
    return roles.findAllByTenantIdAndIdIn(tenant.tenantId(), roleIds).stream()
        .anyMatch(role -> Set.of("OWNER", "HOTEL_ADMIN", "GENERAL_MANAGER")
            .contains(role.getName().toUpperCase(Locale.ROOT)));
  }

  private boolean currentActorIsOwner() {
    StaffUser actor = users.findByTenantIdAndId(tenant.tenantId(), tenant.userId())
        .filter(StaffUser::isActive).orElse(null);
    return actor != null && roles.findAllByTenantIdAndIdIn(tenant.tenantId(), actor.getRoleIds()).stream()
        .anyMatch(role -> "OWNER".equalsIgnoreCase(role.getName()));
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
