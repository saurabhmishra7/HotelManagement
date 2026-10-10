package com.InnovaServe.inventory.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "recipe", schema = "restaurant")
public class Recipe extends TenantEntity {
  @Column(name = "menu_item_id", nullable = false, unique = true) private UUID menuItemId;
  protected Recipe() {}
  public Recipe(UUID tenantId, UUID menuItemId) { super(tenantId); this.menuItemId = menuItemId; }
  public UUID getMenuItemId() { return menuItemId; }
}
