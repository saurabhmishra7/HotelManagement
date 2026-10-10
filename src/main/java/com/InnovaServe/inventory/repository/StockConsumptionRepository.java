package com.InnovaServe.inventory.repository;
import com.InnovaServe.inventory.entity.StockConsumption;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StockConsumptionRepository extends JpaRepository<StockConsumption, UUID> {
  List<StockConsumption> findAllByTenantIdAndItemIdOrderByCreatedAtDesc(UUID tenantId, UUID itemId);
  void deleteAllByTenantIdAndItemId(UUID tenantId, UUID itemId);
}
