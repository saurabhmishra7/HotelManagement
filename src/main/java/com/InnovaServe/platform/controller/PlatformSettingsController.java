package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.PlatformSettingsService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/settings")
@PreAuthorize("hasAuthority('PLATFORM_SETTINGS_MANAGE')")
public class PlatformSettingsController {
  private final PlatformSettingsService settings;

  public PlatformSettingsController(PlatformSettingsService settings) {
    this.settings = settings;
  }

  @GetMapping
  public Map<String, Object> get() {
    return settings.get();
  }

  @PatchMapping
  public Map<String, Object> update(@RequestBody SettingsRequest request) {
    PlatformAdmin admin = (PlatformAdmin) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
    return settings.update(
        request.signupMode(), request.trialPlanId(), request.trialDurationDays(), admin.getId());
  }

  public record SettingsRequest(
      @JsonProperty("signup_mode") String signupMode,
      @JsonProperty("trial_plan_id") UUID trialPlanId,
      @JsonProperty("trial_duration_days") Integer trialDurationDays) {}
}
