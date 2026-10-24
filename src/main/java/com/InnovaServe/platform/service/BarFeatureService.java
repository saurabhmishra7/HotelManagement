package com.InnovaServe.platform.service;

import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BarFeatureService {
  private final ModuleEntitlementService modules;
  private final TenantSubscriptionRepository subscriptions;
  private final Clock clock;

  public BarFeatureService(ModuleEntitlementService modules,
      TenantSubscriptionRepository subscriptions, Clock platformClock) {
    this.modules = modules;
    this.subscriptions = subscriptions;
    this.clock = platformClock;
  }

  @Transactional(readOnly = true)
  public boolean enabled(UUID tenantId) {
    if (!modules.isActive(tenantId, ModuleType.RESTAURANT)) return false;
    return subscriptions.findFirstByTenantIdAndStatusInOrderByStartsOnDesc(
            tenantId, List.of("active", "cancelling"))
        .map(subscription -> !subscription.getStartsOn().isAfter(LocalDate.now(clock))
            && !subscription.getExpiresOn().isBefore(LocalDate.now(clock))
            && subscription.isBarEnabled())
        .orElse(true); // Legacy restaurant access predating subscriptions retains Bar.
  }

  public void requireEnabled(UUID tenantId) {
    if (!enabled(tenantId)) throw new IllegalStateException("Bar is not included in this property's plan");
  }
}
