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
@Table(name = "tenant_subscription_checkout", schema = "platform")
public class TenantSubscriptionCheckout {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(name = "initiated_by", nullable = false) private UUID initiatedBy;
  @Column(name = "plan_id", nullable = false) private UUID planId;
  @Column(name = "subscription_request_id") private UUID subscriptionRequestId;
  @Column(nullable = false, length = 10) private String action;
  @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "payment_order_id", nullable = false, unique = true, length = 80)
  private String paymentOrderId;
  @Column(nullable = false, length = 20) private String status = "pending";
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

  protected TenantSubscriptionCheckout() {}

  public TenantSubscriptionCheckout(UUID tenantId, UUID initiatedBy, UUID planId,
      String action, BigDecimal amount, String currency, String paymentOrderId, Instant expiresAt) {
    this(tenantId, initiatedBy, planId, null, action, amount, currency, paymentOrderId, expiresAt);
  }

  public TenantSubscriptionCheckout(UUID tenantId, UUID initiatedBy, UUID planId,
      UUID subscriptionRequestId, String action, BigDecimal amount, String currency,
      String paymentOrderId, Instant expiresAt) {
    this.tenantId = tenantId;
    this.initiatedBy = initiatedBy;
    this.planId = planId;
    this.subscriptionRequestId = subscriptionRequestId;
    this.action = action;
    this.amount = amount;
    this.currency = currency;
    this.paymentOrderId = paymentOrderId;
    this.expiresAt = expiresAt;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getInitiatedBy() { return initiatedBy; }
  public UUID getPlanId() { return planId; }
  public UUID getSubscriptionRequestId() { return subscriptionRequestId; }
  public String getAction() { return action; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public String getPaymentOrderId() { return paymentOrderId; }
  public String getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }

  public boolean complete(Instant now) {
    if (!"pending".equals(status) || !expiresAt.isAfter(now)) return false;
    status = "completed";
    return true;
  }

  public void expire() {
    if ("pending".equals(status)) status = "expired";
  }
}
