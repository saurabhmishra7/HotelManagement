package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.AuditLog;
import com.InnovaServe.core.repository.AuditLogRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditService {
  private final AuditLogRepository repository;
  private final TenantContext tenant;

  public AuditService(AuditLogRepository r, TenantContext t) {
    repository = r;
    tenant = t;
  }

  @Transactional
  public AuditLog record(
      String action, String type, UUID id, Map<String, Object> before, Map<String, Object> after) {
    return repository.save(
        new AuditLog(tenant.tenantId(), tenant.userId(), action, type, id, before, after));
  }

  public List<AuditLog> search(
      String type, UUID id, UUID user, LocalDateTime from, LocalDateTime to) {
    return repository.search(tenant.tenantId(), type, id, user, from, to);
  }
}
