package com.InnovaServe.platform.entity;

import com.InnovaServe.platform.security.PlatformRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admin", schema = "platform")
public class PlatformAdmin {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 150)
  private String email;

  @Column(name = "password_hash", nullable = false, columnDefinition = "text")
  @JsonIgnore
  private String passwordHash;

  @Column(nullable = false, length = 30)
  private String role;

  @Column(nullable = false)
  private boolean active = true;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  private Instant updatedAt;

  protected PlatformAdmin() {}

  public PlatformAdmin(String name, String email, String passwordHash, PlatformRole role) {
    this.name = name.trim();
    this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
    this.passwordHash = passwordHash;
    this.role = role.name();
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public PlatformRole roleType() {
    return PlatformRole.valueOf(role);
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void changeRole(PlatformRole next) {
    role = next.name();
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  @PreUpdate
  void markUpdated() {
    updatedAt = Instant.now();
  }
}
