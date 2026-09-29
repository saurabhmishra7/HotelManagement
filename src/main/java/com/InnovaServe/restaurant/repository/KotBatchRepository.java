package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.KotBatch;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KotBatchRepository extends JpaRepository<KotBatch, UUID> {
  List<KotBatch> findAllByTenantIdAndOrderIdOrderByBatchNumber(UUID t, UUID order);

  Optional<KotBatch> findByTenantIdAndId(UUID t, UUID id);

  List<KotBatch> findAllByTenantIdAndPrintedAtIsNull(UUID tenantId);
}
