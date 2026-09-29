package com.InnovaServe.core.controller;

import com.InnovaServe.core.repository.TenantRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/lookup")
public class TenantLookupController {
  private final TenantRepository tenants;

  public TenantLookupController(TenantRepository tenants) {
    this.tenants = tenants;
  }

  @GetMapping
  public Map<String, Object> lookup(@RequestParam String code) {
    var tenant =
        tenants
            .findByTenantCodeIgnoreCase(code.trim())
            .orElseThrow(() -> new java.util.NoSuchElementException("Property not found"));
    return Map.of(
        "tenant_id", tenant.getId(),
        "tenant_code", tenant.getTenantCode(),
        "tenant_name", tenant.getName());
  }
}
