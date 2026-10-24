package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.StaffApprovalRequest;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface StaffApprovalRequestRepository extends JpaRepository<StaffApprovalRequest, UUID> {
  List<StaffApprovalRequest> findAllByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
  List<StaffApprovalRequest> findAllByTenantIdAndRequestedByAndStatusOrderByCreatedAtDesc(UUID tenantId, UUID requestedBy, String status);
  Optional<StaffApprovalRequest> findByTenantIdAndId(UUID tenantId, UUID id);
  boolean existsByTenantIdAndPhoneAndStatus(UUID tenantId, String phone, String status);
  boolean existsByTenantIdAndTargetUserIdAndStatus(UUID tenantId, UUID targetUserId, String status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from StaffApprovalRequest r where r.tenantId = :tenantId and r.id = :id")
  Optional<StaffApprovalRequest> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
