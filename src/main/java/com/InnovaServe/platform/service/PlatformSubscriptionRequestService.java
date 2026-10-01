package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSubscriptionRequestService {
  private final SubscriptionRequestRepository requests;
  private final TenantRepository tenants;
  private final PlanRepository plans;
  private final PlatformAuditService audit;

  public PlatformSubscriptionRequestService(
      SubscriptionRequestRepository requests,
      TenantRepository tenants,
      PlanRepository plans,
      PlatformAuditService audit) {
    this.requests = requests;
    this.tenants = tenants;
    this.plans = plans;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String status) {
    String normalized = status == null || status.isBlank() ? "pending" : status.trim().toLowerCase(Locale.ROOT);
    if (!Set.of("pending", "in_review", "completed", "declined").contains(normalized))
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
    result.put("status", request.getStatus());
    result.put("message", request.getMessage());
    result.put("response_note", request.getResponseNote());
    result.put("submitted_at", request.getSubmittedAt());
    result.put("updated_at", request.getUpdatedAt());
    result.put("requested_plan", plan == null ? null : PlatformPlanService.snapshot(plan));
    return result;
  }
}
