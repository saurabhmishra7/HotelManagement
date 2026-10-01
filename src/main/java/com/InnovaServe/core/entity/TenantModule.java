package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "tenant_module",
    schema = "core",
    uniqueConstraints = @UniqueConstraint(name = "uq_tenant_module", columnNames = {"tenant_id", "module"}))
public class TenantModule {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 20)
  private String module;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "activated_at", nullable = false)
  private Instant activatedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  protected TenantModule() {}

  public TenantModule(UUID tenantId, String module, boolean active) {
    this.tenantId = tenantId;
    this.module = module;
    this.status = active ? "active" : "cancelled";
    this.activatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getModule() {
    return module;
  }

  public String getStatus() {
    return status;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void activate() {
    if (!"active".equals(status)) activatedAt = Instant.now();
    status = "active";
    expiresAt = null;
  }

  public void grantUntil(Instant expiresAt) {
    if (!"active".equals(status)) activatedAt = Instant.now();
    status = "active";
    this.expiresAt = expiresAt;
  }

  public void suspend() {
    status = "suspended";
  }

  public void cancel() {
    status = "cancelled";
  }
}
