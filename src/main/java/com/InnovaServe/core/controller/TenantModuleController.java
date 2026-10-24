package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.platform.service.BarFeatureService;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
  private final BarFeatureService barFeature;
  private final TenantSubscriptionRepository subscriptions;
  private final Clock clock;

  public TenantModuleController(
      TenantContext tenant, ModuleEntitlementService modules, TenantRepository tenants,
      BarFeatureService barFeature, TenantSubscriptionRepository subscriptions, Clock platformClock) {
    this.tenant = tenant;
    this.modules = modules;
    this.tenants = tenants;
    this.barFeature = barFeature;
    this.subscriptions = subscriptions;
    this.clock = platformClock;
  }

  @GetMapping
  public Map<String, Object> modules() {
    UUID tenantId = tenant.tenantId();
    var property =
        tenants
            .findById(tenantId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Tenant not found"));
    List<String> activeModules = modules.activeModules(tenantId);
    boolean subscriptionActive = !activeModules.isEmpty() || subscriptions
        .findFirstByTenantIdAndStatusInOrderByStartsOnDesc(tenantId, List.of("active", "cancelling"))
        .map(subscription -> !subscription.getStartsOn().isAfter(LocalDate.now(clock))
            && !subscription.getExpiresOn().isBefore(LocalDate.now(clock)))
        .orElse(false);
    return Map.of(
        "tenant_id", tenantId,
        "tenant_code", property.getTenantCode(),
        "tenant_name", property.getName(),
        "modules", activeModules,
        "subscription_active", subscriptionActive,
        "features", Map.of("bar", barFeature.enabled(tenantId)));
  }
}
