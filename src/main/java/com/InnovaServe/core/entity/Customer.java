package com.InnovaServe.core.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "customer",
    schema = "core",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_customer_tenant_phone",
            columnNames = {"tenant_id", "phone"}))
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 15)
  private String phone;

  @Column(name = "id_proof_type", length = 20)
  private String idProofType;

  @Column(name = "id_proof_number", length = 50)
  private String idProofNumber;

  @Column(name = "id_proof_type_other", length = 100)
  private String idProofTypeOther;

  @Column(columnDefinition = "text")
  private String address;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Customer() {}

  public Customer(
      UUID tenantId,
      String name,
      String phone,
      String idProofType,
      String idProofNumber,
      String address) {
    this(tenantId, name, phone, idProofType, idProofNumber, null, address);
  }

  public Customer(
      UUID tenantId,
      String name,
      String phone,
      String idProofType,
      String idProofNumber,
      String idProofTypeOther,
      String address) {
    this.tenantId = tenantId;
    this.name = name;
    this.phone = phone;
    this.idProofType = idProofType;
    this.idProofNumber = idProofNumber;
    this.idProofTypeOther = idProofTypeOther;
    this.address = address;
  }

  public UUID getId() {
    return id;
  }

  @JsonProperty("tenant_id")
  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  @JsonProperty("id_proof_type")
  public String getIdProofType() {
    return idProofType;
  }

  @JsonProperty("id_proof_number")
  public String getIdProofNumber() {
    return idProofNumber;
  }

  @JsonProperty("id_proof_type_other")
  public String getIdProofTypeOther() {
    return idProofTypeOther;
  }

  public String getAddress() {
    return address;
  }

  @JsonProperty("created_at")
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void update(String name, String idProofType, String idProofNumber, String address) {
    this.name = name;
    this.idProofType = idProofType;
    this.idProofNumber = idProofNumber;
    this.address = address;
  }

  public void updateProfile(
      String name,
      String phone,
      String idProofType,
      String idProofNumber,
      String idProofTypeOther,
      String address) {
    this.name = name;
    this.phone = phone;
    this.idProofType = idProofType;
    this.idProofNumber = idProofNumber;
    this.idProofTypeOther = idProofTypeOther;
    this.address = address;
  }
}
