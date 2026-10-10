package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop20ByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(UUID tenantId, UUID userId);
  long countByTenantIdAndRecipientUserIdAndReadAtIsNullAndDismissedAtIsNull(UUID tenantId, UUID userId);
  Optional<Notification> findByTenantIdAndRecipientUserIdAndId(UUID tenantId, UUID userId, UUID id);
  void deleteAllByTenantIdAndRecipientUserIdAndDismissedAtIsNull(UUID tenantId, UUID userId);
}
