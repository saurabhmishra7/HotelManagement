package com.InnovaServe.inventory.repository;
import com.InnovaServe.inventory.entity.StockPurchase;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StockPurchaseRepository extends JpaRepository<StockPurchase, UUID> {
  List<StockPurchase> findAllByTenantIdAndItemIdOrderByPurchasedAtDesc(UUID tenantId, UUID itemId);
  void deleteAllByTenantIdAndItemId(UUID tenantId, UUID itemId);
}
