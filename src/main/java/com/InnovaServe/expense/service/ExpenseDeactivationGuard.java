package com.InnovaServe.expense.service;

import com.InnovaServe.contracts.ModuleDeactivationGuard;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.expense.repository.ExpenseRepository;
import com.InnovaServe.expense.repository.PettyCashRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExpenseDeactivationGuard implements ModuleDeactivationGuard {
  private final ExpenseRepository expenses;
  private final PettyCashRepository pettyCash;

  public ExpenseDeactivationGuard(
      ExpenseRepository expenses, PettyCashRepository pettyCash) {
    this.expenses = expenses;
    this.pettyCash = pettyCash;
  }

  @Override
  public ModuleType module() {
    return ModuleType.EXPENSE;
  }

  @Override
  public Optional<String> blockingReason(UUID tenantId) {
    if (expenses.existsByTenantIdAndApprovalStatus(tenantId, "pending"))
      return Optional.of("Approve or resolve pending expenses before disabling Expense");
    if (pettyCash.existsByTenantIdAndReconciledAtIsNull(tenantId))
      return Optional.of("Reconcile all petty cash ledgers before disabling Expense");
    return Optional.empty();
  }
}
