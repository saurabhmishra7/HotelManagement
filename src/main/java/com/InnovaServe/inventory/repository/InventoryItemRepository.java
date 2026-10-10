package com.InnovaServe.inventory.repository;

import com.InnovaServe.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
  List<InventoryItem> findAllByTenantIdOrderByName(UUID tenantId);
  Optional<InventoryItem> findByTenantIdAndId(UUID tenantId, UUID id);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from InventoryItem i where i.tenantId = :tenant and i.id = :id")
  Optional<InventoryItem> lockByTenantIdAndId(@Param("tenant") UUID tenantId, @Param("id") UUID id);
  @Query("select i from InventoryItem i where i.tenantId = :tenant and i.active = true and i.currentStock <= i.reorderThreshold order by i.name")
  List<InventoryItem> findLowStockByTenant(@Param("tenant") UUID tenantId);
}
