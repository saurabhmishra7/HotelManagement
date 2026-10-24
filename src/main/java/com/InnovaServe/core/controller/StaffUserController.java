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

  public StaffUserController(
      StaffUserService s, StaffApprovalService approvals, TokenService tokens, PasswordResetService passwordResets,
      EmailVerificationService emailVerifications) {
    service = s;
    this.approvals = approvals;
    this.tokens = tokens;
    this.passwordResets = passwordResets;
    this.emailVerifications = emailVerifications;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(
      @RequestHeader("X-Tenant-Id") UUID tenant, @RequestBody LoginRequest r) {
    StaffUser u = service.authenticate(tenant, r.identity(), r.password());
    return Map.of("token", tokens.issue(tenant, u.getId(), u.getRoleId()), "user", user(u));
  }

  @GetMapping("/auth/me")
  public Map<String, Object> currentUser() {
    return service.currentUser();
  }

  @PostMapping("/auth/password-reset/request")
  public ResponseEntity<Map<String, String>> requestPasswordReset(
      @RequestBody PasswordResetRequest request) {
    passwordResets.request(request.tenantCode(), request.email());
    return ResponseEntity.accepted()
        .body(Map.of("message", "If the account exists, password reset instructions were sent"));
  }

  @PostMapping("/auth/password-reset/confirm")
  public Map<String, String> confirmPasswordReset(@RequestBody PasswordResetConfirm request) {
    passwordResets.confirm(request.tenantCode(), request.token(), request.newPassword());
    return Map.of("message", "Password has been reset; please log in again");
  }

  @PostMapping("/auth/email-verification/confirm")
  public Map<String, String> confirmEmail(@RequestBody EmailVerificationConfirm request) {
    emailVerifications.confirm(request.token());
    return Map.of("message", "Email verified");
  }

  @GetMapping("/users")
  @PreAuthorize("hasAnyAuthority('PERM_STAFF_READ', 'PERM_STAFF_MANAGE')")
  public List<Map<String, Object>> users() {
    return service.listWithRoles();
  }

  @PostMapping("/create/user")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Map<String, Object>> create(@RequestBody CreateUser r) {
    Map<String, Object> result = approvals.create(r.name(), r.phone(), r.email(), r.password(), r.pin(),
        r.roleIds() != null ? r.roleIds() : r.roleId() == null ? List.of() : List.of(r.roleId()));
    return Boolean.TRUE.equals(result.get("approval_required")) ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
  }

  @PatchMapping("/user/{id}")
  @PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
  public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id, @RequestBody UpdateUser r) {
    Map<String, Object> result = approvals.update(id, r.name(),
        r.roleIds() != null ? r.roleIds() : r.roleId() == null ? null : List.of(r.roleId()), r.active());
    return Boolean.TRUE.equals(result.get("approval_required")) ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
  }

  @PostMapping("/user/{id}/verify-pin")
  @PreAuthorize("hasAuthority('PERM_APPROVAL_PIN_VERIFY')")
  public Map<String, Boolean> pin(@PathVariable UUID id, @RequestBody PinRequest r) {
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

  public record PasswordResetRequest(
      @JsonProperty("tenant_code") String tenantCode, String email) {}

  public record PasswordResetConfirm(
      @JsonProperty("tenant_code") String tenantCode,
      String token,
      @JsonProperty("new_password") String newPassword) {}

  public record EmailVerificationConfirm(String token) {}

  public record CreateUser(
      String name,
      String phone,
      String email,
      String password,
      @JsonProperty("role_id") UUID roleId,
      String pin, @JsonProperty("role_ids") List<UUID> roleIds) {}

  public record UpdateUser(String name, @JsonProperty("role_id") UUID roleId, Boolean active,
      @JsonProperty("role_ids") List<UUID> roleIds) {}

  public record PinRequest(String pin) {}
}
