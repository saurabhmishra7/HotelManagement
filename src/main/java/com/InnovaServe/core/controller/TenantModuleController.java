package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.core.repository.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/modules")
public class TenantModuleController {
  private final TenantContext tenant;
  private final ModuleEntitlementService modules;
  private final TenantRepository tenants;

  public TenantModuleController(
      TenantContext tenant, ModuleEntitlementService modules, TenantRepository tenants) {
    this.tenant = tenant;
    this.modules = modules;
    this.tenants = tenants;
  }

  @GetMapping
  public Map<String, Object> modules() {
    UUID tenantId = tenant.tenantId();
    var property =
        tenants
            .findById(tenantId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Tenant not found"));
    return Map.of(
        "tenant_id", tenantId,
        "tenant_code", property.getTenantCode(),
        "tenant_name", property.getName(),
        "modules", modules.activeModules(tenantId));
  }
}
