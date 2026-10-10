package com.InnovaServe.inventory.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "item", schema = "inventory")
public class InventoryItem extends TenantEntity {
  @Column(nullable = false, length = 150) private String name;
  @Column(nullable = false, length = 20) private String unit;
  @Column(name = "usage_type", nullable = false, length = 20) private String usageType;
  @Column(name = "current_stock", nullable = false, precision = 10, scale = 3) private BigDecimal currentStock = BigDecimal.ZERO;
  @Column(name = "average_unit_cost", nullable = false, precision = 10, scale = 2) private BigDecimal averageUnitCost = BigDecimal.ZERO;
  @Column(name = "reorder_threshold", nullable = false, precision = 10, scale = 3) private BigDecimal reorderThreshold = BigDecimal.ZERO;
  @Column(nullable = false) private boolean active = true;

  protected InventoryItem() {}
  public InventoryItem(UUID tenantId, String name, String unit, String usageType, BigDecimal reorderThreshold) {
    super(tenantId);
    this.name = name;
    this.unit = unit;
    this.usageType = usageType;
    this.reorderThreshold = reorderThreshold;
  }
  public String getName() { return name; }
  public String getUnit() { return unit; }
  public String getUsageType() { return usageType; }
  public BigDecimal getCurrentStock() { return currentStock; }
  public BigDecimal getAverageUnitCost() { return averageUnitCost; }
  public BigDecimal getReorderThreshold() { return reorderThreshold; }
  public boolean isActive() { return active; }
  public void update(String name, String unit, String usageType, BigDecimal threshold, Boolean active) {
    if (name != null) this.name = name;
    if (unit != null) this.unit = unit;
    if (usageType != null) this.usageType = usageType;
    if (threshold != null) reorderThreshold = threshold;
    if (active != null) this.active = active;
  }
  public void purchase(BigDecimal quantity, BigDecimal unitCost) {
    BigDecimal oldStock = currentStock;
    BigDecimal newStock = oldStock.add(quantity);
    if (oldStock.signum() > 0 && newStock.signum() > 0) {
      averageUnitCost = oldStock.multiply(averageUnitCost).add(quantity.multiply(unitCost))
          .divide(newStock, 2, RoundingMode.HALF_UP);
    } else {
      // Negative on-hand can occur after automatic service deductions; a restock first clears that deficit.
      averageUnitCost = unitCost.setScale(2, RoundingMode.HALF_UP);
    }
    currentStock = newStock;
  }
  public void changeStock(BigDecimal delta) { currentStock = currentStock.add(delta); }
}
