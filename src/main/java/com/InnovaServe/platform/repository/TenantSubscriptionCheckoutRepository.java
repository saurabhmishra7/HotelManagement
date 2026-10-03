package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.TenantSubscriptionCheckout;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionCheckoutRepository
    extends JpaRepository<TenantSubscriptionCheckout, UUID> {
  boolean existsBySubscriptionRequestIdAndStatus(UUID subscriptionRequestId, String status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from TenantSubscriptionCheckout c where c.id = :id")
  Optional<TenantSubscriptionCheckout> lockById(@Param("id") UUID id);

  List<TenantSubscriptionCheckout> findAllByStatusAndExpiresAtBefore(String status, Instant now);
}
