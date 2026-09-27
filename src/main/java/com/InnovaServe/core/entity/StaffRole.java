package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "role", schema = "core")
public class StaffRole {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 50)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> permissions;

  protected StaffRole() {}

  public StaffRole(UUID tenantId, String name, List<String> permissions) {
    this.tenantId = tenantId;
    this.name = name;
    this.permissions = permissions;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public List<String> getPermissions() {
    return permissions;
  }
}
