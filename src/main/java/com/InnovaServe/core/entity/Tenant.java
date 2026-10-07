package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant", schema = "core")
public class Tenant {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(name = "tenant_code", nullable = false, unique = true, length = 12)
  private String tenantCode;

  @Column(length = 15)
  private String gstin;

  @Column(columnDefinition = "text")
  private String address;

  @Column(name = "primary_owner_email", length = 150)
  private String primaryOwnerEmail;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "checkout_time", nullable = false)
  private LocalTime checkoutTime = LocalTime.of(11, 0);

  @ElementCollection
  @CollectionTable(name = "tenant_meal_plan", schema = "core",
      joinColumns = @JoinColumn(name = "tenant_id"))
  @Column(name = "plan", nullable = false, length = 5)
  private Set<String> mealPlans = new LinkedHashSet<>(Set.of("EP"));

  @JsonIgnore
  @Column(name = "logo", columnDefinition = "bytea")
  private byte[] logo;

  @Column(name = "logo_version")
  private UUID logoVersion;

  public LocalTime getCheckoutTime() { return checkoutTime; }
  @JsonIgnore
  public Set<String> getMealPlans() { return mealPlans; }
  @JsonIgnore
  public byte[] getLogo() { return logo; }
  public UUID getLogoVersion() { return logoVersion; }

  public void updateProfile(String name, String gstin, String address) {
    this.name = name;
    this.gstin = gstin;
    this.address = address;
  }

  public void updateRules(LocalTime checkoutTime, Set<String> plans) {
    this.checkoutTime = checkoutTime;
    this.mealPlans.clear();
    this.mealPlans.addAll(plans);
  }

  public void setLogo(byte[] logo) {
    this.logo = logo;
    this.logoVersion = logo == null ? null : UUID.randomUUID();
  }

  protected Tenant() {}

  public Tenant(String name, String gstin, String address) {
    this.name = name;
    this.tenantCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    this.gstin = gstin;
    this.address = address;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTenantCode() {
    return tenantCode;
  }

  public String getGstin() {
    return gstin;
  }

  public String getAddress() {
    return address;
  }

  public String getPrimaryOwnerEmail() { return primaryOwnerEmail; }

  public void setPrimaryOwnerEmail(String email) {
    this.primaryOwnerEmail = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
