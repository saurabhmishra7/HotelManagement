package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(
    name = "invoice",
    schema = "core",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_invoice_tenant_fy_number",
            columnNames = {"tenant_id", "financial_year", "invoice_number"}))
public class Invoice extends TenantEntity {
  @Column(name = "invoice_number", nullable = false, length = 30)
  private String invoiceNumber;

  @Column(name = "financial_year", nullable = false, length = 9)
  private String financialYear;

  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "customer_id")
  private UUID customerId;

  @Column(name = "source_module", nullable = false, length = 20)
  private String sourceModule;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 10)
  private String status;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "locked_at")
  private LocalDateTime lockedAt;

  protected Invoice() {}

  public Invoice(
      UUID t,
      String number,
      String year,
      UUID account,
      UUID customer,
      String source,
      BigDecimal subtotal,
      BigDecimal tax,
      BigDecimal total) {
    super(t);
    invoiceNumber = number;
    financialYear = year;
    accountId = account;
    customerId = customer;
    sourceModule = source;
    this.subtotal = subtotal;
    taxAmount = tax;
    totalAmount = total;
    status = "draft";
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public String getFinancialYear() {
    return financialYear;
  }

  public String getSourceModule() {
    return sourceModule;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public LocalDateTime getLockedAt() {
    return lockedAt;
  }

  public void lock() {
    if (!"draft".equals(status)) throw new IllegalStateException("AlreadyLocked");
    status = "final";
    lockedAt = LocalDateTime.now();
  }

  public void markCredited() {
    if (!"final".equals(status)) throw new IllegalStateException("Invoice must be final");
    status = "credited";
  }

  public void postToAccount(UUID account) {
    accountId = account;
  }

  public void associateCustomerIfMissing(UUID customer) {
    if (customerId == null) customerId = customer;
  }
}
