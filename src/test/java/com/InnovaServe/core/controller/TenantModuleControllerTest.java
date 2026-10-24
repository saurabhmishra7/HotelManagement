package com.InnovaServe.core.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import com.InnovaServe.platform.service.BarFeatureService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantModuleControllerTest {
  private final UUID tenantId = UUID.randomUUID();
  private final TenantContext context = mock(TenantContext.class);
  private final ModuleEntitlementService modules = mock(ModuleEntitlementService.class);
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
  private final BarFeatureService barFeature = mock(BarFeatureService.class);
  private final LocalDate today = LocalDate.of(2026, 10, 24);
  private TenantModuleController controller;

  @BeforeEach
  void setup() {
    when(context.tenantId()).thenReturn(tenantId);
    Tenant tenant = mock(Tenant.class);
    when(tenant.getName()).thenReturn("Property");
    when(tenant.getTenantCode()).thenReturn("PROPERTY");
    when(tenants.findById(tenantId)).thenReturn(Optional.of(tenant));
    controller = new TenantModuleController(context, modules, tenants, barFeature,
        subscriptions, Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void paidPlanWithModulesIsActiveWithoutAnotherSubscriptionLookup() {
    when(modules.activeModules(tenantId)).thenReturn(List.of("restaurant"));

    Map<String, Object> result = controller.modules();

    assertTrue((Boolean) result.get("subscription_active"));
    assertEquals(List.of("restaurant"), result.get("modules"));
    verify(subscriptions, never()).findFirstByTenantIdAndStatusInOrderByStartsOnDesc(
        org.mockito.ArgumentMatchers.any(), anyList());
  }

  @Test
  void activePaidPlanIsNotReportedEndedIfModuleSnapshotIsTemporarilyEmpty() {
    TenantSubscription active = new TenantSubscription(tenantId, UUID.randomUUID(),
        BigDecimal.TEN, BigDecimal.TEN, null, today, today.plusMonths(1), "active",
        null, Set.of("restaurant"), false, false);
    when(subscriptions.findFirstByTenantIdAndStatusInOrderByStartsOnDesc(
        tenantId, List.of("active", "cancelling"))).thenReturn(Optional.of(active));

    assertTrue((Boolean) controller.modules().get("subscription_active"));
  }

  @Test
  void expiredSubscriptionWithNoModulesIsReportedInactive() {
    TenantSubscription expired = new TenantSubscription(tenantId, UUID.randomUUID(),
        BigDecimal.TEN, BigDecimal.TEN, null, today.minusMonths(2), today.minusDays(1),
        "active", null, Set.of("restaurant"), false, false);
    when(subscriptions.findFirstByTenantIdAndStatusInOrderByStartsOnDesc(
        tenantId, List.of("active", "cancelling"))).thenReturn(Optional.of(expired));

    assertFalse((Boolean) controller.modules().get("subscription_active"));
  }
}
