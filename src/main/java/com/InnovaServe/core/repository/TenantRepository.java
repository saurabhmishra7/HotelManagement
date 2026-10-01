package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Tenant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByTenantCodeIgnoreCase(String tenantCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Tenant t where t.id = :id")
  Optional<Tenant> lockById(@Param("id") UUID id);

  @Query(
      "select t from Tenant t where "
          + "(:search is null or lower(t.name) like lower(concat('%', :search, '%')) "
          + "or lower(t.tenantCode) like lower(concat('%', :search, '%'))) and "
          + "(:status is null or "
          + "(:status = 'legacy' and not exists "
          + "(select s.id from TenantSubscription s where s.tenantId = t.id)) or "
          + "exists (select s.id from TenantSubscription s where s.tenantId = t.id "
          + "and s.status = :status))")
  Page<Tenant> searchPlatformTenants(
      @Param("search") String search,
      @Param("status") String status,
      Pageable pageable);
}
