package com.InnovaServe.stay.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "stay",
    schema = "stay",
    uniqueConstraints = @UniqueConstraint(name = "uq_stay_account", columnNames = "account_id"))
public class Stay extends TenantEntity {
  @Column(name = "room_id", nullable = false)
  private UUID roomId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "checkout_invoice_id", unique = true)
  private UUID checkoutInvoiceId;

  @Column(name = "guest_count", nullable = false)
  private short guestCount;

  @Column(name = "is_foreign_guest", nullable = false)
  private boolean foreignGuest;

  @Column(nullable = false, length = 5)
  private String plan;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal tariff;

  @Column(name = "check_in_at", nullable = false)
  private LocalDateTime checkInAt;

  @Column(name = "expected_check_out_at", nullable = false)
  private LocalDateTime expectedCheckOutAt;

  @Column(name = "actual_check_out_at")
  private LocalDateTime actualCheckOutAt;

  @Column(name = "advance_paid", nullable = false, precision = 10, scale = 2)
  private BigDecimal advancePaid;

  @Column(nullable = false, length = 15)
  private String status;

  protected Stay() {}

  public Stay(
      UUID tenant,
      UUID room,
      UUID customer,
      UUID account,
      short guests,
      boolean foreign,
      String plan,
      BigDecimal tariff,
      LocalDateTime expected,
      BigDecimal advance) {
    super(tenant);
    roomId = room;
    customerId = customer;
    accountId = account;
    guestCount = guests;
    foreignGuest = foreign;
    this.plan = plan;
    this.tariff = tariff;
    checkInAt = LocalDateTime.now();
    expectedCheckOutAt = expected;
    advancePaid = advance;
    status = "active";
  }

  public UUID getRoomId() {
    return roomId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getCheckoutInvoiceId() {
    return checkoutInvoiceId;
  }

  public void setCheckoutInvoiceId(UUID checkoutInvoiceId) {
    this.checkoutInvoiceId = checkoutInvoiceId;
  }

  public short getGuestCount() {
    return guestCount;
  }

  public String getPlan() {
    return plan;
  }

  public BigDecimal getTariff() {
    return tariff;
  }

  public LocalDateTime getCheckInAt() {
    return checkInAt;
  }

  public LocalDateTime getExpectedCheckOutAt() {
    return expectedCheckOutAt;
  }

  public LocalDateTime getActualCheckOutAt() {
    return actualCheckOutAt;
  }

  public BigDecimal getAdvancePaid() {
    return advancePaid;
  }

  public String getStatus() {
    return status;
  }

  public boolean isForeignGuest() {
    return foreignGuest;
  }

  public void checkout() {
    status = "checked_out";
    actualCheckOutAt = LocalDateTime.now();
  }
}
