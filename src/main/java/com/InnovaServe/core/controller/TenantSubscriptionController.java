package com.InnovaServe.core.controller;

import com.InnovaServe.platform.service.TenantSubscriptionRequestService;
import com.InnovaServe.platform.service.TenantSubscriptionCheckoutService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/subscription")
public class TenantSubscriptionController {
  private final TenantSubscriptionRequestService service;
  private final TenantSubscriptionCheckoutService checkouts;

  public TenantSubscriptionController(TenantSubscriptionRequestService service,
      TenantSubscriptionCheckoutService checkouts) {
    this.service = service;
    this.checkouts = checkouts;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public Map<String, Object> view() {
    return service.view();
  }

  @PostMapping("/requests")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_REQUEST')")
  public Map<String, Object> request(@RequestBody Request body) {
    return service.request(body.requestType(), body.planId(), body.message());
  }

  @PostMapping("/requests/{id}/accept")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_REQUEST')")
  public Map<String, Object> acceptProposal(@org.springframework.web.bind.annotation.PathVariable UUID id) {
    return checkouts.acceptProposal(id);
  }

  @PostMapping("/activate")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public Map<String, Object> activate(@RequestBody PlanRequest body) {
    return checkouts.begin("activate", body.planId());
  }

  @PostMapping("/schedule")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public Map<String, Object> schedule(@RequestBody PlanRequest body) {
    return checkouts.begin("schedule", body.planId());
  }

  @PostMapping("/checkout/complete")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public Map<String, Object> completeCheckout(
      @RequestBody TenantSubscriptionCheckoutService.PaymentCompletion body) {
    return checkouts.complete(body);
  }

  public record Request(
      @JsonProperty("request_type") String requestType,
      @JsonProperty("plan_id") UUID planId,
      String message) {}

  public record PlanRequest(@JsonProperty("plan_id") UUID planId) {}
}
