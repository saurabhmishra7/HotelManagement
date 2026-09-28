package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "qr_code",
    schema = "core",
    uniqueConstraints =
        @UniqueConstraint(name = "uq_qrcode_tenant_token", columnNames = {"tenant_id", "token"}))
public class QRCode extends TenantEntity {
  @Column(name = "target_type", nullable = false, length = 20)
  private String targetType;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(nullable = false, length = 64)
  private String token;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "rotated_at")
  private LocalDateTime rotatedAt;

  protected QRCode() {}

  public QRCode(UUID tenantId, String targetType, UUID targetId, String token) {
    super(tenantId);
    this.targetType = targetType;
    this.targetId = targetId;
    this.token = token;
  }

  public String getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public String getToken() { return token; }
  public boolean isActive() { return active; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getRotatedAt() { return rotatedAt; }
}
