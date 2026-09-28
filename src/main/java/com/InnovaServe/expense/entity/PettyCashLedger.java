package com.InnovaServe.expense.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "petty_cash_ledger", schema = "expense")
public class PettyCashLedger extends TenantEntity {
  @Column(name = "shift_date", nullable = false)
  private LocalDate shiftDate;

  @Column(name = "opening_float", nullable = false, precision = 10, scale = 2)
  private BigDecimal openingFloat;

  @Column(name = "top_up_amount", nullable = false, precision = 10, scale = 2)
  private BigDecimal topUpAmount = BigDecimal.ZERO;

  @Column(name = "closing_balance_expected", nullable = false, precision = 10, scale = 2)
  private BigDecimal expected;

  @Column(name = "closing_balance_actual", precision = 10, scale = 2)
  private BigDecimal actual;

  @Column(name = "reconciled_by")
  private UUID reconciledBy;

  @Column(name = "reconciled_at")
  private LocalDateTime reconciledAt;

  protected PettyCashLedger() {}

  public PettyCashLedger(UUID t, LocalDate date, BigDecimal opening) {
    super(t);
    shiftDate = date;
    openingFloat = opening;
    expected = opening;
  }

  public LocalDate getShiftDate() { return shiftDate; }
  public BigDecimal getOpeningFloat() { return openingFloat; }
  public BigDecimal getTopUpAmount() { return topUpAmount; }
  public BigDecimal getClosingBalanceExpected() { return expected; }
  public BigDecimal getClosingBalanceActual() { return actual; }
  public UUID getReconciledBy() { return reconciledBy; }
  public LocalDateTime getReconciledAt() { return reconciledAt; }

  public void topUp(BigDecimal amount) {
    if (reconciledAt != null) throw new IllegalStateException("Ledger already reconciled");
    topUpAmount = topUpAmount.add(amount);
    expected = expected.add(amount);
  }

  public void reconcile(BigDecimal actual, UUID user) {
    if (reconciledAt != null) throw new IllegalStateException("Ledger already reconciled");
    this.actual = actual;
    reconciledBy = user;
    reconciledAt = LocalDateTime.now();
  }
}
