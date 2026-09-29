package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(20);

  private final TenantRepository tenants;
  private final StaffUserRepository users;
  private final PasswordEncoder passwords;
  private final ObjectProvider<JavaMailSender> mailSenders;
  private final String mailHost;
  private final String senderAddress;
  private final String resetBaseUrl;

  public PasswordResetService(
      TenantRepository tenants,
      StaffUserRepository users,
      PasswordEncoder passwords,
      ObjectProvider<JavaMailSender> mailSenders,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${app.mail.from:no-reply@innova.example}") String senderAddress,
      @Value("${app.password-reset.base-url:http://localhost:3000/reset-password}")
          String resetBaseUrl) {
    this.tenants = tenants;
    this.users = users;
    this.passwords = passwords;
    this.mailSenders = mailSenders;
    this.mailHost = mailHost;
    this.senderAddress = senderAddress;
    this.resetBaseUrl = resetBaseUrl;
  }

  @Transactional
  public void request(String tenantCode, String email) {
    if (tenantCode == null || tenantCode.isBlank() || email == null || email.isBlank())
      throw new IllegalArgumentException("tenant_code and email are required");
    JavaMailSender mailSender = mailSenders.getIfAvailable();
    if (mailHost == null || mailHost.isBlank() || mailSender == null)
      throw new PasswordResetUnavailableException();

    var tenant = tenants.findByTenantCodeIgnoreCase(tenantCode.trim()).orElse(null);
    if (tenant == null) return;
    StaffUser user =
        users
            .findByTenantIdAndEmailIgnoreCaseAndActiveTrue(tenant.getId(), email.trim())
            .orElse(null);
    if (user == null || user.getEmail() == null || user.getEmail().isBlank()) return;

    byte[] randomBytes = new byte[32];
    RANDOM.nextBytes(randomBytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    Instant now = Instant.now();
    if (!user.issuePasswordReset(hash(token), now.plus(TOKEN_LIFETIME), now)) return;
    users.save(user);

    String link =
        resetBaseUrl
            + "?tenant_code="
            + URLEncoder.encode(tenant.getTenantCode(), StandardCharsets.UTF_8)
            + "&token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(senderAddress);
    message.setTo(user.getEmail());
    message.setSubject("Reset your InnovaServe password");
    message.setText(
        "Use this one-time link to reset your password. It expires in 20 minutes:\n\n"
            + link
            + "\n\nIf you did not request this, you can ignore this email.");
    try {
      mailSender.send(message);
    } catch (MailException ex) {
      user.clearPasswordReset();
      log.error("Could not send password reset email for user {}", user.getId(), ex);
    }
  }

  @Transactional
  public void confirm(String tenantCode, String token, String newPassword) {
    if (tenantCode == null || tenantCode.isBlank())
      throw new IllegalArgumentException("tenant_code is required");
    if (newPassword == null || newPassword.length() < 12)
      throw new IllegalArgumentException("Password must be at least 12 characters");
    if (token == null || token.isBlank())
      throw new IllegalArgumentException("Reset token is required");
    var tenant =
        tenants
            .findByTenantCodeIgnoreCase(tenantCode.trim())
            .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid or expired"));
    StaffUser user =
        users
            .findByTenantIdAndPasswordResetTokenHashAndActiveTrue(tenant.getId(), hash(token))
            .orElseThrow(() -> new IllegalArgumentException("Reset token is invalid or expired"));
    if (!user.resetPassword(hash(token), passwords.encode(newPassword), Instant.now()))
      throw new IllegalArgumentException("Reset token is invalid or expired");
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not hash password reset token", ex);
    }
  }

  public static class PasswordResetUnavailableException extends RuntimeException {
    public PasswordResetUnavailableException() {
      super("Password reset email is not configured");
    }
  }
}
