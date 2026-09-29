package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StaffUser;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {
  Optional<StaffUser> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<StaffUser> findByTenantIdAndPhoneAndActiveTrue(UUID tenantId, String phone);

  Optional<StaffUser> findByTenantIdAndEmailAndActiveTrue(UUID tenantId, String email);

  Optional<StaffUser> findByTenantIdAndEmailIgnoreCaseAndActiveTrue(UUID tenantId, String email);

  Optional<StaffUser> findByTenantIdAndPasswordResetTokenHashAndActiveTrue(
      UUID tenantId, String tokenHash);

  List<StaffUser> findAllByTenantIdOrderByName(UUID tenantId);

  boolean existsByTenantIdAndPhone(UUID tenantId, String phone);
}
