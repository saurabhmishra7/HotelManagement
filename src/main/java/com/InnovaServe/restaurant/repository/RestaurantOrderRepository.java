package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {
  List<RestaurantOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID t);

  List<RestaurantOrder> findAllByTenantIdAndOrderTypeAndStatusAndTableIdIsNotNullOrderByCreatedAtDesc(
      UUID tenantId, String orderType, String status);

  @Query("""
      select restaurantOrder from RestaurantOrder restaurantOrder
      where restaurantOrder.tenantId = :tenantId and restaurantOrder.orderType = 'dine_in'
        and restaurantOrder.status = 'billed' and restaurantOrder.tableId in :tableIds
        and restaurantOrder.createdAt = (
          select max(previous.createdAt) from RestaurantOrder previous
          where previous.tenantId = :tenantId and previous.orderType = 'dine_in'
            and previous.status = 'billed' and previous.tableId = restaurantOrder.tableId)
      order by restaurantOrder.createdAt desc
      """)
  List<RestaurantOrder> findLatestBilledDineInOrdersForTables(
      @Param("tenantId") UUID tenantId, @Param("tableIds") Collection<UUID> tableIds);

  Optional<RestaurantOrder> findFirstByTenantIdAndTableIdAndStatusOrderByCreatedAtDesc(
      UUID tenantId, UUID tableId, String status);

  boolean existsByTenantIdAndTableIdAndStatus(UUID tenantId, UUID tableId, String status);

  List<RestaurantOrder> findAllByTenantIdAndStayIdOrderByCreatedAtDesc(UUID t, UUID stayId);

  Optional<RestaurantOrder> findByTenantIdAndId(UUID t, UUID id);

  List<RestaurantOrder> findAllByTenantIdAndOrderSourceAndConfirmationStatusOrderByCreatedAt(
      UUID t, String source, String status);

  boolean existsByTenantIdAndStatus(UUID tenantId, String status);
}
