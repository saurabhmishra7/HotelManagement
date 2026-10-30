package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StaffInvitation;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {
  List<StaffInvitation> findAllByTenantIdAndAcceptedAtIsNullOrderByCreatedAtDesc(UUID tenantId);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<StaffInvitation> findByTenantIdAndIdAndAcceptedAtIsNull(UUID tenantId, UUID id);
  boolean existsByTenantIdAndPhoneAndAcceptedAtIsNull(UUID tenantId, String phone);
  boolean existsByTenantIdAndEmailIgnoreCaseAndAcceptedAtIsNull(UUID tenantId, String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from StaffInvitation i where i.tokenHash = :hash and i.acceptedAt is null")
  Optional<StaffInvitation> lockPendingByTokenHash(@Param("hash") String hash);
}
