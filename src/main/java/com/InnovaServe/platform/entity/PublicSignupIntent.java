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

  @Column(name = "payment_order_id", nullable = false, unique = true, length = 80)
  private String paymentOrderId;

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
  public String getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getTenantId() { return tenantId; }

  public boolean complete(UUID tenant) {
    if (!"pending".equals(status) || !expiresAt.isAfter(Instant.now())) return false;
    status = "completed";
    tenantId = tenant;
    ownerPasswordHash = null;
    ownerPinHash = null;
    return true;
  }

  public void expire() {
    if ("pending".equals(status)) status = "expired";
    ownerPasswordHash = null;
    ownerPinHash = null;
  }
}
