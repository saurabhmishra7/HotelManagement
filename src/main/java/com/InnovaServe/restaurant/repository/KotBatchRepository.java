package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.KotBatch;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KotBatchRepository extends JpaRepository<KotBatch, UUID> {
  List<KotBatch> findAllByTenantIdAndOrderIdOrderByBatchNumber(UUID t, UUID order);

  Optional<KotBatch> findByTenantIdAndId(UUID t, UUID id);

  List<KotBatch> findAllByTenantId(UUID tenantId);

  @Query("select b from KotBatch b where b.tenantId = :tenantId "
      + "and (:station is null or b.station = :station) "
      + "and exists (select i.id from OrderItem i where i.tenantId = :tenantId "
      + "and i.kotBatchId = b.id and i.status not in ('ready', 'served', 'cancelled')) "
      + "order by b.batchNumber")
  List<KotBatch> findPendingByTenantIdAndStation(
      @Param("tenantId") UUID tenantId, @Param("station") String station);
}
