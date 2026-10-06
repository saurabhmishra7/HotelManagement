package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.Stay;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StayRepository extends JpaRepository<Stay, UUID> {
  Optional<Stay> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stay s where s.tenantId = :tenantId and s.id = :id")
  Optional<Stay> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  Optional<Stay> findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
      UUID tenantId, UUID roomId, String status);

  List<Stay> findAllByTenantIdAndStatusOrderByCheckInAtDesc(UUID tenantId, String status);

  List<Stay> findAllByTenantIdAndAccountId(UUID tenantId, UUID accountId);

  List<Stay> findAllByTenantIdAndCustomerIdOrderByCheckInAtDesc(
      UUID tenantId, UUID customerId);

  boolean existsByTenantIdAndStatus(UUID tenantId, String status);
}
