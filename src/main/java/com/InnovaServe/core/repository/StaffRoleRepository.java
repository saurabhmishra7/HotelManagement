package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StaffRole;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRoleRepository extends JpaRepository<StaffRole, UUID> {
  List<StaffRole> findAllByTenantIdOrderByName(UUID tenantId);

  Optional<StaffRole> findByTenantIdAndId(UUID tenantId, UUID id);

  List<StaffRole> findAllByTenantIdAndNameOrderById(UUID tenantId, String name);
}
