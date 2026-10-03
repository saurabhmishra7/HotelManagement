package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.StaffUserRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(48);

  private final StaffUserRepository users;
  private final ObjectProvider<JavaMailSender> mailSenders;
  private final String mailHost;
  private final String senderAddress;
  private final String verificationBaseUrl;

  public EmailVerificationService(
      StaffUserRepository users,
      ObjectProvider<JavaMailSender> mailSenders,
      @Value("${spring.mail.host:}") String mailHost,
      @Value("${app.mail.from:no-reply@innova.example}") String senderAddress,
      @Value("${app.email-verification.base-url:http://localhost:5173/}") String verificationBaseUrl) {
    this.users = users;
    this.mailSenders = mailSenders;
    this.mailHost = mailHost;
    this.senderAddress = senderAddress;
    this.verificationBaseUrl = verificationBaseUrl;
  }

  @Transactional
  public void send(StaffUser user, String tenantCode) {
    if (user.getEmail() == null || user.getEmail().isBlank()) return;
    JavaMailSender sender = mailSenders.getIfAvailable();
    if (sender == null || mailHost == null || mailHost.isBlank()) {
      log.info("Email verification is not configured for staff user {}", user.getId());
      return;
    }
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now();
    user.issueEmailVerification(hash(token), now.plus(TOKEN_LIFETIME));
    users.save(user);
    String separator = verificationBaseUrl.contains("?") ? "&" : "?";
    String link = verificationBaseUrl + separator + "tenant_code="
        + URLEncoder.encode(tenantCode, StandardCharsets.UTF_8)
        + "&verify_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(senderAddress);
    message.setTo(user.getEmail());
    message.setSubject("Verify your InnovaServe email");
    message.setText("Your InnovaServe account is ready. Verify your email using this link "
        + "within 48 hours:\n\n" + link
        + "\n\nVerification is optional and does not affect access to your account.");
    try {
      sender.send(message);
    } catch (MailException exception) {
      log.error("Could not send verification email for staff user {}", user.getId(), exception);
    }
  }

  @Transactional
  public void confirm(String token) {
    if (token == null || token.isBlank())
      throw new IllegalArgumentException("Verification token is required");
    String tokenHash = hash(token);
    StaffUser user = users.findByEmailVerificationTokenHashAndActiveTrue(tokenHash)
        .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired"));
    if (!user.verifyEmail(tokenHash, Instant.now()))
      throw new IllegalArgumentException("Verification link is invalid or expired");
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not hash email verification token", exception);
    }
  }
}
