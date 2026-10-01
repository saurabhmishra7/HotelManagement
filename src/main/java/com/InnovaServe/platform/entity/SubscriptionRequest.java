package com.InnovaServe.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_request", schema = "platform")
public class SubscriptionRequest {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "current_subscription_id")
  private UUID currentSubscriptionId;

  @Column(name = "requested_plan_id", nullable = false)
  private UUID requestedPlanId;

  @Column(name = "request_type", nullable = false, length = 24)
  private String requestType;

  @Column(length = 1000)
  private String message;

  @Column(nullable = false, length = 20)
  private String status = "pending";

  @Column(name = "response_note", length = 1000)
  private String responseNote;

  @Column(name = "submitted_at", insertable = false, updatable = false)
  private Instant submittedAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  protected SubscriptionRequest() {}

  public SubscriptionRequest(
      UUID tenantId,
      UUID requestedBy,
      UUID currentSubscriptionId,
      UUID requestedPlanId,
      String requestType,
      String message) {
    this.id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.requestedBy = requestedBy;
    this.currentSubscriptionId = currentSubscriptionId;
    this.requestedPlanId = requestedPlanId;
    this.requestType = requestType;
    this.message = message;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public UUID getCurrentSubscriptionId() {
    return currentSubscriptionId;
  }

  public UUID getRequestedPlanId() {
    return requestedPlanId;
  }

  public String getRequestType() {
    return requestType;
  }

  public String getMessage() {
    return message;
  }

  public String getStatus() {
    return status;
  }

  public String getResponseNote() {
    return responseNote;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void updateStatus(String status, String responseNote, UUID adminId) {
    this.status = status;
    this.responseNote = responseNote;
    this.resolvedBy = adminId;
    this.resolvedAt = "completed".equals(status) || "declined".equals(status)
        ? Instant.now()
        : null;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @PreUpdate
  void markUpdated() {
    updatedAt = Instant.now();
  }
}
