package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account", schema = "core")
public class Account extends TenantEntity {
  @Column(name = "opened_by_module", nullable = false, length = 10)
  private String openedByModule;

  @Column(name = "linked_entity_type", length = 30)
  private String linkedEntityType;

  @Column(name = "linked_entity_id")
  private UUID linkedEntityId;

  @Column(nullable = false, length = 10)
  private String status;

  @Column(name = "opened_at", insertable = false, updatable = false)
  private LocalDateTime openedAt;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  protected Account() {}

  public Account(UUID tenant, String module, String entityType, UUID entityId) {
    super(tenant);
    openedByModule = module;
    linkedEntityType = entityType;
    linkedEntityId = entityId;
    status = "open";
  }

  public Account(UUID id, UUID tenant, String module, String entityType, UUID entityId) {
    super(id, tenant);
    openedByModule = module;
    linkedEntityType = entityType;
    linkedEntityId = entityId;
    status = "open";
  }

  public String getStatus() {
    return status;
  }

  public UUID getLinkedEntityId() {
    return linkedEntityId;
  }

  public void close() {
    status = "closed";
    closedAt = LocalDateTime.now();
  }
}
