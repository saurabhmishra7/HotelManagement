package com.InnovaServe.platform.service;

import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.entity.TenantSubscriptionCheckout;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionCheckoutRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantSubscriptionCheckoutService {
  private final TenantContext context;
  private final PlanRepository plans;
  private final TenantSubscriptionRepository subscriptions;
  private final TenantSubscriptionCheckoutRepository checkouts;
  private final SubscriptionRequestRepository requests;
  private final PlatformSubscriptionService subscriptionService;
  private final RazorpayPaymentService payments;
  private final Clock clock;
  private final boolean demoPaymentsEnabled;
  private final TransactionTemplate transactions;

  public TenantSubscriptionCheckoutService(TenantContext context, PlanRepository plans,
      TenantSubscriptionRepository subscriptions,
      TenantSubscriptionCheckoutRepository checkouts,
      SubscriptionRequestRepository requests,
      PlatformSubscriptionService subscriptionService, RazorpayPaymentService payments,
      Clock clock,
      @Value("${app.payments.demo-mode:false}") boolean demoPaymentsEnabled,
      PlatformTransactionManager transactionManager) {
    this.context = context;
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.checkouts = checkouts;
    this.requests = requests;
    this.subscriptionService = subscriptionService;
    this.payments = payments;
    this.clock = clock;
    this.demoPaymentsEnabled = demoPaymentsEnabled;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  public Map<String, Object> begin(String action, UUID planId) {
    UUID tenantId = context.tenantId();
    UUID userId = context.userId();
    String normalized = action == null ? "" : action.trim().toLowerCase();
    if (!List.of("activate", "schedule").contains(normalized))
      throw new IllegalArgumentException("Action must be activate or schedule");
    Plan plan = plans.findById(planId).filter(Plan::isActive)
        .orElseThrow(() -> new NoSuchElementException("Active plan not found"));
    if (plan.getPrice().signum() == 0)
      return subscriptionService.activateTenantPlan(
          tenantId, plan, plan.getPrice(), "schedule".equals(normalized));
    LocalDate today = LocalDate.now(clock);
    List<TenantSubscription> current = subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId)
        .stream().filter(row -> List.of("active", "cancelling").contains(row.getStatus()))
        .filter(row -> !row.getExpiresOn().isBefore(today)).toList();
    if ("activate".equals(normalized) && current.stream().anyMatch(row -> !row.isTrial()))
      throw new IllegalStateException("An active paid plan cannot be replaced instantly. Schedule a plan or request a reviewed change.");
    if ("schedule".equals(normalized)
        && subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(tenantId, "scheduled").isPresent())
      throw new IllegalStateException("A paid future subscription is already scheduled; contact the platform team to change it.");
    if (!demoPaymentsEnabled && !payments.isConfigured())
      throw new RazorpayPaymentService.PaymentProviderUnavailableException();
    return createCheckout(tenantId, userId, plan, null, normalized, plan.getPrice());
  }

  @Transactional
  public Map<String, Object> acceptProposal(UUID requestId) {
    UUID tenantId = context.tenantId();
    UUID userId = context.userId();
    SubscriptionRequest request = requests.findById(requestId)
        .orElseThrow(() -> new NoSuchElementException("Subscription proposal not found"));
    if (!tenantId.equals(request.getTenantId())
        || !"negotiated_price".equals(request.getRequestType())
        || !"platform_negotiated_price".equals(request.getTriggerType()))
      throw new NoSuchElementException("Subscription proposal not found");
    if (!"awaiting_tenant".equals(request.getStatus()))
      throw new IllegalStateException("This proposal is no longer awaiting acceptance");
    if (checkouts.existsBySubscriptionRequestIdAndStatus(requestId, "pending"))
      throw new IllegalStateException("A checkout is already open for this proposal; finish it or retry after it expires");
    Plan plan = plans.findById(request.getRequestedPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new IllegalStateException("Proposed plan is no longer active"));
    LocalDate today = LocalDate.now(clock);
    List<TenantSubscription> current = subscriptions.findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(tenantId)
        .stream().filter(row -> List.of("active", "cancelling").contains(row.getStatus()))
        .filter(row -> !row.getExpiresOn().isBefore(today)).toList();
    String action = current.stream().anyMatch(row -> !row.isTrial()) ? "schedule" : "activate";
    if (action.equals("schedule")
        && subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(tenantId, "scheduled").isPresent())
      throw new IllegalStateException("A paid future subscription is already scheduled");
    if (request.getProposedPrice() == null || request.getProposedPrice().signum() < 0)
      throw new IllegalStateException("The proposal has no valid negotiated price");
    if (request.getProposedPrice().signum() == 0) {
      Map<String, Object> created = subscriptionService.activateTenantPlan(
          tenantId, plan, request.getProposedPrice(), "schedule".equals(action));
      created.put("action", action);
      request.acceptByTenant();
      requests.save(request);
      return created;
    }
    if (!demoPaymentsEnabled && !payments.isConfigured())
      throw new RazorpayPaymentService.PaymentProviderUnavailableException();
    return createCheckout(tenantId, userId, plan, requestId, action, request.getProposedPrice());
  }

  private Map<String, Object> createCheckout(UUID tenantId, UUID userId, Plan plan,
      UUID requestId, String action, java.math.BigDecimal amount) {
    String orderId;
    String keyId = null;
    long displayAmount;
    if (demoPaymentsEnabled) {
      orderId = "demo-subscription-" + UUID.randomUUID();
      displayAmount = amount.movePointRight(2).longValueExact();
    } else {
      String receipt = "sub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
      RazorpayPaymentService.Order order = payments.createOrder(amount, plan.getCurrency(), receipt);
      orderId = order.orderId();
      keyId = order.keyId();
      displayAmount = order.amountMinor();
    }
    TenantSubscriptionCheckout checkout = checkouts.save(new TenantSubscriptionCheckout(
        tenantId, userId, plan.getId(), requestId, action, amount, plan.getCurrency(),
        orderId, Instant.now(clock).plus(Duration.ofMinutes(20))));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("checkout_id", checkout.getId());
    result.put("action", action);
    result.put("demo_payment", demoPaymentsEnabled);
    if (keyId != null) result.put("key_id", keyId);
    result.put("order_id", orderId);
    result.put("amount", demoPaymentsEnabled ? amount : displayAmount);
    result.put("currency", plan.getCurrency());
    result.put("plan", PlatformPlanService.snapshot(plan));
    result.put("proposed_price", amount);
    return result;
  }

  public Map<String, Object> complete(PaymentCompletion payment) {
    TenantSubscriptionCheckout checkout = checkouts.findById(payment.checkoutId())
        .orElseThrow(() -> new NoSuchElementException("Subscription checkout not found"));
    if (!checkout.getTenantId().equals(context.tenantId()))
      throw new NoSuchElementException("Subscription checkout not found");
    if (!checkout.getPaymentOrderId().equals(payment.orderId()))
      throw new IllegalArgumentException("Payment order does not match this checkout");
    if ("completed".equals(checkout.getStatus()))
      return Map.of("status", "completed", "action", checkout.getAction());
    if (!"pending".equals(checkout.getStatus()) || !checkout.getExpiresAt().isAfter(Instant.now(clock)))
      throw new IllegalArgumentException("Subscription checkout has expired");
    payments.requireCapturedPayment(checkout.getPaymentOrderId(), payment.paymentId(),
        payment.signature(), checkout.getAmount(), checkout.getCurrency());
    return transactions.execute(status -> finish(payment.checkoutId(), false));
  }

  public Map<String, Object> completeDemoPayment(UUID checkoutId) {
    if (!demoPaymentsEnabled)
      throw new IllegalStateException("Demo payment confirmation is disabled");
    return transactions.execute(status -> finish(checkoutId, true));
  }

  private Map<String, Object> finish(UUID checkoutId, boolean demo) {
    TenantSubscriptionCheckout checkout = checkouts.lockById(checkoutId)
        .orElseThrow(() -> new NoSuchElementException("Subscription checkout not found"));
    if ("completed".equals(checkout.getStatus()))
      return Map.of("status", "completed", "action", checkout.getAction());
    if (!checkout.getTenantId().equals(context.tenantId())
        || !checkout.getExpiresAt().isAfter(Instant.now(clock))
        || (demo && !checkout.getPaymentOrderId().startsWith("demo-subscription-"))
        || (!demo && checkout.getPaymentOrderId().startsWith("demo-subscription-")))
      throw new IllegalArgumentException("Subscription checkout has expired");
    Plan plan = plans.findById(checkout.getPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new IllegalStateException("Selected plan is no longer active"));
    Map<String, Object> subscription = new LinkedHashMap<>(subscriptionService.activateTenantPlan(
        checkout.getTenantId(), plan, checkout.getAmount(), "schedule".equals(checkout.getAction())));
    subscription.put("action", checkout.getAction());
    if (!checkout.complete(Instant.now(clock)))
      throw new IllegalArgumentException("Subscription checkout has expired");
    checkouts.save(checkout);
    if (checkout.getSubscriptionRequestId() != null) {
      SubscriptionRequest request = requests.findById(checkout.getSubscriptionRequestId())
          .orElseThrow(() -> new NoSuchElementException("Subscription proposal not found"));
      if ("awaiting_tenant".equals(request.getStatus())) {
        request.acceptByTenant();
        requests.save(request);
      }
    }
    return subscription;
  }

  public record PaymentCompletion(
      @JsonProperty("checkout_id") UUID checkoutId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("payment_id") String paymentId,
      String signature) {}
}
