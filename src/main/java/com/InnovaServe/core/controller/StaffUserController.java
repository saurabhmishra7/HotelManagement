package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.service.StaffUserService;
import com.InnovaServe.core.service.StaffApprovalService;
import com.InnovaServe.core.service.PasswordResetService;
import com.InnovaServe.core.service.EmailVerificationService;
import com.InnovaServe.core.service.TokenService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StaffUserController {
  private final StaffUserService service;
  private final StaffApprovalService approvals;
  private final TokenService tokens;
  private final PasswordResetService passwordResets;
  private final EmailVerificationService emailVerifications;
  private final com.InnovaServe.core.service.StaffInvitationService invitations;

  public StaffUserController(
      StaffUserService s, StaffApprovalService approvals, TokenService tokens, PasswordResetService passwordResets,
      EmailVerificationService emailVerifications,
      com.InnovaServe.core.service.StaffInvitationService invitations) {
    service = s;
    this.approvals = approvals;
    this.tokens = tokens;
    this.passwordResets = passwordResets;
    this.emailVerifications = emailVerifications;
    this.invitations = invitations;
  }

  @PostMapping("/auth/invitations/accept")
  public Map<String, Object> acceptInvitation(@jakarta.validation.Valid @RequestBody AcceptInvitation request) {
    StaffUser user = invitations.accept(request.token(), request.password(), request.pin());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("token", tokens.issue(user.getTenantId(), user.getId(), user.getRoleId()));
    result.put("user", user(user));
    result.put("tenant_id", user.getTenantId());
    result.putAll(invitations.property(user.getTenantId()));
    return result;
  }

  @PostMapping("/staff-invitations")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Map<String, Object>> invite(@jakarta.validation.Valid @RequestBody InviteStaff request) {
    Map<String, Object> result = approvals.invite(request.name(), request.phone(), request.email(), request.roleIds());
    return Boolean.TRUE.equals(result.get("approval_required")) ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
  }

  @GetMapping("/staff-invitations")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public List<Map<String, Object>> pendingInvitations() { return invitations.pending(); }

  @PostMapping("/staff-invitations/{id}/resend")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public Map<String, Boolean> resendInvitation(@PathVariable UUID id) {
    invitations.resend(id);
    return Map.of("sent", true);
  }

  @DeleteMapping("/staff-invitations/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public Map<String, Boolean> revokeInvitation(@PathVariable UUID id) {
    invitations.revoke(id);
    return Map.of("revoked", true);
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(
      @RequestHeader("X-Tenant-Id") UUID tenant, @jakarta.validation.Valid @RequestBody LoginRequest r) {
    StaffUser u = service.authenticate(tenant, r.identity(), r.password());
    return Map.of("token", tokens.issue(tenant, u.getId(), u.getRoleId()), "user", user(u));
  }

  @GetMapping("/auth/me")
  public Map<String, Object> currentUser() {
    return service.currentUser();
  }

  @PostMapping("/auth/password-reset/request")
  public ResponseEntity<Map<String, String>> requestPasswordReset(
      @jakarta.validation.Valid @RequestBody PasswordResetRequest request) {
    passwordResets.request(request.tenantCode(), request.email());
    return ResponseEntity.accepted()
        .body(Map.of("message", "If the account exists, password reset instructions were sent"));
  }

  @PostMapping("/auth/password-reset/confirm")
  public Map<String, String> confirmPasswordReset(@jakarta.validation.Valid @RequestBody PasswordResetConfirm request) {
    passwordResets.confirm(request.tenantCode(), request.token(), request.newPassword());
    return Map.of("message", "Password has been reset; please log in again");
  }

  @PostMapping("/auth/email-verification/confirm")
  public Map<String, String> confirmEmail(@jakarta.validation.Valid @RequestBody EmailVerificationConfirm request) {
    emailVerifications.confirm(request.token());
    return Map.of("message", "Email verified");
  }

  @GetMapping("/users")
  @PreAuthorize("hasAnyAuthority('PERM_STAFF_READ', 'PERM_STAFF_MANAGE')")
  public List<Map<String, Object>> users() {
    return service.listWithRoles();
  }

  @PatchMapping("/user/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody UpdateUser r) {
    Map<String, Object> result = approvals.update(id, r.name(),
        r.roleIds() != null ? r.roleIds() : r.roleId() == null ? null : List.of(r.roleId()), r.active());
    return Boolean.TRUE.equals(result.get("approval_required")) ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
  }

  @PatchMapping("/user/{id}/printer-access")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public Map<String,Boolean> printerAccess(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody Map<String,Boolean> body) {
    service.printerAccess(id,Boolean.TRUE.equals(body.get("enabled")));
    return Map.of("saved",true);
  }

  @PostMapping("/user/{id}/verify-pin")
  @PreAuthorize("hasAuthority('PERM_APPROVAL_PIN_VERIFY')")
  public Map<String, Boolean> pin(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody PinRequest r) {
    return Map.of("valid", service.verifyPin(id, r.pin()));
  }

  private Map<String, Object> user(StaffUser u) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", u.getId());
    m.put("name", u.getName());
    m.put("phone", u.getPhone());
    m.put("email", u.getEmail());
    m.put("role_id", u.getRoleId());
    m.put("role_ids", u.getRoleIds());
    m.put("active", u.isActive());
    return m;
  }

  public record LoginRequest(String phone, String email, String password) {
    public String identity() {
      return phone != null ? phone : email;
    }
  }

  public record InviteStaff(String name, String phone, String email,
      @JsonProperty("role_ids") List<UUID> roleIds) {}

  public record AcceptInvitation(String token, String password, String pin) {}

  public record PasswordResetRequest(
      @JsonProperty("tenant_code") String tenantCode, String email) {}

  public record PasswordResetConfirm(
      @JsonProperty("tenant_code") String tenantCode,
      String token,
      @JsonProperty("new_password") String newPassword) {}

  public record EmailVerificationConfirm(String token) {}

  public record UpdateUser(String name, @JsonProperty("role_id") UUID roleId, Boolean active,
      @JsonProperty("role_ids") List<UUID> roleIds) {}

  public record PinRequest(String pin) {}
}
