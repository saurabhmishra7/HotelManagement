package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_bill", schema = "restaurant")
public class RestaurantBill extends TenantEntity {
  @Column(name = "order_id", nullable = false, unique = true)
  private UUID orderId;

  @Column(name = "invoice_id", nullable = false, unique = true)
  private UUID invoiceId;

  @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal discountAmount = BigDecimal.ZERO;

  @Column(name = "discount_approved_by")
  private UUID discountApprovedBy;

  @Column(name = "settlement_mode", nullable = false, length = 10)
  private String settlementMode;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected RestaurantBill() {}

  public RestaurantBill(UUID t, UUID order, UUID invoice) {
    super(t);
    orderId = order;
    invoiceId = invoice;
    settlementMode = "cash";
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public BigDecimal getDiscountAmount() { return discountAmount; }
  public UUID getDiscountApprovedBy() { return discountApprovedBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }

  public String getSettlementMode() {
    return settlementMode;
  }

  public void settle(String mode) {
    settlementMode = mode;
  }
}
