package com.InnovaServe.expense.repository;import com.InnovaServe.expense.entity.ExpenseCategory;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory,UUID>{List<ExpenseCategory> findAllByTenantIdOrderByName(UUID t);Optional<ExpenseCategory> findByTenantIdAndId(UUID t,UUID id);}
