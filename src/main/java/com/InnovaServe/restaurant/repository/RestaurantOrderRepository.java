package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantOrderRepository extends JpaRepository<RestaurantOrder, UUID> {
  List<RestaurantOrder> findAllByTenantIdOrderByCreatedAtDesc(UUID t);

  Optional<RestaurantOrder> findByTenantIdAndId(UUID t, UUID id);

  List<RestaurantOrder> findAllByTenantIdAndOrderSourceAndConfirmationStatusOrderByCreatedAt(
      UUID t, String source, String status);
}
