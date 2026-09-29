package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Tenant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByTenantCodeIgnoreCase(String tenantCode);
}
