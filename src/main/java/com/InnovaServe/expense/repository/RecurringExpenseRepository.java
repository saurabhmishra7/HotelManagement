package com.InnovaServe.expense.repository;

import com.InnovaServe.expense.entity.RecurringExpense;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
  List<RecurringExpense> findAllByTenantIdAndNextDueDateLessThanEqualOrderByNextDueDate(
      UUID t, LocalDate date);

  Optional<RecurringExpense> findByTenantIdAndId(UUID t, UUID id);
}
