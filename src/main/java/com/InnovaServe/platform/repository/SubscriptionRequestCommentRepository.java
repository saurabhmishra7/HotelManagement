package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.SubscriptionRequestComment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRequestCommentRepository
    extends JpaRepository<SubscriptionRequestComment, UUID> {
  List<SubscriptionRequestComment> findAllByRequestIdOrderByCreatedAtAscIdAsc(UUID requestId);
}
