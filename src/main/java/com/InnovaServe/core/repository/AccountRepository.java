package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.tenantId = :tenantId and a.id = :id")
  Optional<Account> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  Optional<Account> findByTenantIdAndLinkedEntityTypeAndLinkedEntityIdAndStatus(
      UUID tenantId, String type, UUID entityId, String status);
}
