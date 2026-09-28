package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.DiningTable;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {
  List<DiningTable> findAllByTenantIdOrderByTableNumber(UUID t);

  Optional<DiningTable> findByTenantIdAndId(UUID t, UUID id);

  boolean existsByTenantIdAndTableNumber(UUID t, String tableNumber);
}
