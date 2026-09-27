package com.InnovaServe.expense.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "expense", schema = "expense")
public class Expense extends TenantEntity {
  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 15)
  private String department;

  @Column(name = "vendor_name", length = 150)
  private String vendorName;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(name = "payment_mode", nullable = false, length = 15)
  private String paymentMode;

  @Column(name = "receipt_file_ref", length = 300)
  private String receiptFileRef;

  @Column(name = "entered_by", nullable = false)
  private UUID enteredBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approval_status", nullable = false, length = 15)
  private String approvalStatus;

  @Column(name = "expense_date", nullable = false)
  private LocalDate expenseDate;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected Expense() {}

  public Expense(
      UUID t,
      UUID category,
      String dept,
      String vendor,
      BigDecimal amount,
      String mode,
      String receipt,
      UUID user,
      LocalDate date) {
    super(t);
    categoryId = category;
    department = dept;
    vendorName = vendor;
    this.amount = amount;
    paymentMode = mode;
    receiptFileRef = receipt;
    enteredBy = user;
    approvalStatus = "not_required";
    expenseDate = date;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void markPending() {
    approvalStatus = "pending";
  }

  public void approve(UUID by) {
    if (!"pending".equals(approvalStatus)) throw new IllegalStateException("AlreadyApproved");
    approvalStatus = "approved";
    approvedBy = by;
  }
}
