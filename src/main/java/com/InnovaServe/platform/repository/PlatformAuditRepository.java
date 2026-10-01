package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.PlatformAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditRepository extends JpaRepository<PlatformAuditLog, UUID> {
  List<PlatformAuditLog> findTop200ByOrderByCreatedAtDesc();
}
