package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.MenuItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
  List<MenuItem> findAllByTenantIdOrderByName(UUID t);

  List<MenuItem> findAllByTenantIdAndCategoryIdOrderByName(UUID t, UUID cat);

  Optional<MenuItem> findByTenantIdAndId(UUID t, UUID id);

  List<MenuItem> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

  Optional<MenuItem> findByTenantIdAndItemCodeIgnoreCase(UUID tenantId, String itemCode);
}
