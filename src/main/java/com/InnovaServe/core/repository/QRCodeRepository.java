package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.QRCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QRCodeRepository extends JpaRepository<QRCode, UUID> {
  List<QRCode> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
