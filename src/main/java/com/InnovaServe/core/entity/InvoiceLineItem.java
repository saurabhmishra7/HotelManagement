package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_item", schema = "core")
public class InvoiceLineItem extends TenantEntity {
  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, precision = 8, scale = 2)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "tax_rule_id")
  private UUID taxRuleId;

  @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
  private BigDecimal lineTotal;

  protected InvoiceLineItem() {}

  public InvoiceLineItem(
      UUID t,
      UUID invoice,
      String description,
      BigDecimal qty,
      BigDecimal price,
      UUID taxRule,
      BigDecimal tax,
      BigDecimal total) {
    super(t);
    invoiceId = invoice;
    this.description = description;
    quantity = qty;
    unitPrice = price;
    taxRuleId = taxRule;
    taxAmount = tax;
    lineTotal = total;
  }

  public UUID getInvoiceId() { return invoiceId; }
  public String getDescription() { return description; }
  public BigDecimal getQuantity() { return quantity; }
  public BigDecimal getUnitPrice() { return unitPrice; }
  public UUID getTaxRuleId() { return taxRuleId; }
  public BigDecimal getTaxAmount() { return taxAmount; }
  public BigDecimal getLineTotal() { return lineTotal; }
}
