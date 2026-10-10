package com.InnovaServe.inventory.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_ingredient", schema = "restaurant")
public class RecipeIngredient {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "recipe_id", nullable = false) private UUID recipeId;
  @Column(name = "inventory_item_id", nullable = false) private UUID inventoryItemId;
  @Column(name = "quantity_required", nullable = false, precision = 10, scale = 3) private BigDecimal quantityRequired;
  protected RecipeIngredient() {}
  public RecipeIngredient(UUID recipeId, UUID inventoryItemId, BigDecimal quantityRequired) { this.recipeId = recipeId; this.inventoryItemId = inventoryItemId; this.quantityRequired = quantityRequired; }
  public UUID getId() { return id; }
  public UUID getRecipeId() { return recipeId; }
  public UUID getInventoryItemId() { return inventoryItemId; }
  public BigDecimal getQuantityRequired() { return quantityRequired; }
}
