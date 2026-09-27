package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.MenuCategory;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
  List<MenuCategory> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

  Optional<MenuCategory> findByTenantIdAndId(UUID t, UUID id);
}
