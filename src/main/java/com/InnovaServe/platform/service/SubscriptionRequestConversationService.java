package com.InnovaServe.platform.service;

import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.entity.SubscriptionRequestComment;
import com.InnovaServe.platform.entity.SubscriptionRequestProposal;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestCommentRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestProposalRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionRequestConversationService {
  private static final String INSTANT_CHANGE = "tenant_instant_change";
  private static final List<String> OPEN_STATUSES = List.of("pending", "in_review");
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  private final TenantContext tenantContext;
  private final StaffUserRepository users;
  private final TenantRepository tenants;
  private final PlanRepository plans;
  private final SubscriptionRequestRepository requests;
  private final SubscriptionRequestProposalRepository proposals;
  private final SubscriptionRequestCommentRepository comments;
  private final TenantSubscriptionRepository subscriptions;
  private final ModuleEntitlementService entitlements;
  private final PlatformAuditService audit;
  private final Clock clock;

  public SubscriptionRequestConversationService(
      TenantContext tenantContext,
      StaffUserRepository users,
      TenantRepository tenants,
      PlanRepository plans,
      SubscriptionRequestRepository requests,
      SubscriptionRequestProposalRepository proposals,
      SubscriptionRequestCommentRepository comments,
      TenantSubscriptionRepository subscriptions,
      ModuleEntitlementService entitlements,
      PlatformAuditService audit,
      Clock platformClock) {
    this.tenantContext = tenantContext;
    this.users = users;
    this.tenants = tenants;
    this.plans = plans;
    this.requests = requests;
    this.proposals = proposals;
    this.comments = comments;
    this.subscriptions = subscriptions;
    this.entitlements = entitlements;
    this.audit = audit;
    this.clock = platformClock;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> tenantComments(UUID requestId) {
    UUID tenantId = tenantContext.tenantId();
    requireTenantRequest(requestId, tenantId);
    return thread(requestId);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> platformComments(UUID requestId) {
    requireRequest(requestId);
    return thread(requestId);
  }

  @Transactional
  public Map<String, Object> addTenantComment(UUID requestId, String message) {
    UUID tenantId = tenantContext.tenantId();
    UUID userId = tenantContext.userId();
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    SubscriptionRequest request = lockTenantRequest(requestId, tenantId);
    users.findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new NoSuchElementException("Tenant user not found"));
    requireOpen(request);
    return commentView(comments.save(new SubscriptionRequestComment(
        requestId, "tenant_user", userId, validatedMessage(message), null)));
  }

  @Transactional
  public Map<String, Object> addPlatformComment(UUID requestId, String message, UUID adminId) {
    SubscriptionRequest request = lockRequest(requestId);
    requireOpen(request);
    return commentView(comments.save(new SubscriptionRequestComment(
        requestId, "platform_admin", adminId, validatedMessage(message), null)));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> tenantProposals(UUID requestId) {
    UUID tenantId = tenantContext.tenantId();
    requireTenantRequest(requestId, tenantId);
    return proposals.findAllByRequestIdOrderByCreatedAtAsc(requestId).stream()
        .map(this::proposalView)
        .toList();
  }

  @Transactional
  public List<Map<String, Object>> preview(
      UUID requestId, List<String> calculationTypes) {
    SubscriptionRequest request = requireInstantRequest(requestId);
    List<String> types = normalizeCalculationTypes(calculationTypes);
    CalculationContext context = calculationContext(request);
    return types.stream().map(type -> {
      try {
        return calculationView(calculate(type, context));
      } catch (IllegalArgumentException unavailable) {
        return unavailableCalculationView(type, unavailable.getMessage());
      }
    }).toList();
  }

  @Transactional
  public List<Map<String, Object>> createProposals(
      UUID requestId, List<String> calculationTypes, UUID adminId) {
    SubscriptionRequest existing = requireRequest(requestId);
    tenants.lockById(existing.getTenantId())
        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    SubscriptionRequest request = lockRequest(requestId);
    requireOpenInstantRequest(request);
    List<String> types = normalizeCalculationTypes(calculationTypes);
    CalculationContext context = calculationContext(request);
    List<ProrationOffer> offers = types.stream().map(type -> calculate(type, context)).toList();

    proposals.findAllByRequestIdAndStatus(requestId, "offered")
        .forEach(SubscriptionRequestProposal::supersede);
    proposals.flush();

    List<SubscriptionRequestProposal> created = new ArrayList<>();
    for (ProrationOffer offer : offers) {
      SubscriptionRequestProposal proposal = proposals.saveAndFlush(new SubscriptionRequestProposal(
          requestId,
          offer.calculationType(),
          offer.plan().getId(),
          offer.newPricePaid(),
          offer.newExpiresOn(),
          offer.extraChargeAmount(),
          adminId));
      comments.save(new SubscriptionRequestComment(
          requestId, "platform_admin", adminId, null, proposal.getId()));
      created.add(proposal);
    }
    audit.record(adminId, "SUBSCRIPTION_PRORATION_PROPOSALS_SENT", "subscription_request",
        requestId, null, Map.of("proposal_ids", created.stream().map(SubscriptionRequestProposal::getId).toList()));
    return created.stream().map(this::proposalView).toList();
  }

  @Transactional
  public Map<String, Object> agree(UUID requestId, UUID proposalId) {
    UUID tenantId = tenantContext.tenantId();
    UUID userId = tenantContext.userId();
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    users.findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new NoSuchElementException("Tenant user not found"));

    SubscriptionRequest request = lockTenantRequest(requestId, tenantId);
    requireOpenInstantRequest(request);
    SubscriptionRequestProposal proposal = proposals.lockById(proposalId)
        .filter(row -> requestId.equals(row.getRequestId()))
        .orElseThrow(() -> new NoSuchElementException("Proposal not found"));
    if (!"offered".equals(proposal.getStatus()))
      throw new IllegalStateException("This proration offer is no longer available");

    LocalDate today = LocalDate.now(clock);
    TenantSubscription current = subscriptions.lockCurrentByTenant(tenantId).stream()
        .filter(row -> !row.getExpiresOn().isBefore(today))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The current subscription is no longer active"));
    if (current.isTrial() || !current.getId().equals(request.getCurrentSubscriptionId()))
      throw new IllegalStateException("The current subscription changed after this request was submitted");
    if (subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(tenantId, "scheduled").isPresent())
      throw new IllegalStateException("Cancel the scheduled plan before accepting an immediate change");
    if (proposal.getNewExpiresOn().isBefore(today))
      throw new IllegalStateException("This offer has expired");

    Plan newPlan = plans.findById(proposal.getNewPlanId())
        .orElseThrow(() -> new NoSuchElementException("Proposed plan not found"));
    current.cancel();
    subscriptions.save(current);
    subscriptions.flush();
    TenantSubscription replacement = subscriptions.save(new TenantSubscription(
        tenantId,
        proposal.getNewPlanId(),
        newPlan.getPrice(),
        proposal.getNewPricePaid(),
        "Proration: " + proposal.getCalculationType(),
        today,
        proposal.getNewExpiresOn(),
        "active",
        null,
        newPlan.getModules(),
        false));
    entitlements.applySubscription(tenantId, replacement.getModules(),
        PlatformSubscriptionService.expiresAt(replacement.getExpiresOn()));

    proposal.accept();
    proposals.save(proposal);
    proposals.findAllByRequestIdAndStatus(requestId, "offered").stream()
        .filter(sibling -> !sibling.getId().equals(proposalId))
        .forEach(SubscriptionRequestProposal::supersede);
    request.completeByTenant();
    requests.save(request);
    comments.save(new SubscriptionRequestComment(
        requestId, "tenant_user", userId, "Accepted the "
            + calculationLabel(proposal.getCalculationType()) + " offer. The plan is now active.",
        proposalId));
    audit.recordSystem("SUBSCRIPTION_PRORATION_ACCEPTED", "subscription_request", requestId,
        null, Map.of("tenant_id", tenantId,
            "subscription_id", replacement.getId(), "proposal_id", proposalId));
    return Map.of(
        "status", "completed",
        "subscription", PlatformSubscriptionService.view(replacement, newPlan),
        "accepted_proposal", proposalView(proposal));
  }

  @Transactional
  public Map<String, Object> revoke(UUID requestId) {
    UUID tenantId = tenantContext.tenantId();
    UUID userId = tenantContext.userId();
    tenants.lockById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    SubscriptionRequest request = lockTenantRequest(requestId, tenantId);
    users.findByTenantIdAndId(tenantId, userId)
        .orElseThrow(() -> new NoSuchElementException("Tenant user not found"));
    requireOpenInstantRequest(request);
    request.revokeByTenant();
    requests.save(request);
    proposals.findAllByRequestIdAndStatus(requestId, "offered")
        .forEach(SubscriptionRequestProposal::supersede);
    comments.save(new SubscriptionRequestComment(
        requestId, "tenant_user", userId, "The tenant withdrew this request.", null));
    audit.recordSystem("SUBSCRIPTION_REQUEST_REVOKED", "subscription_request", requestId,
        null, Map.of("tenant_id", tenantId));
    return Map.of("status", "revoked", "request_id", requestId);
  }

  private List<Map<String, Object>> thread(UUID requestId) {
    return comments.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId).stream()
        .map(this::commentView)
        .toList();
  }

  private Map<String, Object> commentView(SubscriptionRequestComment comment) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", comment.getId());
    result.put("request_id", comment.getRequestId());
    result.put("author_type", comment.getAuthorType());
    result.put("author_id", comment.getAuthorId());
    result.put("message", comment.getMessage());
    result.put("proposal_id", comment.getProposalId());
    result.put("created_at", comment.getCreatedAt());
    result.put("proposal", comment.getProposalId() == null ? null
        : proposals.findById(comment.getProposalId()).map(this::proposalView).orElse(null));
    return result;
  }

  private Map<String, Object> proposalView(SubscriptionRequestProposal proposal) {
    Plan plan = plans.findById(proposal.getNewPlanId()).orElse(null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", proposal.getId());
    result.put("request_id", proposal.getRequestId());
    result.put("calculation_type", proposal.getCalculationType());
    result.put("calculation_label", calculationLabel(proposal.getCalculationType()));
    result.put("new_plan_id", proposal.getNewPlanId());
    result.put("new_plan_name", plan == null ? "Unavailable plan" : plan.getName());
    result.put("currency", plan == null ? "INR" : plan.getCurrency());
    result.put("new_price_paid", proposal.getNewPricePaid());
    result.put("new_expires_on", proposal.getNewExpiresOn());
    result.put("extra_charge_amount", proposal.getExtraChargeAmount());
    result.put("status", proposal.getStatus());
    result.put("created_by", proposal.getCreatedBy());
    result.put("created_at", proposal.getCreatedAt());
    return result;
  }

  private List<String> normalizeCalculationTypes(List<String> types) {
    if (types == null || types.isEmpty())
      throw new IllegalArgumentException("Choose at least one proration option");
    List<String> normalized = types.stream()
        .filter(type -> type != null)
        .map(type -> type.trim().toLowerCase())
        .distinct()
        .toList();
    if (normalized.size() != types.size()
        || !Set.of("adjust_days", "pay_difference").containsAll(normalized))
      throw new IllegalArgumentException("Calculation types must be adjust_days or pay_difference");
    return normalized;
  }

  private CalculationContext calculationContext(SubscriptionRequest request) {
    requireOpenInstantRequest(request);
    tenants.lockById(request.getTenantId())
        .orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    LocalDate today = LocalDate.now(clock);
    TenantSubscription current = subscriptions.lockCurrentByTenant(request.getTenantId()).stream()
        .filter(row -> !row.getExpiresOn().isBefore(today))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("The current subscription is no longer active"));
    if (current.isTrial() || !current.getId().equals(request.getCurrentSubscriptionId()))
      throw new IllegalStateException("The current subscription changed after this request was submitted");
    if (subscriptions.findFirstByTenantIdAndStatusOrderByStartsOnDesc(
        request.getTenantId(), "scheduled").isPresent())
      throw new IllegalStateException("Cancel the scheduled plan before proposing an immediate change");

    Plan newPlan = plans.findById(request.getRequestedPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new NoSuchElementException("Requested active plan not found"));
    if (newPlan.getPrice().compareTo(current.getPlanPriceAtTime()) < 0)
      throw new IllegalArgumentException("Mid-cycle proration proposals are not available for cheaper plans");
    if (newPlan.getPrice().signum() <= 0)
      throw new IllegalArgumentException("Proration proposals require a plan with a positive price");
    long oldDurationDays = ChronoUnit.DAYS.between(current.getStartsOn(), current.getExpiresOn());
    long remainingDays = ChronoUnit.DAYS.between(today, current.getExpiresOn());
    long newDurationDays = ChronoUnit.DAYS.between(
        today, PlatformSubscriptionService.expiryDate(today, newPlan.getDuration()));
    if (oldDurationDays <= 0 || remainingDays <= 0 || newDurationDays <= 0)
      throw new IllegalStateException("Subscription dates do not support proration calculations");
    BigDecimal remainingValue = current.getPricePaid()
        .multiply(BigDecimal.valueOf(remainingDays))
        .divide(BigDecimal.valueOf(oldDurationDays), 2, RoundingMode.HALF_UP);
    if (remainingValue.signum() <= 0)
      throw new IllegalStateException("The remaining subscription value is too small to prorate");
    return new CalculationContext(current, newPlan, today, oldDurationDays, remainingDays,
        newDurationDays, remainingValue);
  }

  private ProrationOffer calculate(String type, CalculationContext context) {
    if ("adjust_days".equals(type)) {
      long adjustedDays = context.remainingValue()
          .multiply(BigDecimal.valueOf(context.newDurationDays()))
          .divide(context.newPlan().getPrice(), 0, RoundingMode.DOWN)
          .longValueExact();
      if (adjustedDays < 1)
        throw new IllegalArgumentException(
            "The Adjust Days offer would be shorter than one day; send only Pay the Difference");
      LocalDate expiresOn = context.today().plusDays(adjustedDays);
      return new ProrationOffer(type, context.newPlan(), context.remainingValue(), expiresOn, ZERO);
    }

    BigDecimal newPricePaid = context.newPlan().getPrice()
        .multiply(BigDecimal.valueOf(context.remainingDays()))
        .divide(BigDecimal.valueOf(context.newDurationDays()), 2, RoundingMode.HALF_UP);
    BigDecimal extraCharge = newPricePaid.subtract(context.remainingValue())
        .setScale(2, RoundingMode.HALF_UP);
    if (extraCharge.signum() < 0)
      throw new IllegalArgumentException("The calculated extra charge cannot be negative");
    return new ProrationOffer("pay_difference", context.newPlan(), newPricePaid,
        context.current().getExpiresOn(), extraCharge);
  }

  private Map<String, Object> calculationView(ProrationOffer offer) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("available", true);
    result.put("calculation_type", offer.calculationType());
    result.put("calculation_label", calculationLabel(offer.calculationType()));
    result.put("new_plan", PlatformPlanService.snapshot(offer.plan()));
    result.put("new_price_paid", offer.newPricePaid());
    result.put("new_expires_on", offer.newExpiresOn());
    result.put("extra_charge_amount", offer.extraChargeAmount());
    return result;
  }

  private Map<String, Object> unavailableCalculationView(String type, String reason) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("calculation_type", type);
    result.put("calculation_label", calculationLabel(type));
    result.put("available", false);
    result.put("reason", reason);
    return result;
  }

  private SubscriptionRequest requireInstantRequest(UUID requestId) {
    SubscriptionRequest request = requireRequest(requestId);
    requireOpenInstantRequest(request);
    return request;
  }

  private SubscriptionRequest requireRequest(UUID requestId) {
    return requests.findById(requestId)
        .orElseThrow(() -> new NoSuchElementException("Subscription request not found"));
  }

  private SubscriptionRequest lockRequest(UUID requestId) {
    return requests.lockById(requestId)
        .orElseThrow(() -> new NoSuchElementException("Subscription request not found"));
  }

  private SubscriptionRequest requireTenantRequest(UUID requestId, UUID tenantId) {
    SubscriptionRequest request = requireRequest(requestId);
    if (!tenantId.equals(request.getTenantId()))
      throw new NoSuchElementException("Subscription request not found");
    return request;
  }

  private SubscriptionRequest lockTenantRequest(UUID requestId, UUID tenantId) {
    SubscriptionRequest request = lockRequest(requestId);
    if (!tenantId.equals(request.getTenantId()))
      throw new NoSuchElementException("Subscription request not found");
    return request;
  }

  private void requireOpenInstantRequest(SubscriptionRequest request) {
    if (!INSTANT_CHANGE.equals(request.getTriggerType())
        || !OPEN_STATUSES.contains(request.getStatus()))
      throw new IllegalStateException("This subscription request is closed or not eligible for proration");
  }

  private void requireOpen(SubscriptionRequest request) {
    if (!OPEN_STATUSES.contains(request.getStatus()))
      throw new IllegalStateException("This conversation is closed");
  }

  private String validatedMessage(String message) {
    if (message == null || message.isBlank())
      throw new IllegalArgumentException("Message is required");
    String normalized = message.trim();
    if (normalized.length() > 5000)
      throw new IllegalArgumentException("Message must be at most 5000 characters");
    return normalized;
  }

  private String calculationLabel(String type) {
    return switch (type) {
      case "adjust_days" -> "Adjust Days";
      case "pay_difference" -> "Pay the Difference";
      default -> type;
    };
  }

  private record CalculationContext(
      TenantSubscription current,
      Plan newPlan,
      LocalDate today,
      long oldDurationDays,
      long remainingDays,
      long newDurationDays,
      BigDecimal remainingValue) {}

  private record ProrationOffer(
      String calculationType,
      Plan plan,
      BigDecimal newPricePaid,
      LocalDate newExpiresOn,
      BigDecimal extraChargeAmount) {}
}
