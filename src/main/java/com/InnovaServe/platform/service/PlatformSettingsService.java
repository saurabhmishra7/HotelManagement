package com.InnovaServe.platform.service;

import com.InnovaServe.platform.entity.PlatformSettings;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.PlatformSettingsRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformSettingsService {
  private final PlatformSettingsRepository settings;
  private final PlanRepository plans;
  private final PlatformAuditService audit;

  public PlatformSettingsService(
      PlatformSettingsRepository settings, PlanRepository plans, PlatformAuditService audit) {
    this.settings = settings;
    this.plans = plans;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get() {
    return view(settings.findById("GLOBAL").orElseThrow(
        () -> new IllegalStateException("Global platform settings are missing")));
  }

  @Transactional
  public Map<String, Object> update(
      String signupMode, UUID trialPlanId, Integer trialDurationDays, UUID adminId) {
    PlatformSettings global = settings.lockById("GLOBAL").orElseThrow(
        () -> new IllegalStateException("Global platform settings are missing"));
    String mode = signupMode == null ? global.getSignupMode() : signupMode.trim().toLowerCase();
    UUID planId = trialPlanId == null ? global.getTrialPlanId() : trialPlanId;
    int days = trialDurationDays == null ? global.getTrialDurationDays() : trialDurationDays;
    if (!"standard".equals(mode) && !"trial".equals(mode))
      throw new IllegalArgumentException("signup_mode must be standard or trial");
    if (days < 1 || days > 365)
      throw new IllegalArgumentException("trial_duration_days must be between 1 and 365");
    if ("trial".equals(mode)) {
      if (planId == null) throw new IllegalArgumentException("trial_plan_id is required in trial mode");
      plans.findById(planId).filter(plan -> plan.isActive())
          .orElseThrow(() -> new IllegalArgumentException("Trial plan must be active"));
    }
    Map<String, Object> before = view(global);
    global.update(mode, planId, days, adminId);
    Map<String, Object> after = view(global);
    audit.record(adminId, "PLATFORM_SETTINGS_UPDATED", "platform_settings",
        UUID.nameUUIDFromBytes("GLOBAL".getBytes(java.nio.charset.StandardCharsets.UTF_8)), before, after);
    return after;
  }

  private static Map<String, Object> view(PlatformSettings settings) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("signup_mode", settings.getSignupMode());
    result.put("trial_plan_id", settings.getTrialPlanId());
    result.put("trial_duration_days", settings.getTrialDurationDays());
    result.put("updated_by", settings.getUpdatedBy());
    result.put("updated_at", settings.getUpdatedAt());
    return result;
  }
}
