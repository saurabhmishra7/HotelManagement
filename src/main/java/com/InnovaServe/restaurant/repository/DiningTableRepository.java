package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.DiningTable;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiningTableRepository extends JpaRepository<DiningTable, UUID> {
  List<DiningTable> findAllByTenantIdOrderByTableNumber(UUID t);

  Optional<DiningTable> findByTenantIdAndId(UUID t, UUID id);

  List<DiningTable> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from DiningTable t where t.tenantId = :tenantId and t.id = :id")
  Optional<DiningTable> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  boolean existsByTenantIdAndStationAndStatusNot(UUID tenantId, String station, String status);

  boolean existsByTenantIdAndTableNumber(UUID t, String tableNumber);

  boolean existsByTenantIdAndTableNumberAndIdNot(UUID t, String tableNumber, UUID id);
}
