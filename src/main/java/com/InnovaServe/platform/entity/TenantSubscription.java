package com.InnovaServe.platform.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tenant_subscription", schema = "platform")
public class TenantSubscription {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "plan_id", nullable = false)
  private UUID planId;

  @Column(name = "plan_price_at_time", nullable = false, precision = 10, scale = 2)
  private BigDecimal planPriceAtTime;

  @Column(name = "price_paid", nullable = false, precision = 10, scale = 2)
  private BigDecimal pricePaid;

  @Column(name = "negotiation_note", columnDefinition = "text")
  private String negotiationNote;

  @Column(name = "starts_on", nullable = false)
  private LocalDate startsOn;

  @Column(name = "expires_on", nullable = false)
  private LocalDate expiresOn;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "is_trial", nullable = false)
  private boolean trial;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "tenant_subscription_module",
      schema = "platform",
      joinColumns = @JoinColumn(name = "subscription_id"))
  @Column(name = "module", nullable = false, length = 20)
  private Set<String> modules = new HashSet<>();

  protected TenantSubscription() {}

  public TenantSubscription(
      UUID tenantId,
      UUID planId,
      BigDecimal planPriceAtTime,
      BigDecimal pricePaid,
      String negotiationNote,
      LocalDate startsOn,
      LocalDate expiresOn,
      String status,
      UUID createdBy,
      Set<String> modules,
      boolean trial) {
    this.tenantId = tenantId;
    this.planId = planId;
    this.planPriceAtTime = planPriceAtTime;
    this.pricePaid = pricePaid;
    this.negotiationNote = negotiationNote;
    this.startsOn = startsOn;
    this.expiresOn = expiresOn;
    this.status = status;
    this.trial = trial;
    this.createdBy = createdBy;
    this.modules = new HashSet<>(modules);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPlanId() {
    return planId;
  }

  public BigDecimal getPlanPriceAtTime() {
    return planPriceAtTime;
  }

  public BigDecimal getPricePaid() {
    return pricePaid;
  }

  public String getNegotiationNote() {
    return negotiationNote;
  }

  public LocalDate getStartsOn() {
    return startsOn;
  }

  public LocalDate getExpiresOn() {
    return expiresOn;
  }

  public String getStatus() {
    return status;
  }

  public boolean isTrial() {
    return trial;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Set<String> getModules() {
    return Set.copyOf(modules);
  }

  public void replaceModules(Set<String> modules) {
    this.modules = new HashSet<>(modules);
  }

  public void activate() {
    status = "active";
    cancelledAt = null;
  }

  public void markCancelling() {
    status = "cancelling";
    cancelledAt = Instant.now();
  }

  public void expire() {
    status = "expired";
  }

  public void cancel() {
    status = "cancelled";
    cancelledAt = Instant.now();
  }
}
