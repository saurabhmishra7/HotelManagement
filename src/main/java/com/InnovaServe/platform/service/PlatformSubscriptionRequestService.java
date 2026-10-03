package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSubscriptionRequestService {
  private final SubscriptionRequestRepository requests;
  private final TenantRepository tenants;
  private final PlanRepository plans;
  private final StaffUserRepository users;
  private final PlatformAuditService audit;

  public PlatformSubscriptionRequestService(
      SubscriptionRequestRepository requests,
      TenantRepository tenants,
      PlanRepository plans,
      StaffUserRepository users,
      PlatformAuditService audit) {
    this.requests = requests;
    this.tenants = tenants;
    this.plans = plans;
    this.users = users;
    this.audit = audit;
  }

  @Transactional
  public Map<String, Object> proposePrice(
      UUID tenantId, UUID planId, BigDecimal price, String note, UUID adminId) {
    tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found"));
    Plan plan = plans.findById(planId).filter(Plan::isActive)
        .orElseThrow(() -> new NoSuchElementException("Active plan not found"));
    if (price == null || price.signum() < 0 || price.scale() > 2 || price.precision() > 10)
      throw new IllegalArgumentException("Proposed price must be a valid non-negative amount");
    if (note != null && note.length() > 1000)
      throw new IllegalArgumentException("Proposal note must be at most 1000 characters");
    if (requests.existsByTenantIdAndStatusIn(tenantId, List.of("pending", "in_review", "awaiting_tenant")))
      throw new IllegalStateException("A subscription request is already open for this tenant");
    UUID contactUser = users.findAllByTenantIdOrderByName(tenantId).stream()
        .filter(user -> user.isActive()).findFirst()
        .orElseThrow(() -> new IllegalStateException("Tenant has no active staff contact"))
        .getId();
    SubscriptionRequest request = new SubscriptionRequest(
        tenantId, contactUser, null, plan.getId(), "negotiated_price",
        note == null || note.isBlank() ? null : note.trim());
    request.propose("platform_negotiated_price", price, adminId);
    SubscriptionRequest saved = requests.save(request);
    audit.record(adminId, "SUBSCRIPTION_PRICE_PROPOSED", "subscription_request",
        saved.getId(), null, view(saved));
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String status) {
    String normalized = status == null || status.isBlank() ? "pending" : status.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("pending", "in_review", "completed", "declined", "awaiting_tenant", "accepted").contains(normalized))
      throw new IllegalArgumentException("Unsupported request status");
    return requests.findAllByStatusOrderBySubmittedAtAsc(normalized).stream()
        .map(this::view).toList();
  }

  @Transactional
  public Map<String, Object> update(UUID id, String status, String responseNote, UUID adminId) {
    SubscriptionRequest request = requests.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Subscription request not found"));
    String next = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("in_review", "completed", "declined").contains(next))
      throw new IllegalArgumentException("Status must be in_review, completed, or declined");
    if (!List.of("pending", "in_review").contains(request.getStatus()))
      throw new IllegalStateException("A resolved request cannot be changed");
    if (responseNote != null && responseNote.length() > 1000)
      throw new IllegalArgumentException("Response note must be at most 1000 characters");
    Map<String, Object> before = view(request);
    request.updateStatus(next, responseNote == null || responseNote.isBlank() ? null : responseNote.trim(), adminId);
    Map<String, Object> after = view(request);
    audit.record(adminId, "SUBSCRIPTION_REQUEST_" + next.toUpperCase(Locale.ROOT),
        "subscription_request", id, before, after);
    return after;
  }

  private Map<String, Object> view(SubscriptionRequest request) {
    Tenant tenant = tenants.findById(request.getTenantId()).orElse(null);
    Plan plan = plans.findById(request.getRequestedPlanId()).orElse(null);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", request.getId());
    result.put("tenant_id", request.getTenantId());
    result.put("tenant_name", tenant == null ? "Unknown tenant" : tenant.getName());
    result.put("tenant_code", tenant == null ? null : tenant.getTenantCode());
    result.put("requested_by", request.getRequestedBy());
    result.put("current_subscription_id", request.getCurrentSubscriptionId());
    result.put("request_type", request.getRequestType());
    result.put("trigger_type", request.getTriggerType());
    result.put("status", request.getStatus());
    result.put("proposed_price", request.getProposedPrice());
    result.put("proposed_by", request.getProposedBy());
    result.put("message", request.getMessage());
    result.put("response_note", request.getResponseNote());
    result.put("submitted_at", request.getSubmittedAt());
    result.put("updated_at", request.getUpdatedAt());
    result.put("requested_plan", plan == null ? null : PlatformPlanService.snapshot(plan));
    return result;
  }
}
