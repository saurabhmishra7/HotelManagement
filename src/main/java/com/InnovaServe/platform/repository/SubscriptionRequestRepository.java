package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.SubscriptionRequest;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRequestRepository
    extends JpaRepository<SubscriptionRequest, UUID> {

  boolean existsByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

  List<SubscriptionRequest> findAllByTenantIdOrderBySubmittedAtDesc(UUID tenantId);

  List<SubscriptionRequest> findAllByStatusOrderBySubmittedAtAsc(String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from SubscriptionRequest r where r.id = :id")
  Optional<SubscriptionRequest> lockById(@Param("id") UUID id);

  java.util.Optional<SubscriptionRequest>
      findFirstByTenantIdAndRequestTypeAndRequestedPlanIdAndStatusInOrderBySubmittedAtAsc(
          UUID tenantId, String requestType, UUID requestedPlanId, List<String> statuses);
}
