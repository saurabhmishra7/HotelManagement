package com.InnovaServe.expense.controller;

import com.InnovaServe.expense.entity.*;
import com.InnovaServe.expense.service.ExpenseService;
import com.InnovaServe.expense.service.ExpenseReceiptStorageService;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.RequiresModule;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiresModule(ModuleType.EXPENSE)
public class ExpenseController {
  private final ExpenseService service;
  private final ExpenseReceiptStorageService receiptStorage;
  private final com.InnovaServe.core.service.TenantContext tenant;

  public ExpenseController(
      ExpenseService service,
      ExpenseReceiptStorageService receiptStorage,
      com.InnovaServe.core.service.TenantContext tenant) {
    this.service = service;
    this.receiptStorage = receiptStorage;
    this.tenant = tenant;
  }

  @PostMapping(value = "/expenses/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('PERM_EXPENSE_CREATE')")
  public Map<String, String> uploadReceipt(@RequestPart("file") MultipartFile file) {
    return Map.of("receipt_file_ref", receiptStorage.store(tenant.tenantId(), file));
  }

  @GetMapping("/expenses/receipts/{receiptId}")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_READ')")
  public ResponseEntity<byte[]> receipt(@PathVariable UUID receiptId) {
    var file = receiptStorage.load(tenant.tenantId(), receiptId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mediaType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(file.content());
  }

  @GetMapping("/expense-categories")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_READ')")
  public List<ExpenseCategory> categories() {
    return service.categories();
  }

  @PostMapping("/expense-categories")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_CREATE')")
  public ExpenseCategory category(@RequestBody NameRequest r) {
    return service.addCategory(r.name());
  }

  @PostMapping("/expenses")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_CREATE')")
  public Map<String, Object> expense(@RequestBody ExpenseRequest r) {
    Expense e =
        service.addExpense(
            new ExpenseService.NewExpense(
                r.categoryId(),
                r.department(),
                r.description(),
                r.vendorName(),
                r.amount(),
                r.paymentMode(),
                r.receiptFileRef(),
                r.expenseDate()));
    return Map.of("id", e.getId(), "approval_status", e.getApprovalStatus());
  }

  @GetMapping("/expenses")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_READ')")
  public List<Expense> expenses(
      @RequestParam(required = false) String department,
      @RequestParam(name = "category_id", required = false) UUID category,
      @RequestParam(name = "approval_status", required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.list(department, category, status, from, to);
  }

  @PostMapping("/expenses/{id}/approve")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_APPROVE')")
  public Map<String, Object> approve(@PathVariable UUID id, @RequestBody ApproveRequest r) {
    return Map.of("approval_status", service.approve(id, r.pin()).getApprovalStatus());
  }

  @GetMapping("/expenses/report")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_REPORT')")
  public List<Map<String, Object>> report(
      @RequestParam(name = "group_by") String group,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.report(group, from, to);
  }

  @PostMapping("/petty-cash/open")
  @PreAuthorize("hasAuthority('PERM_PETTY_CASH_MANAGE')")
  public PettyCashLedger open(@RequestBody PettyOpen r) {
    return service.openLedger(r.shiftDate(), r.openingFloat());
  }

  @GetMapping("/petty-cash")
  @PreAuthorize("hasAuthority('PERM_PETTY_CASH_MANAGE')")
  public List<PettyCashLedger> ledgers() {
    return service.ledgers();
  }

  @PostMapping("/petty-cash/{id}/top-up")
  @PreAuthorize("hasAuthority('PERM_PETTY_CASH_MANAGE')")
  public PettyCashLedger topup(@PathVariable UUID id, @RequestBody AmountRequest r) {
    return service.topup(id, r.amount());
  }

  @PostMapping("/petty-cash/{id}/reconcile")
  @PreAuthorize("hasAuthority('PERM_PETTY_CASH_MANAGE')")
  public PettyCashLedger reconcile(@PathVariable UUID id, @RequestBody AmountRequest r) {
    return service.reconcile(id, r.amount());
  }

  @PostMapping("/recurring-expenses")
  @PreAuthorize("hasAuthority('PERM_RECURRING_EXPENSE_MANAGE')")
  public RecurringExpense recurring(@RequestBody RecurringRequest r) {
    return service.createRecurring(
        r.categoryId(), r.description(), r.amount(), r.frequency(), r.nextDueDate());
  }

  @GetMapping("/recurring-expenses")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_READ')")
  public List<RecurringExpense> recurringExpenses() {
    return service.recurringExpenses();
  }

  @GetMapping("/recurring-expenses/due")
  @PreAuthorize("hasAuthority('PERM_EXPENSE_READ')")
  public List<RecurringExpense> due(@RequestParam(required = false) LocalDate date) {
    return service.due(date == null ? LocalDate.now() : date);
  }

  @PostMapping("/recurring-expenses/{id}/mark-paid")
  @PreAuthorize("hasAuthority('PERM_RECURRING_EXPENSE_MANAGE')")
  public Map<String, Object> markPaid(@PathVariable UUID id, @RequestBody MarkPaid r) {
    return Map.of(
        "next_due_date",
        service
            .markPaid(id, r.expenseDate(), Boolean.TRUE.equals(r.createExpense()))
            .getNextDueDate());
  }

  public record NameRequest(String name) {}

  public record ApproveRequest(String pin) {}

  public record ExpenseRequest(
      @JsonProperty("category_id") UUID categoryId,
      String department,
      String description,
      @JsonProperty("vendor_name") String vendorName,
      BigDecimal amount,
      @JsonProperty("payment_mode") String paymentMode,
      @JsonProperty("receipt_file_ref") String receiptFileRef,
      @JsonProperty("expense_date") LocalDate expenseDate) {}

  public record PettyOpen(
      @JsonProperty("shift_date") LocalDate shiftDate,
      @JsonProperty("opening_float") BigDecimal openingFloat) {}

  public record AmountRequest(
      BigDecimal amount, @JsonProperty("closing_balance_actual") BigDecimal closingBalanceActual) {}

  public record RecurringRequest(
      @JsonProperty("category_id") UUID categoryId,
      String description,
      BigDecimal amount,
      String frequency,
      @JsonProperty("next_due_date") LocalDate nextDueDate) {}

  public record MarkPaid(
      @JsonProperty("expense_date") LocalDate expenseDate,
      @JsonProperty("create_expense") Boolean createExpense) {}
}
