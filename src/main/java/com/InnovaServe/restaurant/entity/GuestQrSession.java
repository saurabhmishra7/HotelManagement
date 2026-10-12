package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_qr_session", schema = "restaurant")
public class GuestQrSession extends TenantEntity {
  @Column(name = "qr_code_id", nullable = false) private UUID qrCodeId;
  @Column(name = "target_type", nullable = false, length = 20) private String targetType;
  @Column(name = "target_id", nullable = false) private UUID targetId;
  @Column(name = "token_hash", nullable = false, length = 64, unique = true) private String tokenHash;
  @Column(nullable = false, length = 10) private String status = "active";
  @Column(name = "closed_reason", length = 20) private String closedReason;
  @Column(name = "created_at", insertable = false, updatable = false) private LocalDateTime createdAt;
  @Column(name = "closed_at") private LocalDateTime closedAt;

  protected GuestQrSession() {}

  public GuestQrSession(UUID tenantId, UUID qrCodeId, String targetType, UUID targetId, String tokenHash) {
    super(tenantId);
    this.qrCodeId = qrCodeId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.tokenHash = tokenHash;
  }

  public UUID getQrCodeId() { return qrCodeId; }
  public String getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public String getStatus() { return status; }
  public String getClosedReason() { return closedReason; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getClosedAt() { return closedAt; }
  public void close(String reason) {
    if (!"active".equals(status)) return;
    status = "closed";
    closedReason = reason;
    closedAt = LocalDateTime.now();
  }
}
