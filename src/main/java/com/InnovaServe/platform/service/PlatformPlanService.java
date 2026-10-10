package com.InnovaServe.platform.service;

import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import com.InnovaServe.platform.repository.PlanRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformPlanService {
  private final PlanRepository plans;
  private final TenantSubscriptionRepository subscriptions;
  private final ModuleEntitlementService entitlements;
  private final PlatformAuditService audit;
  private final Clock clock;

  public PlatformPlanService(PlanRepository plans, TenantSubscriptionRepository subscriptions,
      ModuleEntitlementService entitlements, PlatformAuditService audit, Clock platformClock) {
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.entitlements = entitlements;
    this.audit = audit;
    this.clock = platformClock;
  }

  @Transactional(readOnly = true)
  public List<Plan> list(boolean includeInactive) {
    return includeInactive
        ? plans.findAllByOrderByNameAscVersionDesc()
        : plans.findAllByActiveTrueOrderByNameAsc();
  }

  @Transactional
  public Plan create(
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Collection<String> moduleNames,
      UUID adminId) {
    PlanInput input = validate(name, price, currency, duration, moduleNames);
    Plan plan =
        plans.save(
            new Plan(
                UUID.randomUUID(),
                1,
                input.name(),
                input.price(),
                input.currency(),
                input.duration(),
                input.modules()));
    audit.record(adminId, "PLAN_CREATED", "plan", plan.getId(), null, snapshot(plan));
    return plan;
  }

  @Transactional
  public Map<String, Object> update(
      UUID id,
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Collection<String> moduleNames,
      Boolean active,
      UUID adminId) {
    Plan existing =
        plans.lockById(id).orElseThrow(() -> new NoSuchElementException("Plan not found"));
    Map<String, Object> before = snapshot(existing);
    if (Boolean.FALSE.equals(active)
        && name == null
        && price == null
        && currency == null
        && duration == null
        && moduleNames == null) {
      existing.retire();
      Map<String, Object> after = snapshot(existing);
      after.put("active_subscriptions_updated", 0);
      after.put("scheduled_subscriptions_updated", 0);
      audit.record(adminId, "PLAN_RETIRED", "plan", id, before, after);
      return after;
    }
    if (Boolean.TRUE.equals(active) && !existing.isActive()) {
      throw new IllegalStateException("Retired plan versions cannot be reactivated; create a new version");
    }
    if (!existing.isActive()) throw new IllegalStateException("Retired plan versions are immutable");
    boolean hasOfferChanges =
        name != null || price != null || currency != null || duration != null || moduleNames != null;
    if (!hasOfferChanges) {
      if (Boolean.TRUE.equals(active)) return snapshot(existing);
      throw new IllegalArgumentException("Provide plan terms or set active to false");
    }

    PlanInput input =
        validate(
            name == null ? existing.getName() : name,
            price == null ? existing.getPrice() : price,
            currency == null ? existing.getCurrency() : currency,
            duration == null ? existing.getDuration() : duration,
            moduleNames == null ? existing.getModules() : moduleNames);
    existing.retire();
    plans.saveAndFlush(existing);
    Plan version =
        plans.save(
            new Plan(
                existing.getFamilyId(),
                existing.getVersion() + 1,
                input.name(),
                input.price(),
                input.currency(),
                input.duration(),
                input.modules()));
    if (Boolean.FALSE.equals(active)) version.retire();
    Map<String, Object> after = snapshot(version);
    Map<String, Integer> propagation = version.isActive()
        ? applyVersionToSubscribers(version)
        : Map.of("active_subscriptions_updated", 0, "scheduled_subscriptions_updated", 0);
    after.putAll(propagation);
    audit.record(adminId, version.isActive() ? "PLAN_VERSION_CREATED_AND_APPLIED" : "PLAN_VERSION_CREATED",
        "plan", version.getId(), before, after);
    return after;
  }

  /** Applies module changes to current and scheduled subscriptions in this plan family.
   * Current paid-through dates and amounts remain unchanged; expired tenants remain blocked.
   */
  private Map<String, Integer> applyVersionToSubscribers(Plan version) {
    List<UUID> planIds = plans.findAllByFamilyId(version.getFamilyId()).stream().map(Plan::getId).toList();
    List<TenantSubscription> rows = subscriptions.findAllByPlanIdInAndStatusIn(
        planIds, List.of("active", "cancelling", "scheduled"));
    LocalDate today = LocalDate.now(clock);
    int activeUpdated = 0;
    int scheduledUpdated = 0;
    for (TenantSubscription subscription : rows) {
      if ("scheduled".equals(subscription.getStatus())) {
        subscription.replaceModules(version.getModules());
        scheduledUpdated++;
      } else if (!subscription.getExpiresOn().isBefore(today)) {
        subscription.replaceModules(version.getModules());
        entitlements.applySubscription(subscription.getTenantId(), version.getModules(),
            PlatformSubscriptionService.expiresAt(subscription.getExpiresOn()));
        activeUpdated++;
      }
    }
    return Map.of("active_subscriptions_updated", activeUpdated,
        "scheduled_subscriptions_updated", scheduledUpdated);
  }

  @Transactional(readOnly = true)
  public Plan get(UUID id) {
    return plans.findById(id).orElseThrow(() -> new NoSuchElementException("Plan not found"));
  }

  public static Map<String, Object> snapshot(Plan plan) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", plan.getId());
    result.put("family_id", plan.getFamilyId());
    result.put("version", plan.getVersion());
    result.put("name", plan.getName());
    result.put("price", plan.getPrice());
    result.put("currency", plan.getCurrency());
    result.put("duration", plan.getDuration());
    result.put("active", plan.isActive());
    result.put("modules", plan.getModules().stream().sorted().toList());
    return result;
  }

  private PlanInput validate(
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Collection<String> moduleNames) {
    if (name == null || name.isBlank() || name.trim().length() > 80)
      throw new IllegalArgumentException("Plan name is required and must be at most 80 characters");
    if (price == null
        || price.signum() < 0
        || price.scale() > 2
        || price.compareTo(new BigDecimal("99999999.99")) > 0)
      throw new IllegalArgumentException("Plan price must be zero or greater with at most two decimals");
    String normalizedCurrency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
    if (!normalizedCurrency.matches("[A-Z]{3}"))
      throw new IllegalArgumentException("Currency must be a three-letter uppercase code");
    String normalizedDuration = duration == null ? "" : duration.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("monthly", "annual").contains(normalizedDuration))
      throw new IllegalArgumentException("Duration must be monthly or annual");
    if (moduleNames == null || moduleNames.isEmpty())
      throw new IllegalArgumentException("A plan must include at least one module");
    Set<String> modules = new TreeSet<>();
    for (String nameValue : moduleNames) {
      ModuleType module = ModuleType.from(nameValue);
      if (!modules.add(module.key())) throw new IllegalArgumentException("Duplicate module: " + nameValue);
    }
    return new PlanInput(name.trim(), price, normalizedCurrency, normalizedDuration, modules);
  }

  private record PlanInput(
      String name, BigDecimal price, String currency, String duration, Set<String> modules) {}
}
