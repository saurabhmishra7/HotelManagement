package com.InnovaServe.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

  @Column(length = 15)
  private String gstin;

  @Column(columnDefinition = "text")
  private String address;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Tenant() {}

  public Tenant(String name, String gstin, String address) {
    this.name = name;
    this.gstin = gstin;
    this.address = address;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getGstin() {
    return gstin;
  }

  public String getAddress() {
    return address;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
