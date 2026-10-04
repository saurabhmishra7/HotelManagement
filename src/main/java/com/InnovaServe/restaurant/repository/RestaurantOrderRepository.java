package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {
  List<RestaurantOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID t);

  List<RestaurantOrder> findAllByTenantIdAndOrderTypeAndStatusAndTableIdIsNotNullOrderByCreatedAtDesc(
      UUID tenantId, String orderType, String status);

  Optional<RestaurantOrder> findFirstByTenantIdAndTableIdAndStatusOrderByCreatedAtDesc(
      UUID tenantId, UUID tableId, String status);

  boolean existsByTenantIdAndTableIdAndStatus(UUID tenantId, UUID tableId, String status);

  List<RestaurantOrder> findAllByTenantIdAndStayIdOrderByCreatedAtDesc(UUID t, UUID stayId);

  Optional<RestaurantOrder> findByTenantIdAndId(UUID t, UUID id);

  List<RestaurantOrder> findAllByTenantIdAndOrderSourceAndConfirmationStatusOrderByCreatedAt(
      UUID t, String source, String status);

  boolean existsByTenantIdAndStatus(UUID tenantId, String status);
}
