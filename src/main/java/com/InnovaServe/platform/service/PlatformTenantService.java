package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.entity.TenantModule;
import com.InnovaServe.core.repository.TenantModuleRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantService {
  private final TenantRepository tenants;
  private final TenantSubscriptionRepository subscriptions;
  private final TenantModuleRepository tenantModules;
  private final PlanRepository plans;
  private final Clock clock;

  public PlatformTenantService(
      TenantRepository tenants,
      TenantSubscriptionRepository subscriptions,
      TenantModuleRepository tenantModules,
      PlanRepository plans,
      Clock platformClock) {
    this.tenants = tenants;
    this.subscriptions = subscriptions;
    this.tenantModules = tenantModules;
    this.plans = plans;
    this.clock = platformClock;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(
      String search, String status, int page, int pageSize) {
    String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
    String normalizedStatus = status == null || status.isBlank() || "all".equalsIgnoreCase(status)
        ? null
        : status.trim().toLowerCase(Locale.ROOT);
    if (normalizedStatus != null
        && !Set.of("legacy", "scheduled", "active", "cancelling", "expired", "cancelled")
            .contains(normalizedStatus)) {
      throw new IllegalArgumentException("Unsupported tenant subscription status filter");
    }
    Page<Tenant> tenantPage =
        tenants.searchPlatformTenants(
            normalizedSearch,
            normalizedStatus,
            PageRequest.of(Math.max(0, page), Math.clamp(pageSize, 1, 100), Sort.by("name").ascending()));
    List<UUID> ids = tenantPage.getContent().stream().map(Tenant::getId).toList();
    if (ids.isEmpty()) return pageResult(tenantPage, List.of());

    Map<UUID, List<TenantSubscription>> byTenant =
        subscriptions.findAllByTenantIdIn(ids).stream()
            .collect(Collectors.groupingBy(TenantSubscription::getTenantId));
    Map<UUID, List<TenantModule>> entitlementsByTenant =
        tenantModules.findAllByTenantIdIn(ids).stream()
            .collect(Collectors.groupingBy(TenantModule::getTenantId));
    Set<UUID> planIds =
        byTenant.values().stream()
            .flatMap(Collection::stream)
            .map(TenantSubscription::getPlanId)
            .collect(Collectors.toSet());
    Map<UUID, Plan> planById = plans.findAllById(planIds).stream()
        .collect(Collectors.toMap(Plan::getId, Function.identity()));
    Instant now = Instant.now(clock);
    List<Map<String, Object>> rows =
        tenantPage.getContent().stream()
            .map(
                tenant ->
                    tenantRow(
                        tenant,
                        byTenant.getOrDefault(tenant.getId(), List.of()),
                        entitlementsByTenant.getOrDefault(tenant.getId(), List.of()),
                        planById,
                        now))
            .toList();
    return pageResult(tenantPage, rows);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> detail(UUID tenantId) {
    Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    List<TenantSubscription> history = subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId);
    Map<UUID, Plan> planById = plans.findAllById(history.stream().map(TenantSubscription::getPlanId).collect(Collectors.toSet()))
        .stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
    List<Map<String, Object>> subscriptionViews =
        history.stream()
            .map(subscription -> PlatformSubscriptionService.view(subscription, planById.get(subscription.getPlanId())))
            .toList();
    Instant now = Instant.now(clock);
    List<Map<String, Object>> moduleRows =
        tenantModules.findAllByTenantId(tenantId).stream()
            .sorted(Comparator.comparing(TenantModule::getModule))
            .map(
                entitlement -> {
                  Map<String, Object> row = new LinkedHashMap<>();
                  row.put("module", entitlement.getModule());
                  row.put("status", entitlement.getStatus());
                  row.put("expires_at", entitlement.getExpiresAt());
                  row.put(
                      "effective",
                      "active".equals(entitlement.getStatus())
                          && (entitlement.getExpiresAt() == null
                              || entitlement.getExpiresAt().isAfter(now)));
                  return row;
                })
            .toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tenant", tenantView(tenant));
    result.put("subscriptions", subscriptionViews);
    result.put("modules", moduleRows);
    return result;
  }

  private Map<String, Object> tenantRow(
      Tenant tenant,
      List<TenantSubscription> tenantSubscriptions,
      List<TenantModule> tenantEntitlements,
      Map<UUID, Plan> planById,
      Instant now) {
    TenantSubscription current = findStatus(tenantSubscriptions, "active", "cancelling");
    TenantSubscription scheduled = findStatus(tenantSubscriptions, "scheduled");
    TenantSubscription mostRecent = current != null ? current : scheduled != null ? scheduled : latest(tenantSubscriptions);
    Map<String, Object> row = new LinkedHashMap<>(tenantView(tenant));
    row.put("subscription_status", mostRecent == null ? "legacy" : mostRecent.getStatus());
    Plan currentPlan = current == null ? null : planById.get(current.getPlanId());
    row.put("current_plan", currentPlan == null ? null : currentPlan.getName());
    row.put("current_plan_version", currentPlan == null ? null : currentPlan.getVersion());
    row.put("price_paid", current == null ? null : current.getPricePaid());
    row.put("expires_on", current == null ? null : current.getExpiresOn());
    Plan scheduledPlan = scheduled == null ? null : planById.get(scheduled.getPlanId());
    row.put("scheduled_plan", scheduledPlan == null ? null : scheduledPlan.getName());
    row.put("scheduled_starts_on", scheduled == null ? null : scheduled.getStartsOn());
    row.put(
        "active_modules",
        tenantEntitlements.stream()
            .filter(module -> "active".equals(module.getStatus()))
            .filter(module -> module.getExpiresAt() == null || module.getExpiresAt().isAfter(now))
            .map(TenantModule::getModule)
            .sorted()
            .toList());
    return row;
  }

  private static TenantSubscription findStatus(List<TenantSubscription> rows, String... statuses) {
    Set<String> statusSet = Set.of(statuses);
    return rows.stream()
        .filter(row -> statusSet.contains(row.getStatus()))
        .max(Comparator.comparing(TenantSubscription::getStartsOn))
        .orElse(null);
  }

  private static TenantSubscription latest(List<TenantSubscription> rows) {
    return rows.stream().max(Comparator.comparing(TenantSubscription::getStartsOn)).orElse(null);
  }

  private static Map<String, Object> tenantView(Tenant tenant) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", tenant.getId());
    result.put("name", tenant.getName());
    result.put("tenant_code", tenant.getTenantCode());
    result.put("gstin", tenant.getGstin());
    result.put("address", tenant.getAddress());
    result.put("created_at", tenant.getCreatedAt());
    return result;
  }

  private static Map<String, Object> pageResult(Page<Tenant> page, List<Map<String, Object>> rows) {
    return Map.of(
        "content", rows,
        "page", page.getNumber(),
        "page_size", page.getSize(),
        "total_elements", page.getTotalElements(),
        "total_pages", page.getTotalPages());
  }
}
