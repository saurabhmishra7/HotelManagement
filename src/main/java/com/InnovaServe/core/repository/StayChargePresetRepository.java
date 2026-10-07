package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StayChargePreset;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StayChargePresetRepository extends JpaRepository<StayChargePreset, UUID> {
  List<StayChargePreset> findAllByTenantIdOrderByDescriptionAsc(UUID tenantId);
  Optional<StayChargePreset> findByTenantIdAndId(UUID tenantId, UUID id);
}
