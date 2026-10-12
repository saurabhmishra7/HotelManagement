package com.InnovaServe.stay.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stay_charge", schema = "stay")
public class StayCharge extends TenantEntity {
  @Column(name = "stay_id", nullable = false)
  private UUID stayId;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "added_by", nullable = false)
  private UUID addedBy;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected StayCharge() {}

  public StayCharge(UUID t, UUID stay, String description, int quantity, BigDecimal unitPrice,
      BigDecimal amount, UUID user) {
    super(t);
    stayId = stay;
    this.description = description;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.amount = amount;
    addedBy = user;
  }

  public UUID getStayId() {
    return stayId;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }
}
