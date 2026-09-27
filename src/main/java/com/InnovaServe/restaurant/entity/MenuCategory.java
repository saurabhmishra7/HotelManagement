package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "menu_category", schema = "restaurant")
public class MenuCategory extends TenantEntity {
  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "sort_order", nullable = false)
  private short sortOrder;

  protected MenuCategory() {}

  public MenuCategory(UUID t, String name, short order) {
    super(t);
    this.name = name;
    sortOrder = order;
  }

  public String getName() {
    return name;
  }

  public short getSortOrder() {
    return sortOrder;
  }
}
