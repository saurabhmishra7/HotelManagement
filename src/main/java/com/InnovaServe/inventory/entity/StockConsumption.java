package com.InnovaServe.inventory.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_consumption", schema = "inventory")
public class StockConsumption extends TenantEntity {
  @Column(name = "item_id", nullable = false) private UUID itemId;
  @Column(nullable = false, precision = 10, scale = 3) private BigDecimal quantity;
  @Column(name = "consumption_type", nullable = false, length = 30) private String consumptionType;
  @Column(name = "reference_type", length = 20) private String referenceType;
  @Column(name = "reference_id") private UUID referenceId;
  @Column(columnDefinition = "text") private String note;
  @Column(name = "created_by") private UUID createdBy;
  @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
  protected StockConsumption() {}
  public StockConsumption(UUID tenantId, UUID itemId, BigDecimal quantity, String type, String referenceType, UUID referenceId, String note, UUID createdBy) {
    super(tenantId); this.itemId = itemId; this.quantity = quantity; this.consumptionType = type; this.referenceType = referenceType; this.referenceId = referenceId; this.note = note; this.createdBy = createdBy;
  }
  public UUID getItemId() { return itemId; }
  public BigDecimal getQuantity() { return quantity; }
  public String getConsumptionType() { return consumptionType; }
  public String getReferenceType() { return referenceType; }
  public UUID getReferenceId() { return referenceId; }
  public String getNote() { return note; }
  public UUID getCreatedBy() { return createdBy; }
  public Instant getCreatedAt() { return createdAt; }
}
