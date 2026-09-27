package com.InnovaServe.expense.repository;

import com.InnovaServe.expense.entity.ExpenseCategory;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, UUID> {
  List<ExpenseCategory> findAllByTenantIdOrderByName(UUID t);

  Optional<ExpenseCategory> findByTenantIdAndId(UUID t, UUID id);
}
