package com.InnovaServe.inventory.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "room_par_item", schema = "stay")
public class RoomParItem extends TenantEntity {
  @Column(name = "room_type", nullable = false, length = 50) private String roomType;
  @Column(name = "inventory_item_id", nullable = false) private UUID inventoryItemId;
  @Column(name = "quantity_per_clean", nullable = false, precision = 10, scale = 3) private BigDecimal quantityPerClean;
  protected RoomParItem() {}
  public RoomParItem(UUID tenantId, String roomType, UUID inventoryItemId, BigDecimal quantity) { super(tenantId); this.roomType = roomType; this.inventoryItemId = inventoryItemId; this.quantityPerClean = quantity; }
  public String getRoomType() { return roomType; }
  public UUID getInventoryItemId() { return inventoryItemId; }
  public BigDecimal getQuantityPerClean() { return quantityPerClean; }
}
