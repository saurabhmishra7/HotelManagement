package com.InnovaServe.restaurant.service;

import com.InnovaServe.contracts.ModuleDeactivationGuard;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.restaurant.repository.RestaurantOrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RestaurantDeactivationGuard implements ModuleDeactivationGuard {
  private final RestaurantOrderRepository orders;

  public RestaurantDeactivationGuard(RestaurantOrderRepository orders) {
    this.orders = orders;
  }

  @Override
  public ModuleType module() {
    return ModuleType.RESTAURANT;
  }

  @Override
  public Optional<String> blockingReason(UUID tenantId) {
    return orders.existsByTenantIdAndStatus(tenantId, "open")
        ? Optional.of("Bill or cancel all open restaurant orders before disabling Restaurant")
        : Optional.empty();
  }
}
