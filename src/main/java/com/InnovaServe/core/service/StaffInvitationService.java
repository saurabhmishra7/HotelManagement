package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffInvitationService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration LIFETIME = Duration.ofHours(48);
  private final StaffInvitationRepository invitations;
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;
  private final TenantRepository tenants;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;
  private final EmailSender email;
  private final String baseUrl;

  public StaffInvitationService(StaffInvitationRepository invitations, StaffUserRepository users,
      StaffRoleRepository roles, TenantRepository tenants, TenantContext tenant,
      PasswordEncoder encoder, EmailSender email,
      @Value("${app.staff-invitation.base-url:${APP_STAFF_INVITATION_BASE_URL:http://localhost:3000/invite}}") String baseUrl) {
    this.invitations = invitations; this.users = users; this.roles = roles;
    this.tenants = tenants; this.tenant = tenant; this.encoder = encoder;
    this.email = email; this.baseUrl = baseUrl;
  }

  @Transactional
  public StaffInvitation issue(String name, String phone, String emailAddress, Collection<UUID> roleIds) {
    if (!email.isAvailable()) throw new EmailDeliveryException("Staff invitation email is not configured");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Staff name is required");
    AccountValidation.requireMobile(phone);
    AccountValidation.requireEmail(emailAddress);
    if (roleIds == null || roleIds.isEmpty() || roles.findAllByTenantIdAndIdIn(tenant.tenantId(), roleIds).size() != new HashSet<>(roleIds).size())
      throw new IllegalArgumentException("Choose valid roles for this property");
    if (users.existsByTenantIdAndPhone(tenant.tenantId(), phone)
        || invitations.existsByTenantIdAndPhoneAndAcceptedAtIsNull(tenant.tenantId(), phone))
      throw new IllegalArgumentException("This phone already has an account or pending invitation");
    if (users.existsByTenantIdAndEmailIgnoreCase(tenant.tenantId(), emailAddress)
        || invitations.existsByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNull(tenant.tenantId(), emailAddress))
      throw new IllegalArgumentException("This email already has an account or pending invitation");
    String token = newToken();
    StaffInvitation invitation = invitations.save(new StaffInvitation(tenant.tenantId(), tenant.userId(),
        name.trim(), phone, emailAddress.trim(), roleIds, hash(token), Instant.now().plus(LIFETIME)));
    send(invitation, token);
    return invitation;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> pending() {
    return invitations.findAllByTenantIdAndAcceptedAtIsNullOrderByCreatedAtDesc(tenant.tenantId())
        .stream().map(this::summary).toList();
  }

  public boolean hasPendingPhone(String phone) {
    return invitations.existsByTenantIdAndPhoneAndAcceptedAtIsNull(tenant.tenantId(), phone);
  }

  @Transactional
  public void resend(UUID id) {
    if (!email.isAvailable()) throw new EmailDeliveryException("Staff invitation email is not configured");
    StaffInvitation invitation = invitations.findByTenantIdAndIdAndAcceptedAtIsNull(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Pending invitation not found"));
    if (users.existsByTenantIdAndPhone(tenant.tenantId(), invitation.getPhone()))
      throw new IllegalStateException("This staff account already exists");
    String token = newToken();
    invitation.renew(hash(token), Instant.now().plus(LIFETIME));
    send(invitation, token);
  }

  @Transactional
  public void revoke(UUID id) {
    StaffInvitation invitation = invitations.findByTenantIdAndIdAndAcceptedAtIsNull(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Pending invitation not found"));
    invitations.delete(invitation);
  }

  @Transactional
  public StaffUser accept(String token, String password, String pin) {
    AccountValidation.requirePassword(password, 12);
    if (pin == null || !pin.matches("[0-9]{4,8}"))
      throw new IllegalArgumentException("PIN must contain 4 to 8 digits");
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Invitation link is invalid or expired");
    StaffInvitation invitation = invitations.lockPendingByTokenHash(hash(token))
        .filter(row -> row.matches(hash(token), Instant.now()))
        .orElseThrow(() -> new IllegalArgumentException("Invitation link is invalid or expired"));
    if (users.existsByTenantIdAndPhone(invitation.getTenantId(), invitation.getPhone())
        || users.existsByTenantIdAndEmailIgnoreCase(invitation.getTenantId(), invitation.getEmail()))
      throw new IllegalStateException("This staff account already exists");
    List<UUID> roleIds = invitation.getRoleIds();
    if (roleIds.isEmpty() || roles.findAllByTenantIdAndIdIn(invitation.getTenantId(), roleIds).size() != roleIds.size())
      throw new IllegalStateException("Assigned roles have changed. Ask your manager for a new invitation.");
    StaffUser user = new StaffUser(invitation.getTenantId(), invitation.getName(), invitation.getPhone(),
        invitation.getEmail(), encoder.encode(password), encoder.encode(pin), roleIds.get(0));
    user.setRoleIds(roleIds);
    user.markEmailVerified(Instant.now());
    users.saveAndFlush(user);
    invitation.accept(Instant.now());
    return user;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary(StaffInvitation invitation) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", invitation.getId()); result.put("name", invitation.getName());
    result.put("phone", invitation.getPhone()); result.put("email", invitation.getEmail());
    result.put("expires_at", invitation.getExpiresAt());
    result.put("role_names", roles.findAllByTenantIdAndIdIn(invitation.getTenantId(), invitation.getRoleIds())
        .stream().map(StaffRole::getName).sorted().toList());
    return result;
  }

  public Map<String, String> property(UUID tenantId) {
    Tenant property = tenants.findById(tenantId).orElseThrow();
    return Map.of("tenant_code", property.getTenantCode(), "tenant_name", property.getName());
  }

  private void send(StaffInvitation invitation, String token) {
    String propertyName = property(invitation.getTenantId()).get("tenant_name");
    String separator = baseUrl.contains("?") ? "&" : "?";
    String link = baseUrl + separator + "invite_token=" + token;
    email.send(invitation.getEmail(), "Join " + propertyName + " on InnovaServe",
        "You've been invited to join " + propertyName + " on InnovaServe. Open this link to set your password and PIN:\n\n"
        + link + "\n\nThis link expires in 48 hours and can be used once. If you weren't expecting this, ignore this email.");
  }

  private static String newToken() {
    byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }
}
