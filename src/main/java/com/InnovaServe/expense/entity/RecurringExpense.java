package com.InnovaServe.expense.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_expense", schema = "expense")
public class RecurringExpense extends TenantEntity {
  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 10)
  private String frequency;

  @Column(name = "next_due_date", nullable = false)
  private LocalDate nextDueDate;

  @Column(name = "last_paid_date")
  private LocalDate lastPaidDate;

  protected RecurringExpense() {}

  public RecurringExpense(
      UUID t, UUID cat, String desc, BigDecimal amt, String freq, LocalDate due) {
    super(t);
    categoryId = cat;
    description = desc;
    amount = amt;
    frequency = freq;
    nextDueDate = due;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getFrequency() {
    return frequency;
  }

  public LocalDate getNextDueDate() {
    return nextDueDate;
  }

  public LocalDate getLastPaidDate() { return lastPaidDate; }

  public void markPaid(LocalDate date) {
    lastPaidDate = date;
    nextDueDate = "weekly".equals(frequency) ? nextDueDate.plusWeeks(1) : nextDueDate.plusMonths(1);
  }
}
