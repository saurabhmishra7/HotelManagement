package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Locale;
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

  @Column(nullable = false, length = 32)
  private String station;

  @Column(name = "veg_flag", nullable = false)
  private boolean vegFlag;

  @Column(name = "food_type", nullable = false, length = 10)
  private String foodType = "VEG";

  @Column(nullable = false)
  private boolean active = true;

  protected MenuItem() {}

  public MenuItem(
      UUID t, UUID cat, String name, String itemCode, BigDecimal price, UUID tax, String station, boolean veg, String foodType) {
    super(t);
    categoryId = cat;
    this.name = name;
    this.itemCode = itemCode;
    this.price = price;
    taxRuleId = tax;
    this.station = station;
    setFoodType(foodType == null ? (veg ? "VEG" : "NON_VEG") : foodType);
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

  public String getFoodType() {
    return foodType;
  }

  private void setFoodType(String value) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!normalized.equals("VEG") && !normalized.equals("NON_VEG") && !normalized.equals("EGG") && !normalized.equals("NONE")) {
      throw new IllegalArgumentException("Dietary type must be VEG, NON_VEG, EGG, or NONE");
    }
    foodType = normalized;
    vegFlag = normalized.equals("VEG");
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
      String foodType,
      Boolean active) {
    if (categoryId != null) this.categoryId = categoryId;
    if (name != null) this.name = name;
    if (itemCode != null) this.itemCode = itemCode.isBlank() ? null : itemCode;
    if (price != null) this.price = price;
    if (Boolean.TRUE.equals(clearTaxRule)) this.taxRuleId = null;
    else if (taxRuleId != null) this.taxRuleId = taxRuleId;
    if (station != null) this.station = station;
    if (foodType != null) setFoodType(foodType);
    else if (vegFlag != null) setFoodType(vegFlag ? "VEG" : "NON_VEG");
    if (active != null) this.active = active;
  }
}
