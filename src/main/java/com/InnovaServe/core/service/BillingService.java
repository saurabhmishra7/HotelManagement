package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
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

  public BillingService(
      TenantContext t,
      InvoiceRepository i,
      InvoiceLineItemRepository l,
      PaymentRepository p,
      AccountRepository a,
      TaxRuleRepository tax,
      CreditNoteRepository creditNotes) {
    tenant = t;
    invoices = i;
    lines = l;
    payments = p;
    accounts = a;
    taxes = tax;
    this.creditNotes = creditNotes;
  }

  public List<TaxRule> taxes(String applies) {
    LocalDate d = LocalDate.now();
    return taxes.findAllByTenantId(tenant.tenantId()).stream()
        .filter(
            x ->
                (applies == null || applies.equals(x.getAppliesTo()))
                    && !x.getEffectiveFrom().isAfter(d)
                    && (x.getEffectiveTo() == null || !x.getEffectiveTo().isBefore(d)))
        .toList();
  }

  @Transactional
  public TaxRule addTax(
      String name, String applies, BigDecimal rate, boolean itc, LocalDate effective) {
    if (!Set.of("room", "restaurant", "other").contains(applies))
      throw new IllegalArgumentException("Invalid tax category");
    return taxes.save(new TaxRule(tenant.tenantId(), name, applies, rate, itc, effective));
  }

  @Transactional
  public TaxRule supersede(UUID id, LocalDate to) {
    TaxRule rule =
        taxes
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Tax rule not found"));
    rule.supersede(to);
    return rule;
  }

  @Transactional
  public Account openAccount(String module, String type, UUID entity) {
    return accounts.save(new Account(tenant.tenantId(), module, type, entity));
  }

  public Account account(UUID id) {
    return accounts
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Account not found"));
  }

  public List<Invoice> invoicesForAccount(UUID id) {
    return invoices.findAllByTenantIdAndAccountId(tenant.tenantId(), id);
  }

  @Transactional
  public void postInvoiceToAccount(UUID accountId, UUID invoiceId) {
    Account a = account(accountId);
    if (!"open".equals(a.getStatus())) throw new IllegalStateException("AccountClosed");
    Invoice invoice =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), invoiceId)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    invoice.postToAccount(accountId);
  }

  @Transactional
  public InvoiceResult createInvoice(NewInvoice request) {
    UUID t = tenant.tenantId();
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

  public Map<String, Object> invoice(UUID id) {
    Invoice i =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Invoice not found"));
    return Map.of(
        "invoice", i, "line_items", lines.findAllByTenantIdAndInvoiceId(tenant.tenantId(), id));
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
    Account account = account(id);
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
}
