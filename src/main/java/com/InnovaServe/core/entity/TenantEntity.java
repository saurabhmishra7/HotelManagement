package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.util.UUID;

@MappedSuperclass
public abstract class TenantEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  protected UUID id;

  @Column(name = "tenant_id", nullable = false)
  protected UUID tenantId;

  protected TenantEntity() {}

  protected TenantEntity(UUID tenantId) {
    this.tenantId = tenantId;
  }

  protected TenantEntity(UUID id, UUID tenantId) {
    this.id = id;
    this.tenantId = tenantId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }
}
