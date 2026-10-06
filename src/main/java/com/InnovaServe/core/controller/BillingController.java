package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.service.BillingService;
import com.InnovaServe.core.service.CoreReferenceService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BillingController {
  private final BillingService service;
  private final CoreReferenceService references;

  public BillingController(BillingService service, CoreReferenceService references) {
    this.service = service;
    this.references = references;
  }

  @PostMapping("/qr-codes")
  @PreAuthorize("hasAuthority('PERM_QR_CODE_MANAGE')")
  public QRCode createQRCode(@RequestBody QRCodeRequest r) {
    return references.createQRCode(r.targetType(), r.targetId());
  }

  @PostMapping("/credit-notes")
  @PreAuthorize("hasAuthority('PERM_CREDIT_NOTE_CREATE')")
  public CreditNote createCreditNote(@RequestBody CreditNoteRequest r) {
    return references.createCreditNote(r.originalInvoiceId(), r.reason(), r.amount());
  }

  @GetMapping("/credit-notes")
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public List<CreditNote> creditNotes() {
    return references.creditNotes();
  }

  @GetMapping("/qr-codes")
  @PreAuthorize("hasAuthority('PERM_QR_CODE_MANAGE')")
  public List<QRCode> qrCodes() {
    return references.qrCodes();
  }

  @GetMapping("/tax-rules")
  @PreAuthorize("hasAuthority('PERM_TAX_READ')")
  public List<TaxRule> taxRules(
      @RequestParam(name = "applies_to", required = false) String applies) {
    return service.taxes(applies);
  }

  @PostMapping("/tax-rules")
  @PreAuthorize("hasAuthority('PERM_TAX_MANAGE')")
  public TaxRule addTax(@RequestBody TaxRequest r) {
    return service.addTax(
        r.name(),
        r.appliesTo(),
        r.ratePercent(),
        Boolean.TRUE.equals(r.itcEligible()),
        r.effectiveFrom());
  }

  @PostMapping("/tax-rules/{id}/supersede")
  @PreAuthorize("hasAuthority('PERM_TAX_MANAGE')")
  public TaxRule supersede(@PathVariable UUID id, @RequestBody SupersedeRequest r) {
    return service.supersede(id, r.effectiveTo());
  }

  @PostMapping("/accounts")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGE')")
  public Map<String, Object> openAccount(@RequestBody AccountRequest r) {
    Account a = service.openAccount(r.openedByModule(), r.linkedEntityType(), r.linkedEntityId());
    return Map.of("account_id", a.getId(), "status", a.getStatus());
  }

  @GetMapping("/accounts/{id}")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_READ')")
  public Account account(@PathVariable UUID id) {
    return service.account(id);
  }

  @GetMapping("/accounts/{id}/invoices")
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public List<?> accountInvoices(@PathVariable UUID id) {
    return service.invoicesForAccount(id);
  }

  @PostMapping("/accounts/{id}/settle-now")
  @PreAuthorize("hasAuthority('PERM_BILL_SETTLE')")
  public BillingService.SettlementResult settleAccountNow(
      @PathVariable UUID id, @RequestBody SettleAccountRequest request) {
    return service.settleOutstandingAccount(id, request.mode());
  }

  @PostMapping("/accounts/{id}/charges")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGE')")
  public Map<String, Boolean> charge(@PathVariable UUID id, @RequestBody InvoiceRef r) {
    service.postInvoiceToAccount(id, r.invoiceId());
    return Map.of("success", true);
  }

  @PostMapping("/accounts/{id}/close")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGE')")
  public Map<String, Boolean> closeAccount(@PathVariable UUID id) {
    service.closeAccount(id);
    return Map.of("success", true);
  }

  @PostMapping("/invoices")
  @PreAuthorize("hasAuthority('PERM_BILLING_CREATE')")
  public Map<String, Object> createInvoice(@RequestBody InvoiceRequest r) {
    var result =
        service.createInvoice(
            new BillingService.NewInvoice(
                r.accountId(),
                r.customerId(),
                r.sourceModule(),
                r.lineItems().stream()
                    .map(
                        x ->
                            new BillingService.LineInput(
                                x.description(), x.quantity(), x.unitPrice(), x.taxRuleId()))
                    .toList()));
    return Map.of(
        "invoice_id",
        result.invoice().getId(),
        "subtotal",
        result.subtotal().toPlainString(),
        "tax_amount",
        result.taxAmount().toPlainString(),
        "total_amount",
        result.totalAmount().toPlainString(),
        "financial_year",
        result.invoice().getFinancialYear(),
        "invoice_number",
        result.invoice().getInvoiceNumber());
  }

  @GetMapping("/invoices/{id}")
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public Map<String, Object> invoice(@PathVariable UUID id) {
    return service.invoice(id);
  }

  @PostMapping("/invoices/{id}/lock")
  @PreAuthorize("hasAuthority('PERM_BILLING_CREATE')")
  public Map<String, Object> lock(@PathVariable UUID id) {
    Invoice i = service.lock(id);
    return Map.of("status", i.getStatus(), "locked_at", i.getLockedAt());
  }

  @PostMapping("/payments")
  @PreAuthorize("hasAuthority('PERM_PAYMENT_RECORD')")
  public Map<String, Object> payment(@RequestBody PaymentRequest r) {
    var result = service.payment(r.invoiceId(), r.mode(), r.amount(), r.reference());
    return Map.of("payment_id", result.payment().getId(), "overpayment", result.overpayment());
  }

  @GetMapping("/invoices/{id}/payments")
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public List<Payment> payments(@PathVariable UUID id) {
    return service.payments(id);
  }

  public record TaxRequest(
      String name,
      @JsonProperty("applies_to") String appliesTo,
      @JsonProperty("rate_percent") BigDecimal ratePercent,
      @JsonProperty("itc_eligible") Boolean itcEligible,
      @JsonProperty("effective_from") LocalDate effectiveFrom) {}

  public record SupersedeRequest(@JsonProperty("effective_to") LocalDate effectiveTo) {}

  public record AccountRequest(
      @JsonProperty("opened_by_module") String openedByModule,
      @JsonProperty("linked_entity_type") String linkedEntityType,
      @JsonProperty("linked_entity_id") UUID linkedEntityId) {}

  public record InvoiceRef(@JsonProperty("invoice_id") UUID invoiceId) {}

  public record InvoiceRequest(
      @JsonProperty("account_id") UUID accountId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("source_module") String sourceModule,
      List<LineRequest> lineItems) {}

  public record LineRequest(
      String description,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      @JsonProperty("tax_rule_id") UUID taxRuleId) {}

  public record PaymentRequest(
      @JsonProperty("invoice_id") UUID invoiceId,
      String mode,
      BigDecimal amount,
      String reference) {}

  public record SettleAccountRequest(String mode) {}

  public record QRCodeRequest(
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") UUID targetId) {}

  public record CreditNoteRequest(
      @JsonProperty("original_invoice_id") UUID originalInvoiceId,
      String reason,
      BigDecimal amount) {}
}
