package com.InnovaServe.core.controller;

import com.InnovaServe.platform.service.TenantSubscriptionRequestService;
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

  public TenantSubscriptionController(TenantSubscriptionRequestService service) {
    this.service = service;
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

  public record Request(
      @JsonProperty("request_type") String requestType,
      @JsonProperty("plan_id") UUID planId,
      String message) {}
}
