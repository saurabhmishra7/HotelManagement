package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Account;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Account> findByTenantIdAndLinkedEntityTypeAndLinkedEntityIdAndStatus(
      UUID tenantId, String type, UUID entityId, String status);
}
