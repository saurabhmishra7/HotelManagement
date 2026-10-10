package com.InnovaServe.inventory.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_purchase", schema = "inventory")
public class StockPurchase extends TenantEntity {
  @Column(name = "item_id", nullable = false) private UUID itemId;
  @Column(nullable = false, precision = 10, scale = 3) private BigDecimal quantity;
  @Column(name = "unit_cost", nullable = false, precision = 10, scale = 2) private BigDecimal unitCost;
  @Column(name = "total_cost", nullable = false, precision = 10, scale = 2) private BigDecimal totalCost;
  @Column(name = "vendor_name", length = 150) private String vendorName;
  @Column(name = "expense_id") private UUID expenseId;
  @Column(name = "purchased_by", nullable = false) private UUID purchasedBy;
  @Column(name = "purchased_at", insertable = false, updatable = false) private Instant purchasedAt;
  protected StockPurchase() {}
  public StockPurchase(UUID tenantId, UUID itemId, BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, String vendorName, UUID expenseId, UUID purchasedBy) {
    super(tenantId); this.itemId = itemId; this.quantity = quantity; this.unitCost = unitCost; this.totalCost = totalCost; this.vendorName = vendorName; this.expenseId = expenseId; this.purchasedBy = purchasedBy;
  }
  public UUID getItemId() { return itemId; }
  public BigDecimal getQuantity() { return quantity; }
  public BigDecimal getUnitCost() { return unitCost; }
  public BigDecimal getTotalCost() { return totalCost; }
  public String getVendorName() { return vendorName; }
  public UUID getExpenseId() { return expenseId; }
  public UUID getPurchasedBy() { return purchasedBy; }
  public Instant getPurchasedAt() { return purchasedAt; }
}
