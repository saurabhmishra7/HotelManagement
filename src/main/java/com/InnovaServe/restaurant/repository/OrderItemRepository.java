package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.OrderItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  List<OrderItem> findAllByTenantIdAndOrderId(UUID t, UUID order);

  List<OrderItem> findAllByTenantIdAndKotBatchIdOrderByPlacedAtAscIdAsc(UUID t, UUID batch);

  Optional<OrderItem> findByTenantIdAndOrderIdAndId(UUID t, UUID order, UUID id);

  Optional<OrderItem> findByTenantIdAndId(UUID tenantId, UUID id);

  List<OrderItem> findAllByTenantIdAndServedAtGreaterThanEqualOrderByServedAtDesc(
      UUID tenantId, java.time.LocalDateTime servedAt);
}
