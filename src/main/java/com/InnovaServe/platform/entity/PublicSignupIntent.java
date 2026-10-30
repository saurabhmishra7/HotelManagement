package com.InnovaServe.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "public_signup_intent", schema = "platform")
public class PublicSignupIntent {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "property_name", nullable = false, length = 200)
  private String propertyName;

  @Column(length = 15)
  private String gstin;

  @Column(columnDefinition = "text")
  private String address;

  @Column(name = "owner_name", nullable = false, length = 100)
  private String ownerName;

  @Column(name = "owner_phone", nullable = false, length = 15)
  private String ownerPhone;

  @Column(name = "owner_email", nullable = false, length = 150)
  private String ownerEmail;

  @Column(name = "owner_password_hash", columnDefinition = "text")
  private String ownerPasswordHash;

  @Column(name = "owner_pin_hash", columnDefinition = "text")
  private String ownerPinHash;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "payment_order_id", unique = true, length = 80)
  private String paymentOrderId;

  @Column(name = "signup_mode", nullable = false, length = 10)
  private String signupMode = "standard";

  @Column(name = "email_verification_token_hash", length = 64)
  private String emailVerificationTokenHash;

  @Column(name = "email_verification_expires_at")
  private Instant emailVerificationExpiresAt;

  @Column(name = "email_verification_sent_at")
  private Instant emailVerificationSentAt;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "phone_verified_at")
  private Instant phoneVerifiedAt;

  @Column(name = "continuation_token_hash", length = 64)
  private String continuationTokenHash;

  @Column(nullable = false, length = 20)
  private String status = "pending";

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  protected PublicSignupIntent() {}

  public PublicSignupIntent(
      String propertyName,
      String gstin,
      String address,
      String ownerName,
      String ownerPhone,
      String ownerEmail,
      String ownerPasswordHash,
      String ownerPinHash,
      UUID planId,
      BigDecimal amount,
      String currency,
      String paymentOrderId,
      Instant expiresAt) {
    this.propertyName = propertyName;
    this.gstin = gstin;
    this.address = address;
    this.ownerName = ownerName;
    this.ownerPhone = ownerPhone;
    this.ownerEmail = ownerEmail;
    this.ownerPasswordHash = ownerPasswordHash;
    this.ownerPinHash = ownerPinHash;
    this.planId = planId;
    this.amount = amount;
    this.currency = currency;
    this.paymentOrderId = paymentOrderId;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public String getPropertyName() { return propertyName; }
  public String getGstin() { return gstin; }
  public String getAddress() { return address; }
  public String getOwnerName() { return ownerName; }
  public String getOwnerPhone() { return ownerPhone; }
  public String getOwnerEmail() { return ownerEmail; }
  public String getOwnerPasswordHash() { return ownerPasswordHash; }
  public String getOwnerPinHash() { return ownerPinHash; }
  public UUID getPlanId() { return planId; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public String getPaymentOrderId() { return paymentOrderId; }
  public String getSignupMode() { return signupMode; }
  public String getEmailVerificationTokenHash() { return emailVerificationTokenHash; }
  public Instant getEmailVerificationSentAt() { return emailVerificationSentAt; }
  public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
  public Instant getPhoneVerifiedAt() { return phoneVerifiedAt; }
  public String getContinuationTokenHash() { return continuationTokenHash; }
  public String getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getTenantId() { return tenantId; }

  public static PublicSignupIntent pendingVerification(
      String propertyName, String gstin, String address, String ownerName,
      String ownerPhone, String ownerEmail, String ownerPasswordHash, String ownerPinHash,
      UUID planId, BigDecimal amount, String currency, String signupMode, Instant expiresAt) {
    PublicSignupIntent intent = new PublicSignupIntent(propertyName, gstin, address, ownerName,
        ownerPhone, ownerEmail, ownerPasswordHash, ownerPinHash, planId, amount, currency, null, expiresAt);
    intent.signupMode = signupMode;
    intent.status = "email_pending";
    return intent;
  }

  public boolean issueEmailToken(String tokenHash, Instant now, Instant tokenExpiry) {
    if (!"email_pending".equals(status) || !expiresAt.isAfter(now)) return false;
    if (emailVerificationSentAt != null && emailVerificationSentAt.isAfter(now.minusSeconds(60))) return false;
    emailVerificationTokenHash = tokenHash;
    emailVerificationSentAt = now;
    emailVerificationExpiresAt = tokenExpiry;
    return true;
  }

  public boolean verifyEmail(String tokenHash, String continuationHash, Instant now) {
    if (!"email_pending".equals(status) || !expiresAt.isAfter(now)
        || emailVerificationExpiresAt == null || !emailVerificationExpiresAt.isAfter(now)
        || !tokenHash.equals(emailVerificationTokenHash)) return false;
    emailVerifiedAt = now;
    emailVerificationTokenHash = null;
    emailVerificationExpiresAt = null;
    continuationTokenHash = continuationHash;
    status = "email_verified";
    return true;
  }

  public boolean canContinue(String hash, Instant now) {
    return continuationTokenHash != null && continuationTokenHash.equals(hash)
        && expiresAt.isAfter(now) && ("email_verified".equals(status) || "payment_pending".equals(status));
  }

  public void verifyPhone(Instant now) { phoneVerifiedAt = now; }

  public void beginPayment(String orderId, Instant now) {
    if (!"email_verified".equals(status) || emailVerifiedAt == null || !expiresAt.isAfter(now))
      throw new IllegalStateException("Verify your email before starting payment");
    paymentOrderId = orderId;
    status = "payment_pending";
  }

  public boolean complete(UUID tenant) {
    if (!("pending".equals(status) || "email_verified".equals(status)
        || "payment_pending".equals(status)) || !expiresAt.isAfter(Instant.now())) return false;
    status = "completed";
    tenantId = tenant;
    ownerPasswordHash = null;
    ownerPinHash = null;
    emailVerificationTokenHash = null;
    continuationTokenHash = null;
    return true;
  }

  public void expire() {
    if (!"completed".equals(status)) status = "expired";
    ownerPasswordHash = null;
    ownerPinHash = null;
    emailVerificationTokenHash = null;
    continuationTokenHash = null;
  }
}
