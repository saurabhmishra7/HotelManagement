package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "station", schema = "restaurant")
public class RestaurantStation extends TenantEntity {
  @Column(nullable = false, length = 32)
  private String code;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false)
  private boolean active = true;

  protected RestaurantStation() {}

  public RestaurantStation(UUID tenantId, String code, String name) {
    super(tenantId);
    this.code = code;
    this.name = name;
  }

  public String getCode() { return code; }
  public String getName() { return name; }
  public boolean isActive() { return active; }
  public void update(String name, boolean active) { this.name = name; this.active = active; }
}
