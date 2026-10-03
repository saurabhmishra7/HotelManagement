package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantSubscriptionRequestService {
  private static final List<String> OPEN_STATUSES = List.of("pending", "in_review", "awaiting_tenant");

  private final TenantContext tenantContext;
  private final TenantRepository tenants;
  private final StaffUserRepository users;
  private final PlanRepository plans;
  private final TenantSubscriptionRepository subscriptions;
  private final SubscriptionRequestRepository requests;
  private final ModuleEntitlementService entitlements;
  private final Clock clock;

  public TenantSubscriptionRequestService(
      TenantContext tenantContext,
      TenantRepository tenants,
      StaffUserRepository users,
      PlanRepository plans,
      TenantSubscriptionRepository subscriptions,
      SubscriptionRequestRepository requests,
      ModuleEntitlementService entitlements,
      Clock platformClock) {
    this.tenantContext = tenantContext;
    this.tenants = tenants;
    this.users = users;
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.requests = requests;
    this.entitlements = entitlements;
    this.clock = platformClock;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> view() {
    UUID tenantId = tenantContext.tenantId();
    Tenant tenant = tenants.findById(tenantId).orElseThrow();
    LocalDate today = LocalDate.now(clock);
    List<TenantSubscription> history =
        subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId);
    TenantSubscription current = history.stream()
        .filter(row -> List.of("active", "cancelling").contains(row.getStatus()))
        .filter(row -> !row.getExpiresOn().isBefore(today))
        .findFirst().orElse(null);
    TenantSubscription scheduled = history.stream()
        .filter(row -> "scheduled".equals(row.getStatus()))
        .findFirst().orElse(null);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("tenant_id", tenantId);
    result.put("tenant_name", tenant.getName());
    result.put("tenant_code", tenant.getTenantCode());
    result.put("effective_modules", entitlements.activeModules(tenantId).stream().sorted().toList());
    result.put("current_subscription", subscriptionView(current));
    result.put("scheduled_subscription", subscriptionView(scheduled));
    result.put("history", history.stream().map(this::subscriptionView).toList());
    result.put("available_plans", plans.findAllByActiveTrueOrderByNameAsc().stream()
        .map(PlatformPlanService::snapshot).toList());
    result.put("requests", requests.findAllByTenantIdOrderBySubmittedAtDesc(tenantId).stream()
        .map(this::requestView).toList());
    return result;
  }

  @Transactional
  public Map<String, Object> request(String type, UUID planId, String message) {
    UUID tenantId = tenantContext.tenantId();
    UUID userId = tenantContext.userId();
    users.findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new IllegalStateException("Authenticated user does not belong to this tenant"));
    String requestType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("tenant_instant_change").contains(requestType))
      throw new IllegalArgumentException("Only an instant mid-cycle plan change can be sent for review");
    if (message != null && message.length() > 1000)
      throw new IllegalArgumentException("Message must be at most 1000 characters");
    if (requests.existsByTenantIdAndStatusIn(tenantId, OPEN_STATUSES))
      throw new IllegalStateException("A subscription request is already awaiting review");

    LocalDate today = LocalDate.now(clock);
    TenantSubscription current = subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId)
        .stream()
        .filter(row -> List.of("active", "cancelling").contains(row.getStatus()))
        .filter(row -> !row.getExpiresOn().isBefore(today))
        .findFirst().orElse(null);

    if (current == null || current.isTrial())
      throw new IllegalStateException("An instant-change request requires an active paid subscription");

    UUID targetPlanId = planId;
    if (targetPlanId == null) throw new IllegalArgumentException("plan_id is required for this request");
    Plan target = plans.findById(targetPlanId)
        .filter(Plan::isActive).orElseThrow(() -> new NoSuchElementException("Active plan not found"));
    if (target.getId().equals(current.getPlanId()))
      throw new IllegalArgumentException("Choose a different plan to request an upgrade");

    SubscriptionRequest saved = requests.save(new SubscriptionRequest(
        tenantId, userId, current.getId(), target.getId(), requestType,
        message == null || message.isBlank() ? null : message.trim()));
    return requestView(saved);
  }

  private Map<String, Object> subscriptionView(TenantSubscription subscription) {
    if (subscription == null) return null;
    Plan plan = plans.findById(subscription.getPlanId()).orElse(null);
    Map<String, Object> result = new LinkedHashMap<>(PlatformSubscriptionService.view(subscription, plan));
    result.remove("negotiation_note");
    return result;
  }

  private Map<String, Object> requestView(SubscriptionRequest request) {
    Plan plan = plans.findById(request.getRequestedPlanId()).orElse(null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", request.getId());
    result.put("request_type", request.getRequestType());
    result.put("trigger_type", request.getTriggerType());
    result.put("status", request.getStatus());
    result.put("proposed_price", request.getProposedPrice());
    result.put("message", request.getMessage());
    result.put("response_note", request.getResponseNote());
    result.put("submitted_at", request.getSubmittedAt());
    result.put("updated_at", request.getUpdatedAt());
    result.put("requested_plan", plan == null ? null : PlatformPlanService.snapshot(plan));
    return result;
  }
}
