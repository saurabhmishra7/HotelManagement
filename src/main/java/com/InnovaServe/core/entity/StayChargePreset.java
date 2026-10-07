package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stay_charge_preset", schema = "core")
public class StayChargePreset extends TenantEntity {
  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  protected StayChargePreset() {}

  public StayChargePreset(UUID tenantId, String description, BigDecimal amount) {
    super(tenantId);
    update(description, amount);
  }

  public void update(String description, BigDecimal amount) {
    this.description = description;
    this.amount = amount;
  }

  public String getDescription() { return description; }
  public BigDecimal getAmount() { return amount; }
}
