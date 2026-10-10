package com.InnovaServe.inventory.repository;
import com.InnovaServe.inventory.entity.RecipeIngredient;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, UUID> {
  List<RecipeIngredient> findAllByRecipeId(UUID recipeId);
  void deleteAllByRecipeId(UUID recipeId);
  void deleteAllByInventoryItemId(UUID inventoryItemId);
}
