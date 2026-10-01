package com.InnovaServe.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log", schema = "platform")
public class PlatformAuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "admin_id")
  private UUID adminId;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(nullable = false, length = 60)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 40)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_value", columnDefinition = "jsonb")
  private Map<String, Object> beforeValue;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_value", columnDefinition = "jsonb")
  private Map<String, Object> afterValue;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  protected PlatformAuditLog() {}

  public PlatformAuditLog(
      UUID adminId,
      String actorType,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> beforeValue,
      Map<String, Object> afterValue) {
    this.adminId = adminId;
    this.actorType = actorType;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.beforeValue = beforeValue;
    this.afterValue = afterValue;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public String getActorType() {
    return actorType;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public Map<String, Object> getBeforeValue() {
    return beforeValue;
  }

  public Map<String, Object> getAfterValue() {
    return afterValue;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
