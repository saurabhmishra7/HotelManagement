package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.TenantModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, UUID> {
  List<TenantModule> findAllByTenantId(UUID tenantId);

  Optional<TenantModule> findByTenantIdAndModule(UUID tenantId, String module);
}
