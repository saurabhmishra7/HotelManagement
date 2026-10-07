package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tax_rule", schema = "core")
public class TaxRule extends TenantEntity {
  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "applies_to", nullable = false, length = 20)
  private String appliesTo;

  @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal ratePercent;

  @Column(name = "itc_eligible", nullable = false)
  private boolean itcEligible;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  protected TaxRule() {}

  public TaxRule(
      UUID t, String name, String applies, BigDecimal rate, boolean itc, LocalDate from) {
    super(t);
    this.name = name;
    appliesTo = applies;
    ratePercent = rate;
    itcEligible = itc;
    effectiveFrom = from;
  }

  public String getName() {
    return name;
  }

  public boolean isItcEligible() {
    return itcEligible;
  }

  public BigDecimal getRatePercent() {
    return ratePercent;
  }

  public String getAppliesTo() {
    return appliesTo;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public void supersede(LocalDate to) {
    effectiveTo = to;
  }
}
