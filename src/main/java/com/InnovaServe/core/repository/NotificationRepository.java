package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop20ByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByUpdatedAtDesc(UUID tenantId, UUID userId);
  long countByTenantIdAndRecipientUserIdAndReadAtIsNullAndDismissedAtIsNull(UUID tenantId, UUID userId);
  Optional<Notification> findByTenantIdAndRecipientUserIdAndId(UUID tenantId, UUID userId, UUID id);
  void deleteAllByTenantIdAndRecipientUserIdAndDismissedAtIsNull(UUID tenantId, UUID userId);
  List<Notification> findAllByTenantIdAndSourceAndSourceKeyAndDismissedAtIsNull(UUID tenantId, String source, String sourceKey);
  List<Notification> findAllByTenantIdAndSourceAndSourceKey(UUID tenantId, String source, String sourceKey);
  List<Notification> findAllByTenantIdAndSourceAndSourceKeyStartingWithAndDismissedAtIsNull(UUID tenantId, String source, String sourceKeyPrefix);
}
