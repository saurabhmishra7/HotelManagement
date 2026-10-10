package com.InnovaServe.inventory.repository;
import com.InnovaServe.inventory.entity.Recipe;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
  Optional<Recipe> findByTenantIdAndMenuItemId(UUID tenantId, UUID menuItemId);
}
