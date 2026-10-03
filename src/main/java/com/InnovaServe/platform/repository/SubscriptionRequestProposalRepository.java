package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.SubscriptionRequestProposal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRequestProposalRepository
    extends JpaRepository<SubscriptionRequestProposal, UUID> {
  List<SubscriptionRequestProposal> findAllByRequestIdOrderByCreatedAtAsc(UUID requestId);

  List<SubscriptionRequestProposal> findAllByRequestIdAndStatus(
      UUID requestId, String status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from SubscriptionRequestProposal p where p.id = :id")
  Optional<SubscriptionRequestProposal> lockById(@Param("id") UUID id);
}
