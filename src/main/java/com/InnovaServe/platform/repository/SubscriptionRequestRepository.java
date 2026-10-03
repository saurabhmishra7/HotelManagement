package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.SubscriptionRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRequestRepository
    extends JpaRepository<SubscriptionRequest, UUID> {

  boolean existsByTenantIdAndStatusIn(UUID tenantId, List<String> statuses);

  List<SubscriptionRequest> findAllByTenantIdOrderBySubmittedAtDesc(UUID tenantId);

  List<SubscriptionRequest> findAllByStatusOrderBySubmittedAtAsc(String status);
}
