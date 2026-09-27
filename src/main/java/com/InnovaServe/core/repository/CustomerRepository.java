package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Customer> findByTenantIdAndPhone(UUID tenantId, String phone);

  Page<Customer> findAllByTenantId(UUID tenantId, Pageable pageable);
}
