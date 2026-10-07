package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.security.ModuleType;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BillingService {
  private final TenantContext tenant;
  private final InvoiceRepository invoices;
  private final InvoiceLineItemRepository lines;
  private final PaymentRepository payments;
  private final AccountRepository accounts;
  private final TaxRuleRepository taxes;
  private final CreditNoteRepository creditNotes;
  private final ModuleEntitlementService moduleEntitlements;

  public BillingService(
      TenantContext t,
      InvoiceRepository i,
      InvoiceLineItemRepository l,
      PaymentRepository p,
      AccountRepository a,
      TaxRuleRepository tax,
      CreditNoteRepository creditNotes,
      ModuleEntitlementService moduleEntitlements) {
    tenant = t;
    invoices = i;
    lines = l;
    payments = p;
    accounts = a;
    taxes = tax;
    this.creditNotes = creditNotes;
    this.moduleEntitlements = moduleEntitlements;
  }

  public List<TaxRule> taxes(String applies) {
    return taxes(applies, false);
  }

  public List<TaxRule> taxes(String applies, boolean includeInactive) {
    LocalDate d = LocalDate.now();
    return taxes.findAllByTenantId(tenant.tenantId()).stream()
        .filter(
            x ->
                (applies == null || applies.equals(x.getAppliesTo()))
                    && taxModuleEnabled(x.getAppliesTo())
                    && (includeInactive || (!x.getEffectiveFrom().isAfter(d)
                    && (x.getEffectiveTo() == null || !x.getEffectiveTo().isBefore(d)))))
        .toList();
  }

  @Transactional
  public TaxRule addTax(
      String name, String applies, BigDecimal rate, boolean itc, LocalDate effective) {
    if (!Set.of("room", "restaurant", "other").contains(applies))
      throw new IllegalArgumentException("Invalid tax category");
    if (name == null || name.isBlank() || name.length() > 100)
      throw new IllegalArgumentException("Tax rule name is required and must be at most 100 characters");
    if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("100")) > 0)
      throw new IllegalArgumentException("Tax rate must be between 0 and 100");
    if (effective == null) throw new IllegalArgumentException("Effective start date is required");
    requireTaxModule(applies);
    return taxes.save(new TaxRule(tenant.tenantId(), name, applies, rate, itc, effective));
  }

  @Transactional
  public TaxRule supersede(UUID id, LocalDate to) {
    TaxRule rule =
        taxes
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Tax rule not found"));
    requireTaxModule(rule.getAppliesTo());
    if (to == null || to.isBefore(rule.getEffectiveFrom()))
      throw new IllegalArgumentException("End date must be on or after the effective start date");
    rule.supersede(to);
    return rule;
  }

  @Transactional
  public Account openAccount(String module, String type, UUID entity) {
    if ("stay".equals(module))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    else if ("pos".equals(module))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
    return accounts.save(new Account(tenant.tenantId(), module, type, entity));
  }

  public Account account(UUID id) {
    Account account = accounts
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Account not found"));
    if ("stay".equals(account.getOpenedByModule()))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    else if ("pos".equals(account.getOpenedByModule()))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
    return account;
  }

  public List<Invoice> invoicesForAccount(UUID id) {
    account(id);
    return invoices.findAllByTenantIdAndAccountId(tenant.tenantId(), id);
  }

  @Transactional
  public SettlementResult settleOutstandingAccount(UUID accountId, String mode) {
    if (mode == null || !Set.of("cash", "card", "upi").contains(mode)) {
      throw new IllegalArgumentException("Settlement mode must be cash, card, or upi");
    }

    UUID tenantId = tenant.tenantId();
    Account account =
        accounts
            .findByTenantIdAndIdForUpdate(tenantId, accountId)
            .orElseThrow(() -> new NoSuchElementException("Account not found"));
    if ("stay".equals(account.getOpenedByModule())) {
      moduleEntitlements.requireActive(tenantId, ModuleType.STAY);
    }
    if (!"open".equals(account.getStatus())) {
      throw new IllegalStateException("AccountClosed");
    }

    List<UUID> paidInvoiceIds = new ArrayList<>();
    BigDecimal totalAmount = BigDecimal.ZERO;
    for (Invoice invoice : invoices.findAllByTenantIdAndAccountId(tenantId, accountId)) {
      if (!"final".equals(invoice.getStatus())) continue;
      BigDecimal due = amountDue(invoice);
      if (due.signum() <= 0) continue;
      payment(invoice.getId(), mode, due, null);
      paidInvoiceIds.add(invoice.getId());
      totalAmount = totalAmount.add(due);
    }
    return new SettlementResult(List.copyOf(paidInvoiceIds), totalAmount);
  }

  @Transactional
  public void postInvoiceToAccount(UUID accountId, UUID invoiceId) {
    Account a =
        accounts
            .findByTenantIdAndIdForUpdate(tenant.tenantId(), accountId)
            .orElseThrow(() -> new NoSuchElementException("Account not found"));
    if ("stay".equals(a.getOpenedByModule())) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    } else if ("pos".equals(a.getOpenedByModule())) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
    }
    if (!"open".equals(a.getStatus())) throw new IllegalStateException("AccountClosed");
    Invoice invoice =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), invoiceId)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    requireInvoiceModule(invoice.getSourceModule());
    invoice.postToAccount(accountId);
  }

  @Transactional
  public InvoiceResult createInvoice(NewInvoice request) {
    UUID t = tenant.tenantId();
    requireInvoiceModule(request.sourceModule());
    if (request.accountId() != null) {
      Account a = account(request.accountId());
      if (!"open".equals(a.getStatus())) throw new IllegalStateException("AccountClosed");
    }
    LocalDate now = LocalDate.now();
    String year =
        now.getMonthValue() >= 4
            ? now.getYear() + "-" + (now.getYear() + 1)
            : (now.getYear() - 1) + "-" + now.getYear();
    String number = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    BigDecimal subtotal = BigDecimal.ZERO, taxTotal = BigDecimal.ZERO;
    List<CalculatedLine> calc = new ArrayList<>();
    for (LineInput line : request.lineItems()) {
      BigDecimal qty = line.quantity().setScale(2, RoundingMode.UNNECESSARY),
          price = line.unitPrice().setScale(2, RoundingMode.HALF_UP),
          base = qty.multiply(price).setScale(2, RoundingMode.HALF_UP),
          rate = BigDecimal.ZERO;
      if (line.taxRuleId() != null) {
        TaxRule tr =
            taxes
                .findByTenantIdAndId(t, line.taxRuleId())
                .orElseThrow(() -> new NoSuchElementException("Tax rule not found"));
        if (tr.getEffectiveFrom().isAfter(now)
            || (tr.getEffectiveTo() != null && tr.getEffectiveTo().isBefore(now)))
          throw new IllegalArgumentException("Tax rule is not effective");
        rate = tr.getRatePercent();
      }
      BigDecimal tx = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(base);
      taxTotal = taxTotal.add(tx);
      calc.add(new CalculatedLine(line, base, tx));
    }
    Invoice invoice =
        invoices.save(
            new Invoice(
                t,
                number,
                year,
                request.accountId(),
                request.customerId(),
                request.sourceModule(),
                subtotal,
                taxTotal,
                subtotal.add(taxTotal)));
    for (CalculatedLine c : calc)
      lines.save(
          new InvoiceLineItem(
              t,
              invoice.getId(),
              c.input.description(),
              c.input.quantity(),
              c.input.unitPrice(),
              c.input.taxRuleId(),
              c.tax,
              c.base.add(c.tax)));
    return new InvoiceResult(invoice, subtotal, taxTotal, subtotal.add(taxTotal));
  }

  private boolean taxModuleEnabled(String appliesTo) {
    return switch (appliesTo) {
      case "room" -> moduleEntitlements.isActive(tenant.tenantId(), ModuleType.STAY);
      case "restaurant" ->
          moduleEntitlements.isActive(tenant.tenantId(), ModuleType.RESTAURANT);
      default -> true;
    };
  }

  private void requireInvoiceModule(String sourceModule) {
    if (sourceModule == null)
      throw new IllegalArgumentException("Invoice source module is required");
    switch (sourceModule) {
      case "stay" -> moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
      case "restaurant" ->
          moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
      case "expense" -> moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.EXPENSE);
      case "other" -> {}
      default -> throw new IllegalArgumentException("Invalid invoice source module");
    }
  }

  private void requireTaxModule(String appliesTo) {
    if ("room".equals(appliesTo))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    else if ("restaurant".equals(appliesTo))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
  }

  public Map<String, Object> invoice(UUID id) {
    Invoice i =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    var invoicePayments = payments.findAllByTenantIdAndInvoiceIdOrderByPaidAt(tenant.tenantId(), id);
    var invoiceCredits = creditNotes.findAllByTenantIdAndOriginalInvoiceIdOrderByCreatedAtDesc(tenant.tenantId(), id);
    BigDecimal paidTotal = invoicePayments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal creditTotal = invoiceCredits.stream().map(CreditNote::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return Map.of(
        "invoice", i,
        "line_items", lines.findAllByTenantIdAndInvoiceId(tenant.tenantId(), id),
        "payments", invoicePayments,
        "credit_notes", invoiceCredits,
        "paid_total", paidTotal,
        "credit_total", creditTotal,
        "amount_due", i.getTotalAmount().subtract(paidTotal).subtract(creditTotal).max(BigDecimal.ZERO));
  }

  public Invoice getInvoice(UUID id) {
    return invoices
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
  }

  public BigDecimal amountPaid(UUID invoiceId) {
    return payments.sumPaid(tenant.tenantId(), invoiceId);
  }

  public BigDecimal amountDue(Invoice invoice) {
    BigDecimal credits =
        creditNotes.totalForInvoice(tenant.tenantId(), invoice.getId());
    return invoice.getTotalAmount().subtract(credits).subtract(amountPaid(invoice.getId()))
        .max(BigDecimal.ZERO);
  }

  @Transactional
  public Invoice lock(UUID id) {
    Invoice i =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    i.lock();
    return i;
  }

  @Transactional
  public PaymentResult payment(UUID invoiceId, String mode, BigDecimal amount, String reference) {
    Invoice i =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), invoiceId)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    if (!"final".equals(i.getStatus()))
      throw new IllegalStateException("Invoice must be final before payment");
    if (!Set.of("cash", "card", "upi", "account").contains(mode))
      throw new IllegalArgumentException("Invalid payment mode");
    BigDecimal due = amountDue(i);
    boolean over = amount.compareTo(due) > 0;
    Payment p = payments.save(new Payment(tenant.tenantId(), invoiceId, mode, amount, reference));
    return new PaymentResult(p, over);
  }

  public List<Payment> payments(UUID invoiceId) {
    invoices
        .findByTenantIdAndId(tenant.tenantId(), invoiceId)
        .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    return payments.findAllByTenantIdAndInvoiceIdOrderByPaidAt(tenant.tenantId(), invoiceId);
  }

  @Transactional
  public void closeAccount(UUID id) {
    Account account =
        accounts
            .findByTenantIdAndIdForUpdate(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Account not found"));
    if ("stay".equals(account.getOpenedByModule())) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    } else if ("pos".equals(account.getOpenedByModule())) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.RESTAURANT);
    }
    List<Invoice> list = invoices.findAllByTenantIdAndAccountId(tenant.tenantId(), id);
    for (Invoice i : list)
      if ((!"final".equals(i.getStatus()) && !"credited".equals(i.getStatus()))
          || amountDue(i).signum() > 0)
        throw new IllegalStateException("HasUnsettledCharges");
    account.close();
  }

  public record LineInput(
      String description, BigDecimal quantity, BigDecimal unitPrice, UUID taxRuleId) {}

  public record NewInvoice(
      UUID accountId, UUID customerId, String sourceModule, List<LineInput> lineItems) {}

  private record CalculatedLine(LineInput input, BigDecimal base, BigDecimal tax) {}

  public record InvoiceResult(
      Invoice invoice, BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount) {}

  public record PaymentResult(Payment payment, boolean overpayment) {}

  public record SettlementResult(List<UUID> paidInvoiceIds, BigDecimal totalAmount) {}
}
