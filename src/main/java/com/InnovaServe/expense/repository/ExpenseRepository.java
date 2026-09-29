package com.InnovaServe.expense.repository;

import com.InnovaServe.expense.entity.Expense;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {
  @Query(
      "select e from Expense e where e.tenantId=:tenant and (:department is null or"
          + " e.department=:department) and (:category is null or e.categoryId=:category) and"
          + " (:status is null or e.approvalStatus=:status) and (:fromDate is null or"
          + " e.expenseDate>=:fromDate) and (:toDate is null or e.expenseDate<=:toDate) order by"
          + " e.expenseDate desc")
  List<Expense> search(
      @Param("tenant") UUID t,
      @Param("department") String dept,
      @Param("category") UUID category,
      @Param("status") String status,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to);

  Optional<Expense> findByTenantIdAndId(UUID t, UUID id);

  boolean existsByTenantIdAndApprovalStatus(UUID tenantId, String approvalStatus);

  @Query(
      "select e.department,sum(e.amount) from Expense e where e.tenantId=:t and (:from is null or"
          + " e.expenseDate>=:from) and (:to is null or e.expenseDate<=:to) group by e.department")
  List<Object[]> totalByDepartment(
      @Param("t") UUID t, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      "select coalesce(e.vendorName,'(unknown)'),sum(e.amount) from Expense e where e.tenantId=:t"
          + " and (:from is null or e.expenseDate>=:from) and (:to is null or e.expenseDate<=:to)"
          + " group by e.vendorName")
  List<Object[]> totalByVendor(
      @Param("t") UUID t, @Param("from") LocalDate from, @Param("to") LocalDate to);

  @Query(
      "select e.categoryId,sum(e.amount) from Expense e where e.tenantId=:t and (:from is null or"
          + " e.expenseDate>=:from) and (:to is null or e.expenseDate<=:to) group by e.categoryId")
  List<Object[]> totalByCategory(
      @Param("t") UUID t, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
