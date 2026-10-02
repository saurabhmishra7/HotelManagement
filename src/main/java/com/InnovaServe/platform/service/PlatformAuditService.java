package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformAuditLog;
import com.InnovaServe.platform.repository.PlatformAuditRepository;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
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
            adminId,
            "platform_admin",
            action,
            entityType,
            entityId,
            jsonSafe(before),
            jsonSafe(after)));
  }

  public void recordSystem(String action, String entityType, UUID entityId, Map<String, Object> before, Map<String, Object> after) {
    audit.save(
        new PlatformAuditLog(
            null, "system", action, entityType, entityId, jsonSafe(before), jsonSafe(after)));
  }

  private static Map<String, Object> jsonSafe(Map<String, Object> value) {
    if (value == null) return null;
    Map<String, Object> result = new LinkedHashMap<>();
    value.forEach((key, item) -> result.put(key, jsonSafeValue(item)));
    return result;
  }

  private static Object jsonSafeValue(Object value) {
    if (value instanceof TemporalAccessor) return value.toString();
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, item) -> result.put(String.valueOf(key), jsonSafeValue(item)));
      return result;
    }
    if (value instanceof Collection<?> collection)
      return collection.stream().map(PlatformAuditService::jsonSafeValue).toList();
    return value;
  }
}
