package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.repository.PlatformAuditRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/audit-logs")
public class PlatformAuditController {
  private final PlatformAuditRepository audit;

  public PlatformAuditController(PlatformAuditRepository audit) {
    this.audit = audit;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLATFORM_AUDIT_READ')")
  public List<?> recent() {
    return audit.findTop200ByOrderByCreatedAtDesc();
  }
}
