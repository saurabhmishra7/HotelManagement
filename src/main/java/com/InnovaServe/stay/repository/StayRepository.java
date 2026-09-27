package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.Stay;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StayRepository extends JpaRepository<Stay, UUID> {
  Optional<Stay> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Stay> findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
      UUID tenantId, UUID roomId, String status);

  List<Stay> findAllByTenantIdAndAccountId(UUID tenantId, UUID accountId);
}
