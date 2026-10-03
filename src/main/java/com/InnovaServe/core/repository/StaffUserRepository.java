package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StaffUser;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {
  Optional<StaffUser> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<StaffUser> findByTenantIdAndPhoneAndActiveTrue(UUID tenantId, String phone);

  Optional<StaffUser> findByTenantIdAndEmailAndActiveTrue(UUID tenantId, String email);

  Optional<StaffUser> findByTenantIdAndEmailIgnoreCaseAndActiveTrue(UUID tenantId, String email);

  Optional<StaffUser> findByTenantIdAndPasswordResetTokenHashAndActiveTrue(
      UUID tenantId, String tokenHash);

  Optional<StaffUser> findByEmailVerificationTokenHashAndActiveTrue(String tokenHash);

  @Query("select case when count(u) > 0 then true else false end from StaffUser u, StaffRole r "
      + "where u.roleId = r.id and lower(u.email) = lower(:email) "
      + "and upper(r.name) in ('OWNER', 'HOTEL_ADMIN')")
  boolean existsOwnerEmailIgnoreCase(@Param("email") String email);

  List<StaffUser> findAllByTenantIdOrderByName(UUID tenantId);

  boolean existsByTenantIdAndPhone(UUID tenantId, String phone);
}
