package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformAuditLog;
import com.InnovaServe.platform.repository.PlatformAuditRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuditService {
  private final PlatformAuditRepository audit;

  public PlatformAuditService(PlatformAuditRepository audit) {
    this.audit = audit;
  }

  public void record(
      UUID adminId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> before,
      Map<String, Object> after) {
    audit.save(
        new PlatformAuditLog(
            adminId, "platform_admin", action, entityType, entityId, before, after));
  }

  public void recordSystem(String action, String entityType, UUID entityId, Map<String, Object> before, Map<String, Object> after) {
    audit.save(
        new PlatformAuditLog(
            null, "system", action, entityType, entityId, before, after));
  }
}
