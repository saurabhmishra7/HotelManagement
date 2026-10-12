package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.GuestQrSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestQrSessionRepository extends JpaRepository<GuestQrSession, UUID> {
  Optional<GuestQrSession> findByTokenHash(String tokenHash);
  List<GuestQrSession> findAllByTenantIdAndTargetTypeAndTargetIdAndStatus(
      UUID tenantId, String targetType, UUID targetId, String status);
}
