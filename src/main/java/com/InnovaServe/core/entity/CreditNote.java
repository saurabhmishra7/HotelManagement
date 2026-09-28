package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_note", schema = "core")
public class CreditNote extends TenantEntity {
  @Column(name = "original_invoice_id", nullable = false)
  private UUID originalInvoiceId;

  @Column(nullable = false, columnDefinition = "text")
  private String reason;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(name = "approved_by", nullable = false)
  private UUID approvedBy;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected CreditNote() {}

  public CreditNote(UUID tenantId, UUID invoiceId, String reason, BigDecimal amount, UUID approvedBy) {
    super(tenantId);
    this.originalInvoiceId = invoiceId;
    this.reason = reason;
    this.amount = amount;
    this.approvedBy = approvedBy;
  }

  public UUID getOriginalInvoiceId() { return originalInvoiceId; }
  public String getReason() { return reason; }
  public BigDecimal getAmount() { return amount; }
  public UUID getApprovedBy() { return approvedBy; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
