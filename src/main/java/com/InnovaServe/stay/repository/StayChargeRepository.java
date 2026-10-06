package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.StayCharge;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StayChargeRepository extends JpaRepository<StayCharge, UUID> {
  List<StayCharge> findAllByTenantIdAndStayId(UUID tenantId, UUID stayId);

  List<StayCharge> findAllByTenantIdAndStayIdIn(UUID tenantId, Collection<UUID> stayIds);
}
