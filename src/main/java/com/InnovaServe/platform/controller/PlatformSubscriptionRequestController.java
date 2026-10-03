package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.PlatformSubscriptionRequestService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/subscription-requests")
public class PlatformSubscriptionRequestController {
  private final PlatformSubscriptionRequestService service;

  public PlatformSubscriptionRequestController(PlatformSubscriptionRequestService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
    return service.list(status);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateRequest body) {
    PlatformAdmin admin = (PlatformAdmin) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
    return service.update(id, body.status(), body.responseNote(), admin.getId());
  }


  public record UpdateRequest(
      String status, @JsonProperty("response_note") String responseNote) {}

}
