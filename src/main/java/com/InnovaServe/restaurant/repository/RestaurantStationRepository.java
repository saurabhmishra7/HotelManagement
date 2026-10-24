package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantStation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantStationRepository extends JpaRepository<RestaurantStation, UUID> {
  List<RestaurantStation> findAllByTenantIdOrderByNameAsc(UUID tenantId);
  Optional<RestaurantStation> findByTenantIdAndCode(UUID tenantId, String code);
}
