package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.PlatformSubscriptionService;
import com.InnovaServe.platform.service.PlatformTenantService;
import com.InnovaServe.platform.service.PlatformSubscriptionRequestService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatformTenantController {
  private final PlatformTenantService tenants;
  private final PlatformSubscriptionService subscriptions;
  private final PlatformSubscriptionRequestService subscriptionRequests;

  public PlatformTenantController(
      PlatformTenantService tenants, PlatformSubscriptionService subscriptions,
      PlatformSubscriptionRequestService subscriptionRequests) {
    this.tenants = tenants;
    this.subscriptions = subscriptions;
    this.subscriptionRequests = subscriptionRequests;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLATFORM_TENANTS_READ')")
  public Map<String, Object> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
    return tenants.list(search, status, page, pageSize);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('PLATFORM_TENANTS_READ')")
  public Map<String, Object> detail(@PathVariable UUID id) {
    return tenants.detail(id);
  }

  @PostMapping("/{id}/subscription")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> subscribe(
      @PathVariable UUID id, @RequestBody SubscriptionRequest request) {
    return subscriptions.subscribe(
        id,
        request.planId(),
        request.pricePaid(),
        request.negotiationNote(),
        request.startsOn(),
        currentAdminId());
  }

  @PostMapping("/{id}/subscription/cancel")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> cancel(@PathVariable UUID id) {
    return subscriptions.cancel(id, currentAdminId());
  }

  @PostMapping("/{id}/subscription-proposals")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> proposeSubscription(
      @PathVariable UUID id, @RequestBody ProposalRequest request) {
    return subscriptionRequests.proposePrice(id, request.planId(), request.proposedPrice(),
        request.message(), currentAdminId());
  }

  private UUID currentAdminId() {
    PlatformAdmin admin =
        (PlatformAdmin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return admin.getId();
  }

  public record SubscriptionRequest(
      @JsonProperty("plan_id") UUID planId,
      @JsonProperty("price_paid") BigDecimal pricePaid,
      @JsonProperty("negotiation_note") String negotiationNote,
      @JsonProperty("starts_on") LocalDate startsOn) {}

  public record ProposalRequest(
      @JsonProperty("plan_id") UUID planId,
      @JsonProperty("proposed_price") BigDecimal proposedPrice,
      String message) {}
}
