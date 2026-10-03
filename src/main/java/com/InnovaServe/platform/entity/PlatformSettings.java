package com.InnovaServe.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_settings", schema = "platform")
public class PlatformSettings {
  @Id
  private String id;

  @Column(name = "signup_mode", nullable = false, length = 10)
  private String signupMode;

  @Column(name = "trial_plan_id")
  private UUID trialPlanId;

  @Column(name = "trial_duration_days", nullable = false)
  private int trialDurationDays;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlatformSettings() {}

  public String getId() { return id; }
  public String getSignupMode() { return signupMode; }
  public UUID getTrialPlanId() { return trialPlanId; }
  public int getTrialDurationDays() { return trialDurationDays; }
  public UUID getUpdatedBy() { return updatedBy; }
  public Instant getUpdatedAt() { return updatedAt; }

  public void update(String mode, UUID planId, int trialDays, UUID adminId) {
    signupMode = mode;
    trialPlanId = planId;
    trialDurationDays = trialDays;
    updatedBy = adminId;
    updatedAt = Instant.now();
  }
}
