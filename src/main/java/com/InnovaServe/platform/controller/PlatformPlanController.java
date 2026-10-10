package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.PlatformPlanService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/plans")
public class PlatformPlanController {
  private final PlatformPlanService plans;

  public PlatformPlanController(PlatformPlanService plans) {
    this.plans = plans;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLATFORM_PLANS_READ')")
  public List<Map<String, Object>> list(
      @RequestParam(defaultValue = "false") boolean include_inactive) {
    return plans.list(include_inactive).stream().map(PlatformPlanService::snapshot).toList();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PLATFORM_PLANS_MANAGE')")
  public Map<String, Object> create(@RequestBody PlanRequest request) {
    return PlatformPlanService.snapshot(
        plans.create(
            request.name(),
            request.price(),
            request.currency(),
            request.duration(),
            request.modules(),
            currentAdminId()));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PLATFORM_PLANS_MANAGE')")
  public Map<String, Object> update(
      @PathVariable UUID id, @RequestBody PlanChangeRequest request) {
    return plans.update(
            id,
            request.name(),
            request.price(),
            request.currency(),
            request.duration(),
            request.modules(),
            request.active(),
            currentAdminId());
  }

  private UUID currentAdminId() {
    PlatformAdmin admin =
        (PlatformAdmin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return admin.getId();
  }

  public record PlanRequest(
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Collection<String> modules) {}

  public record PlanChangeRequest(
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Collection<String> modules,
      Boolean active) {}
}
