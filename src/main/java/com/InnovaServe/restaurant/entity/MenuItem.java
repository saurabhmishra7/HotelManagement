package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "menu_item", schema = "restaurant")
public class MenuItem extends TenantEntity {
  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(name = "item_code", length = 40)
  private String itemCode;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(name = "tax_rule_id")
  private UUID taxRuleId;

  @Column(nullable = false, length = 10)
  private String station;

  @Column(name = "veg_flag", nullable = false)
  private boolean vegFlag;

  @Column(nullable = false)
  private boolean active = true;

  protected MenuItem() {}

  public MenuItem(
      UUID t, UUID cat, String name, String itemCode, BigDecimal price, UUID tax, String station, boolean veg) {
    super(t);
    categoryId = cat;
    this.name = name;
    this.itemCode = itemCode;
    this.price = price;
    taxRuleId = tax;
    this.station = station;
    vegFlag = veg;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getName() {
    return name;
  }

  public String getItemCode() {
    return itemCode;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public UUID getTaxRuleId() {
    return taxRuleId;
  }

  public String getStation() {
    return station;
  }

  public boolean isVegFlag() {
    return vegFlag;
  }

  public boolean isActive() {
    return active;
  }

  public void update(
      UUID categoryId,
      String name,
      String itemCode,
      BigDecimal price,
      UUID taxRuleId,
      Boolean clearTaxRule,
      String station,
      Boolean vegFlag,
      Boolean active) {
    if (categoryId != null) this.categoryId = categoryId;
    if (name != null) this.name = name;
    if (itemCode != null) this.itemCode = itemCode.isBlank() ? null : itemCode;
    if (price != null) this.price = price;
    if (Boolean.TRUE.equals(clearTaxRule)) this.taxRuleId = null;
    else if (taxRuleId != null) this.taxRuleId = taxRuleId;
    if (station != null) this.station = station;
    if (vegFlag != null) this.vegFlag = vegFlag;
    if (active != null) this.active = active;
  }
}
