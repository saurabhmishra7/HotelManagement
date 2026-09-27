package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log", schema = "core")
public class AuditLog extends TenantEntity {
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 50)
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
  private LocalDateTime createdAt;

  protected AuditLog() {}

  public AuditLog(
      UUID t,
      UUID user,
      String action,
      String type,
      UUID entity,
      Map<String, Object> before,
      Map<String, Object> after) {
    super(t);
    userId = user;
    this.action = action;
    entityType = type;
    entityId = entity;
    beforeValue = before;
    afterValue = after;
  }

  public UUID getUserId() {
    return userId;
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

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
