package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.OrderItem;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
  List<OrderItem> findAllByTenantIdAndOrderId(UUID t, UUID order);

  List<OrderItem> findAllByTenantIdAndKotBatchIdOrderByPlacedAtAscIdAsc(UUID t, UUID batch);

  boolean existsByTenantIdAndKotBatchIdAndStatusNotIn(UUID tenantId, UUID batchId, Collection<String> statuses);

  Optional<OrderItem> findByTenantIdAndOrderIdAndId(UUID t, UUID order, UUID id);

  Optional<OrderItem> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from OrderItem i where i.tenantId = :tenantId and i.id = :id")
  Optional<OrderItem> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  List<OrderItem> findAllByTenantIdAndServedAtGreaterThanEqualOrderByServedAtDesc(
      UUID tenantId, java.time.LocalDateTime servedAt);
}
