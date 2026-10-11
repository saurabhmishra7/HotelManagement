package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.RoomServiceRequest;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomServiceRequestRepository extends JpaRepository<RoomServiceRequest, UUID> {
  List<RoomServiceRequest> findAllByTenantIdOrderByRequestedAtDesc(UUID tenantId);
  List<RoomServiceRequest> findAllByTenantIdAndGuestSessionIdOrderByRequestedAtDesc(UUID tenantId, UUID guestSessionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RoomServiceRequest r where r.tenantId=:tenantId and r.id=:id")
  Optional<RoomServiceRequest> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
