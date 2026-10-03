package com.InnovaServe.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant", schema = "core")
public class Tenant {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "tenant_code", nullable = false, unique = true, length = 12)
  private String tenantCode;

  @Column(length = 15)
  private String gstin;

  @Column(columnDefinition = "text")
  private String address;

  @Column(name = "primary_owner_email", length = 150)
  private String primaryOwnerEmail;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Tenant() {}

  public Tenant(String name, String gstin, String address) {
    this.name = name;
    this.tenantCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    this.gstin = gstin;
    this.address = address;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTenantCode() {
    return tenantCode;
  }

  public String getGstin() {
    return gstin;
  }

  public String getAddress() {
    return address;
  }

  public String getPrimaryOwnerEmail() { return primaryOwnerEmail; }

  public void setPrimaryOwnerEmail(String email) {
    this.primaryOwnerEmail = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
