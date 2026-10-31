package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.service.BillingService;
import com.InnovaServe.core.service.CoreReferenceService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
  public QRCode createQRCode(@jakarta.validation.Valid @RequestBody QRCodeRequest r) {
    return references.createQRCode(r.targetType(), r.targetId());
  }

  @PatchMapping("/qr-codes/{id}/deactivate")
  @PreAuthorize("hasAuthority('PERM_QR_CODE_MANAGE')")
  public QRCode deactivateQRCode(@PathVariable UUID id) {
    return references.deactivateQRCode(id);
  }

  @PatchMapping("/qr-codes/{id}/activate")
  @PreAuthorize("hasAuthority('PERM_QR_CODE_MANAGE')")
  public QRCode activateQRCode(@PathVariable UUID id) {
    return references.activateQRCode(id);
  }

  @PostMapping("/credit-notes")
  @PreAuthorize("hasAuthority('PERM_CREDIT_NOTE_CREATE')")
  public CreditNote createCreditNote(@jakarta.validation.Valid @RequestBody CreditNoteRequest r) {
    return references.createCreditNote(r.originalInvoiceId(), r.reason(), r.amount());
  }

  @GetMapping("/credit-notes")
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public List<CreditNote> creditNotes() {
    return references.creditNotes();
  }

  @GetMapping("/qr-codes")
  @PreAuthorize("hasAuthority('PERM_QR_CODE_MANAGE')")
  public List<CoreReferenceService.QRCodeSummary> qrCodes() {
    return references.qrCodes();
  }

  @GetMapping("/tax-rules")
  @PreAuthorize("hasAuthority('PERM_TAX_READ')")
  public List<TaxRule> taxRules(
      @RequestParam(name = "applies_to", required = false) String applies,
      @RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
    return service.taxes(applies, includeInactive);
  }

  @PostMapping("/tax-rules")
  @PreAuthorize("hasAuthority('PERM_TAX_MANAGE')")
  public TaxRule addTax(@jakarta.validation.Valid @RequestBody TaxRequest r) {
    return service.addTax(
        r.name(),
        r.appliesTo(),
        r.taxCategory(),
        r.ratePercent(),
        Boolean.TRUE.equals(r.itcEligible()),
        r.effectiveFrom());
  }

  @PostMapping("/tax-rules/{id}/supersede")
  @PreAuthorize("hasAuthority('PERM_TAX_MANAGE')")
  public TaxRule supersede(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody SupersedeRequest r) {
    return service.supersede(id, r.effectiveTo());
  }

  @PostMapping("/accounts")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGE')")
  public Map<String, Object> openAccount(@jakarta.validation.Valid @RequestBody AccountRequest r) {
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
      @PathVariable UUID id, @jakarta.validation.Valid @RequestBody SettleAccountRequest request) {
    return service.settleOutstandingAccount(id, request.mode());
  }

  @PostMapping("/accounts/{id}/charges")
  @PreAuthorize("hasAuthority('PERM_ACCOUNT_MANAGE')")
  public Map<String, Boolean> charge(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody InvoiceRef r) {
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
  public Map<String, Object> createInvoice(@jakarta.validation.Valid @RequestBody InvoiceRequest r) {
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
  public Map<String, Object> invoice(@PathVariable String id) {
    return service.invoiceByReference(id);
  }

  @PostMapping("/invoices/{id}/lock")
  @PreAuthorize("hasAuthority('PERM_BILLING_CREATE')")
  public Map<String, Object> lock(@PathVariable UUID id) {
    Invoice i = service.lock(id);
    return Map.of("status", i.getStatus(), "locked_at", i.getLockedAt());
  }

  @PostMapping("/payments")
  @PreAuthorize("hasAuthority('PERM_PAYMENT_RECORD')")
  public Map<String, Object> payment(@jakarta.validation.Valid @RequestBody PaymentRequest r) {
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
      @JsonProperty("tax_category") String taxCategory,
      @JsonProperty("rate_percent") BigDecimal ratePercent,
      @JsonProperty("itc_eligible") Boolean itcEligible,
      @JsonProperty("effective_from") LocalDate effectiveFrom) {}

  public record SupersedeRequest(@JsonProperty("effective_to") LocalDate effectiveTo) {}

  public record AccountRequest(
      @JsonProperty("opened_by_module") String openedByModule,
      @JsonProperty("linked_entity_type") String linkedEntityType,
      @JsonProperty("linked_entity_id") UUID linkedEntityId) {}

  public record InvoiceRef(@NotNull @JsonProperty("invoice_id") UUID invoiceId) {}

  public record InvoiceRequest(
      @JsonProperty("account_id") UUID accountId,
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("source_module") String sourceModule,
      @NotEmpty @Size(max=500) List<@Valid LineRequest> lineItems) {}

  public record LineRequest(
      @NotBlank @Size(max=200) String description,
      @NotNull @DecimalMin("0.01") BigDecimal quantity,
      @NotNull @DecimalMin("0") @JsonProperty("unit_price") BigDecimal unitPrice,
      @JsonProperty("tax_rule_id") UUID taxRuleId) {}

  public record PaymentRequest(
      @NotNull @JsonProperty("invoice_id") UUID invoiceId,
      @NotBlank @Pattern(regexp="cash|card|upi|account") String mode,
      @NotNull @DecimalMin("0.01") @Digits(integer=8, fraction=2) BigDecimal amount,
      @Size(max=100) String reference) {}

  public record SettleAccountRequest(String mode) {}

  public record QRCodeRequest(
      @JsonProperty("target_type") String targetType,
      @JsonProperty("target_id") UUID targetId) {}

  public record CreditNoteRequest(
      @NotNull @JsonProperty("original_invoice_id") UUID originalInvoiceId,
      @NotBlank @Size(max=2000) String reason,
      @NotNull @DecimalMin("0.01") @Digits(integer=8, fraction=2) BigDecimal amount) {}
}
