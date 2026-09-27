package com.InnovaServe.api;

import com.InnovaServe.expense.entity.*;
import com.InnovaServe.expense.service.ExpenseService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExpenseController {
  private final ExpenseService service;

  public ExpenseController(ExpenseService service) {
    this.service = service;
  }

  @GetMapping("/expense-categories")
  public List<ExpenseCategory> categories() {
    return service.categories();
  }

  @PostMapping("/expense-categories")
  public ExpenseCategory category(@RequestBody NameRequest r) {
    return service.addCategory(r.name());
  }

  @PostMapping("/expenses")
  public Map<String, Object> expense(@RequestBody ExpenseRequest r) {
    Expense e =
        service.addExpense(
            new ExpenseService.NewExpense(
                r.categoryId(),
                r.department(),
                r.vendorName(),
                r.amount(),
                r.paymentMode(),
                r.receiptFileRef(),
                r.expenseDate()));
    return Map.of("id", e.getId(), "approval_status", e.getApprovalStatus());
  }

  @GetMapping("/expenses")
  public List<Expense> expenses(
      @RequestParam(required = false) String department,
      @RequestParam(name = "category_id", required = false) UUID category,
      @RequestParam(name = "approval_status", required = false) String status,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.list(department, category, status, from, to);
  }

  @PostMapping("/expenses/{id}/approve")
  public Map<String, Object> approve(@PathVariable UUID id, @RequestBody ApproveRequest r) {
    return Map.of("approval_status", service.approve(id, r.pin()).getApprovalStatus());
  }

  @GetMapping("/expenses/report")
  public List<Map<String, Object>> report(
      @RequestParam(name = "group_by") String group,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to) {
    return service.report(group, from, to);
  }

  @PostMapping("/petty-cash/open")
  public PettyCashLedger open(@RequestBody PettyOpen r) {
    return service.openLedger(r.shiftDate(), r.openingFloat());
  }

  @PostMapping("/petty-cash/{id}/top-up")
  public PettyCashLedger topup(@PathVariable UUID id, @RequestBody AmountRequest r) {
    return service.topup(id, r.amount());
  }

  @PostMapping("/petty-cash/{id}/reconcile")
  public PettyCashLedger reconcile(@PathVariable UUID id, @RequestBody AmountRequest r) {
    return service.reconcile(id, r.amount());
  }

  @PostMapping("/recurring-expenses")
  public RecurringExpense recurring(@RequestBody RecurringRequest r) {
    return service.createRecurring(
        r.categoryId(), r.description(), r.amount(), r.frequency(), r.nextDueDate());
  }

  @GetMapping("/recurring-expenses/due")
  public List<RecurringExpense> due(@RequestParam(required = false) LocalDate date) {
    return service.due(date == null ? LocalDate.now() : date);
  }

  @PostMapping("/recurring-expenses/{id}/mark-paid")
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
