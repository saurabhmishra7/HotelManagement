package com.InnovaServe.platform.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "plan", schema = "platform")
public class Plan {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(nullable = false)
  private int version;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false, length = 20)
  private String duration;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "plan_module",
      schema = "platform",
      joinColumns = @JoinColumn(name = "plan_id"))
  @Column(name = "module", nullable = false, length = 20)
  private Set<String> modules = new HashSet<>();

  protected Plan() {}

  public Plan(
      UUID familyId,
      int version,
      String name,
      BigDecimal price,
      String currency,
      String duration,
      Set<String> modules) {
    this.familyId = familyId;
    this.version = version;
    this.name = name.trim();
    this.price = price;
    this.currency = currency;
    this.duration = duration;
    this.modules = new HashSet<>(modules);
  }

  public UUID getId() {
    return id;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public int getVersion() {
    return version;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public String getCurrency() {
    return currency;
  }

  public String getDuration() {
    return duration;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Set<String> getModules() {
    return Set.copyOf(modules);
  }

  public void retire() {
    active = false;
  }
}
