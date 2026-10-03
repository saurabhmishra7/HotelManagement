package com.InnovaServe.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscription_request_proposal", schema = "platform")
public class SubscriptionRequestProposal {
  @Id
  private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "calculation_type", nullable = false, length = 20)
  private String calculationType;

  @Column(name = "new_plan_id", nullable = false)
  private UUID newPlanId;

  @Column(name = "new_price_paid", nullable = false, precision = 10, scale = 2)
  private BigDecimal newPricePaid;

  @Column(name = "new_expires_on", nullable = false)
  private LocalDate newExpiresOn;

  @Column(name = "extra_charge_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal extraChargeAmount;

  @Column(nullable = false, length = 20)
  private String status = "offered";

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  protected SubscriptionRequestProposal() {}

  public SubscriptionRequestProposal(
      UUID requestId,
      String calculationType,
      UUID newPlanId,
      BigDecimal newPricePaid,
      LocalDate newExpiresOn,
      BigDecimal extraChargeAmount,
      UUID createdBy) {
    this.id = UUID.randomUUID();
    this.requestId = requestId;
    this.calculationType = calculationType;
    this.newPlanId = newPlanId;
    this.newPricePaid = newPricePaid;
    this.newExpiresOn = newExpiresOn;
    this.extraChargeAmount = extraChargeAmount;
    this.createdBy = createdBy;
  }

  public UUID getId() { return id; }
  public UUID getRequestId() { return requestId; }
  public String getCalculationType() { return calculationType; }
  public UUID getNewPlanId() { return newPlanId; }
  public BigDecimal getNewPricePaid() { return newPricePaid; }
  public LocalDate getNewExpiresOn() { return newExpiresOn; }
  public BigDecimal getExtraChargeAmount() { return extraChargeAmount; }
  public String getStatus() { return status; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }

  public void accept() {
    status = "accepted";
  }

  public void supersede() {
    if ("offered".equals(status)) status = "superseded";
  }
}
