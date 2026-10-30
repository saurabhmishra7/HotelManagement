package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffApprovalService {
  private static final Set<String> ELEVATED = Set.of("OWNER", "HOTEL_ADMIN", "GENERAL_MANAGER");
  private final StaffApprovalRequestRepository requests;
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;
  private final StaffUserService staff;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;
  private final List<StaffApprovalDelivery> deliveries;
  private final StaffInvitationService invitations;

  public StaffApprovalService(StaffApprovalRequestRepository requests, StaffUserRepository users,
      StaffRoleRepository roles, StaffUserService staff, TenantContext tenant,
      PasswordEncoder encoder, List<StaffApprovalDelivery> deliveries,
      StaffInvitationService invitations) {
    this.requests = requests; this.users = users; this.roles = roles; this.staff = staff;
    this.tenant = tenant; this.encoder = encoder; this.deliveries = deliveries;
    this.invitations = invitations;
  }

  @Transactional
  public Map<String, Object> invite(String name, String phone, String email, Collection<UUID> selected) {
    List<StaffRole> chosen = requireRoles(selected);
    if (chosen.stream().noneMatch(this::requiresOwnerApproval) || isOwner(currentUser())) {
      var invitation = invitations.issue(name, phone, email, selected);
      return Map.of("approval_required", false, "invitation_id", invitation.getId());
    }
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Staff name is required");
    AccountValidation.requireMobile(phone);
    AccountValidation.requireEmail(email);
    if (users.existsByTenantIdAndPhone(tenant.tenantId(), phone)
        || invitations.hasPendingPhone(phone))
      throw new IllegalArgumentException("This phone already has an account or pending invitation");
    if (requests.existsByTenantIdAndPhoneAndStatus(tenant.tenantId(), phone, "pending"))
      throw new IllegalArgumentException("A team approval for this phone is already pending");
    var request = StaffApprovalRequest.create(tenant.tenantId(), tenant.userId(), name, phone,
        email, selected);
    queue(request, name);
    return pendingResponse(request);
  }

  @Transactional
  public Map<String, Object> update(UUID id, String name, Collection<UUID> selected, Boolean active) {
    if (selected == null) return staffResponse(staff.updateWithRoles(id, name, null, active));
    List<StaffRole> chosen = requireRoles(selected);
    StaffUser target = users.findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("User not found"));
    boolean addingElevated = chosen.stream().anyMatch(role -> requiresOwnerApproval(role)
        && !target.getRoleIds().contains(role.getId()));
    if (!addingElevated || isOwner(currentUser()))
      return staffResponse(staff.updateWithRoles(id, name, selected, active));
    if (name != null || active != null)
      throw new IllegalArgumentException("Submit elevated role changes separately from profile or status changes");
    if (requests.existsByTenantIdAndTargetUserIdAndStatus(tenant.tenantId(), id, "pending"))
      throw new IllegalArgumentException("A role approval for this staff member is already pending");
    var request = StaffApprovalRequest.roles(tenant.tenantId(), tenant.userId(), id, selected, target.getRoleIds());
    queue(request, target.getName());
    return pendingResponse(request);
  }

  private boolean requiresOwnerApproval(StaffRole role) {
    return ELEVATED.contains(role.getName().toUpperCase(Locale.ROOT))
        || (role.getPermissions() != null && role.getPermissions().contains("FINANCIAL_REPORTS_READ"));
  }

  private void queue(StaffApprovalRequest request, String staffName) {
    Map<UUID, StaffRole> roleById = new HashMap<>();
    roles.findAllByTenantIdOrderByName(tenant.tenantId()).forEach(role -> roleById.put(role.getId(), role));
    List<UUID> ownerIds = users.findAllByTenantIdAndActiveTrue(tenant.tenantId()).stream()
        .filter(user -> com.InnovaServe.core.security.StaffAccess.roles(user, roleById).stream()
            .anyMatch(role -> "OWNER".equalsIgnoreCase(role.getName())))
        .map(StaffUser::getId).toList();
    if (ownerIds.isEmpty()) throw new IllegalStateException("No active Owner is available to approve this request");
    requests.save(request);
    deliveries.forEach(delivery -> delivery.requested(tenant.tenantId(), ownerIds, request.getId(), staffName));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> pending() {
    StaffUser viewer = currentUser();
    List<StaffApprovalRequest> rows = isOwner(viewer)
        ? requests.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenant.tenantId(), "pending")
        : requests.findAllByTenantIdAndRequestedByAndStatusOrderByCreatedAtDesc(tenant.tenantId(), viewer.getId(), "pending");
    return rows.stream().map(this::summary).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    StaffApprovalRequest request = requests.findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Approval request not found"));
    StaffUser viewer = currentUser();
    if (!isOwner(viewer) && !viewer.getId().equals(request.getRequestedBy()))
      throw new AccessDeniedException("This approval request is not available to you");
    return summary(request);
  }

  @Transactional
  public Map<String, Object> review(UUID id, String pin, boolean approve) {
    StaffUser owner = currentUser();
    if (!isOwner(owner)) throw new AccessDeniedException("Only an Owner can review elevated team access");
    if (pin == null || owner.getPinHash() == null || !encoder.matches(pin, owner.getPinHash()))
      throw new IllegalArgumentException("Invalid Owner PIN");
    StaffApprovalRequest request = requests.lockByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Approval request not found"));
    if (!"pending".equals(request.getStatus()))
      throw new IllegalStateException("This request has already been reviewed");
    String staffName = request.getName();
    if (approve) {
      requireRoles(request.getRoleIds());
      if ("create".equals(request.getRequestType())) {
        if (users.existsByTenantIdAndPhone(tenant.tenantId(), request.getPhone()))
          throw new IllegalStateException("This phone is already used by another staff account");
        if (request.getPasswordHash() == null) {
          invitations.issue(request.getName(), request.getPhone(), request.getEmail(), request.getRoleIds());
        } else {
          // Requests submitted before invitation onboarding may already hold credential hashes.
          // Keep them reviewable, but no new request can enter this path.
          StaffUser created = new StaffUser(tenant.tenantId(), request.getName(), request.getPhone(),
              request.getEmail(), request.getPasswordHash(), request.getPinHash(), request.getRoleIds().get(0));
          created.setRoleIds(request.getRoleIds());
          users.save(created);
        }
      } else {
        StaffUser target = users.findByTenantIdAndId(tenant.tenantId(), request.getTargetUserId())
            .orElseThrow(() -> new NoSuchElementException("Staff member no longer exists"));
        if (!new HashSet<>(target.getRoleIds()).equals(new HashSet<>(request.getPreviousRoleIds())))
          throw new IllegalStateException("Staff roles changed since this request. Submit a new approval request.");
        target.setRoleIds(request.getRoleIds());
        staffName = target.getName();
      }
    }
    request.review(owner.getId(), approve);
    String reviewedStaffName = staffName == null ? "The staff member" : staffName;
    deliveries.forEach(delivery -> {
      delivery.resolved(tenant.tenantId(), request.getId());
      delivery.reviewed(tenant.tenantId(), request.getRequestedBy(), request.getId(),
          reviewedStaffName, approve);
    });
    return summary(request);
  }

  private List<StaffRole> requireRoles(Collection<UUID> selected) {
    if (selected == null || selected.isEmpty() || selected.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Select at least one valid role");
    var unique = new LinkedHashSet<>(selected);
    List<StaffRole> chosen = roles.findAllByTenantIdAndIdIn(tenant.tenantId(), unique);
    if (chosen.size() != unique.size()) throw new IllegalArgumentException("Choose roles belonging to this property");
    return chosen;
  }

  private StaffUser currentUser() {
    return users.findByTenantIdAndId(tenant.tenantId(), tenant.userId())
        .filter(StaffUser::isActive).orElseThrow(() -> new AccessDeniedException("Staff account is not active"));
  }

  private boolean isOwner(StaffUser user) {
    return roles.findAllByTenantIdAndIdIn(user.getTenantId(), user.getRoleIds()).stream()
        .anyMatch(role -> "OWNER".equalsIgnoreCase(role.getName()));
  }

  private Map<String, Object> summary(StaffApprovalRequest request) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", request.getId()); result.put("request_type", request.getRequestType());
    result.put("name", request.getName() == null ? users.findByTenantIdAndId(tenant.tenantId(), request.getTargetUserId())
        .map(StaffUser::getName).orElse("Staff member") : request.getName());
    result.put("phone", request.getPhone()); result.put("target_user_id", request.getTargetUserId());
    result.put("requested_by", request.getRequestedBy()); result.put("status", request.getStatus());
    result.put("created_at", request.getCreatedAt()); result.put("reviewed_at", request.getReviewedAt());
    result.put("role_ids", request.getRoleIds());
    result.put("role_names", requireRoles(request.getRoleIds()).stream().map(StaffRole::getName).sorted().toList());
    return result;
  }

  private Map<String, Object> pendingResponse(StaffApprovalRequest request) {
    return Map.of("approval_required", true, "approval_id", request.getId(), "status", request.getStatus());
  }

  private Map<String, Object> staffResponse(StaffUser user) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", user.getId()); result.put("name", user.getName()); result.put("phone", user.getPhone());
    result.put("email", user.getEmail()); result.put("role_id", user.getRoleId());
    result.put("role_ids", user.getRoleIds()); result.put("active", user.isActive());
    return result;
  }
}
