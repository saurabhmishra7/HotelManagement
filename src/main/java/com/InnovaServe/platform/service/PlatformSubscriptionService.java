package com.InnovaServe.platform.service;

import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.platform.PlatformTimeConfiguration;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSubscriptionService {
  private final TenantRepository tenants;
  private final PlanRepository plans;
  private final TenantSubscriptionRepository subscriptions;
  private final ModuleEntitlementService entitlements;
  private final PlatformAuditService audit;
  private final Clock clock;

  public PlatformSubscriptionService(
      TenantRepository tenants,
      PlanRepository plans,
      TenantSubscriptionRepository subscriptions,
      ModuleEntitlementService entitlements,
      PlatformAuditService audit,
      Clock platformClock) {
    this.tenants = tenants;
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.entitlements = entitlements;
    this.audit = audit;
    this.clock = platformClock;
  }

  @Transactional
  public Map<String, Object> subscribe(
      UUID tenantId,
      UUID planId,
      BigDecimal pricePaid,
      String note,
      LocalDate requestedStart,
      UUID adminId) {
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    LocalDate today = LocalDate.now(clock);
    Plan plan = plans.lockById(planId).orElseThrow(() -> new NoSuchElementException("Plan not found"));
    if (!plan.isActive()) throw new IllegalArgumentException("An inactive plan cannot be subscribed to");
    BigDecimal agreedPrice = pricePaid == null ? plan.getPrice() : pricePaid;
    validatePrice(agreedPrice);
    if (note != null && note.length() > 2000)
      throw new IllegalArgumentException("Negotiation note must be at most 2000 characters");

    List<TenantSubscription> currentRows = subscriptions.lockCurrentByTenant(tenantId);
    Optional<TenantSubscription> scheduled = subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(tenantId, "scheduled");
    if (scheduled.isPresent()) throw new IllegalStateException("A renewal is already scheduled for this tenant");

    TenantSubscription current = currentRows.stream().findFirst().orElse(null);
    LocalDate startsOn;
    if (current != null && !current.getExpiresOn().isBefore(today)) {
      startsOn = current.getExpiresOn().plusDays(1);
      if (requestedStart != null && !requestedStart.equals(startsOn))
        throw new IllegalArgumentException("A renewal starts the day after the current paid-through date");
      if ("cancelling".equals(current.getStatus())) current.activate();
    } else {
      if (current != null) {
        current.expire();
        subscriptions.save(current);
        entitlements.applySubscription(tenantId, Set.of(), null);
      }
      boolean hasLegacyAccess = !entitlements.activeModules(tenantId).isEmpty();
      if (requestedStart == null && hasLegacyAccess)
        throw new IllegalArgumentException(
            "Set an effective start date for this tenant's existing legacy access");
      startsOn = requestedStart == null ? today : requestedStart;
      if (startsOn.isBefore(today))
        throw new IllegalArgumentException("Subscription start date cannot be in the past");
    }

    LocalDate expiresOn = expiryDate(startsOn, plan.getDuration());
    String status = startsOn.isAfter(today) ? "scheduled" : "active";
    TenantSubscription created =
        subscriptions.save(
            new TenantSubscription(
                tenantId,
                plan.getId(),
                plan.getPrice(),
                agreedPrice,
                note == null || note.isBlank() ? null : note.trim(),
                startsOn,
                expiresOn,
                status,
                adminId,
                plan.getModules()));
    if ("active".equals(status)) {
      entitlements.applySubscription(tenantId, created.getModules(), expiresAt(created.getExpiresOn()));
    }
    audit.record(
        adminId,
        "SUBSCRIPTION_" + status.toUpperCase(Locale.ROOT),
        "tenant_subscription",
        created.getId(),
        null,
        view(created, plan));
    return view(created, plan);
  }

  @Transactional
  public Map<String, Object> cancel(UUID tenantId, UUID adminId) {
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    Optional<TenantSubscription> scheduled = subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(tenantId, "scheduled");
    if (scheduled.isPresent()) {
      TenantSubscription renewal = subscriptions.lockById(scheduled.get().getId()).orElseThrow();
      Map<String, Object> before = view(renewal, plans.findById(renewal.getPlanId()).orElse(null));
      renewal.cancel();
      audit.record(adminId, "SCHEDULED_RENEWAL_CANCELLED", "tenant_subscription", renewal.getId(), before, view(renewal, plans.findById(renewal.getPlanId()).orElse(null)));
      return view(renewal, plans.findById(renewal.getPlanId()).orElse(null));
    }

    TenantSubscription current =
        subscriptions.lockCurrentByTenant(tenantId).stream()
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No current or scheduled subscription"));
    if ("cancelling".equals(current.getStatus()))
      throw new IllegalStateException("Subscription is already set to cancel at the end of its term");
    Map<String, Object> before = view(current, plans.findById(current.getPlanId()).orElse(null));
    current.markCancelling();
    Plan plan = plans.findById(current.getPlanId()).orElse(null);
    audit.record(adminId, "SUBSCRIPTION_CANCEL_AT_PERIOD_END", "tenant_subscription", current.getId(), before, view(current, plan));
    return view(current, plan);
  }

  @Transactional
  public void processDue() {
    LocalDate today = LocalDate.now(clock);
    List<TenantSubscription> expiredCandidates = subscriptions.findExpired(today);
    for (TenantSubscription candidate : expiredCandidates) {
      tenants.lockById(candidate.getTenantId())
          .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
      TenantSubscription subscription =
          subscriptions.lockById(candidate.getId()).orElse(null);
      if (subscription != null
          && Set.of("active", "cancelling").contains(subscription.getStatus())
          && subscription.getExpiresOn().isBefore(today)) {
        Map<String, Object> before = minimalSnapshot(subscription);
        if ("cancelling".equals(subscription.getStatus())) subscription.cancel();
        else subscription.expire();
        entitlements.applySubscription(subscription.getTenantId(), Set.of(), null);
        audit.recordSystem(
            "SUBSCRIPTION_" + subscription.getStatus().toUpperCase(Locale.ROOT),
            "tenant_subscription",
            subscription.getId(),
            before,
            minimalSnapshot(subscription));
      }
    }

    for (TenantSubscription candidate : subscriptions.findScheduledDue(today)) {
      tenants.lockById(candidate.getTenantId())
          .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
      TenantSubscription subscription =
          subscriptions.lockById(candidate.getId()).orElse(null);
      if (subscription == null
          || !"scheduled".equals(subscription.getStatus())
          || subscription.getStartsOn().isAfter(today)) continue;
      if (subscription.getExpiresOn().isBefore(today)) {
        Map<String, Object> before = minimalSnapshot(subscription);
        subscription.expire();
        entitlements.applySubscription(subscription.getTenantId(), Set.of(), null);
        audit.recordSystem("SCHEDULED_SUBSCRIPTION_EXPIRED", "tenant_subscription", subscription.getId(), before, minimalSnapshot(subscription));
        continue;
      }
      boolean stillCurrent =
          subscriptions.lockCurrentByTenant(subscription.getTenantId()).stream()
              .anyMatch(row -> !row.getExpiresOn().isBefore(today));
      if (stillCurrent) continue;
      Map<String, Object> before = minimalSnapshot(subscription);
      subscription.activate();
      Plan plan = plans.findById(subscription.getPlanId()).orElseThrow();
      entitlements.applySubscription(
          subscription.getTenantId(), subscription.getModules(), expiresAt(subscription.getExpiresOn()));
      audit.recordSystem(
          "SCHEDULED_SUBSCRIPTION_ACTIVATED",
          "tenant_subscription",
          subscription.getId(),
          before,
          view(subscription, plan));
    }
  }

  @Transactional(readOnly = true)
  public List<TenantSubscription> history(UUID tenantId) {
    if (!tenants.existsById(tenantId)) throw new NoSuchElementException("Tenant not found");
    return subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId);
  }

  public static Map<String, Object> view(TenantSubscription subscription, Plan plan) {
    Map<String, Object> result = minimalSnapshot(subscription);
    result.put("plan_name", plan == null ? "Unknown plan" : plan.getName());
    result.put("plan_version", plan == null ? null : plan.getVersion());
    result.put("currency", plan == null ? "INR" : plan.getCurrency());
    result.put("duration", plan == null ? null : plan.getDuration());
    return result;
  }

  public static Map<String, Object> minimalSnapshot(TenantSubscription subscription) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", subscription.getId());
    result.put("tenant_id", subscription.getTenantId());
    result.put("plan_id", subscription.getPlanId());
    result.put("plan_price_at_time", subscription.getPlanPriceAtTime());
    result.put("price_paid", subscription.getPricePaid());
    result.put("negotiation_note", subscription.getNegotiationNote());
    result.put("starts_on", subscription.getStartsOn());
    result.put("expires_on", subscription.getExpiresOn());
    result.put("status", subscription.getStatus());
    result.put("cancelled_at", subscription.getCancelledAt());
    result.put("modules", subscription.getModules().stream().sorted().toList());
    return result;
  }

  public static LocalDate expiryDate(LocalDate startsOn, String duration) {
    return switch (duration) {
      case "monthly" -> startsOn.plusMonths(1).minusDays(1);
      case "annual" -> startsOn.plusYears(1).minusDays(1);
      default -> throw new IllegalArgumentException("Unsupported subscription duration");
    };
  }

  public static java.time.Instant expiresAt(LocalDate expiresOn) {
    return expiresOn
        .plusDays(1)
        .atStartOfDay(PlatformTimeConfiguration.BILLING_ZONE)
        .toInstant();
  }

  private static void validatePrice(BigDecimal price) {
    if (price.signum() < 0
        || price.scale() > 2
        || price.compareTo(new BigDecimal("99999999.99")) > 0)
      throw new IllegalArgumentException(
          "Price paid must be between 0 and 99999999.99 with at most two decimals");
  }
}
