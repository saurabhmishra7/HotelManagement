package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.AuditLog;
import com.InnovaServe.core.service.AuditService;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {
  private final AuditService service;

  public AuditController(AuditService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_AUDIT_READ')")
  public List<AuditLog> search(
      @RequestParam(name = "entity_type", required = false) String type,
      @RequestParam(name = "entity_id", required = false) UUID id,
      @RequestParam(name = "user_id", required = false) UUID user,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime to) {
    return service.search(
        type,
        id,
        user,
        from == null ? null : from.toLocalDateTime(),
        to == null ? null : to.toLocalDateTime());
  }
}
