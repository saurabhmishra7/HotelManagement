package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.AuditLog;
import com.InnovaServe.core.repository.AuditLogRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    Specification<AuditLog> filter =
        (root, query, criteria) -> {
          List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
          predicates.add(criteria.equal(root.get("tenantId"), tenant.tenantId()));
          if (type != null) predicates.add(criteria.equal(root.get("entityType"), type));
          if (id != null) predicates.add(criteria.equal(root.get("entityId"), id));
          if (user != null) predicates.add(criteria.equal(root.get("userId"), user));
          if (from != null) predicates.add(criteria.greaterThanOrEqualTo(root.get("createdAt"), from));
          if (to != null) predicates.add(criteria.lessThanOrEqualTo(root.get("createdAt"), to));
          query.orderBy(criteria.desc(root.get("createdAt")));
          return criteria.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    return repository.findAll(filter, Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
