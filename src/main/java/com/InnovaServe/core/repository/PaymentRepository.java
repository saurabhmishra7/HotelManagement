package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.Payment;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  List<Payment> findAllByTenantIdAndInvoiceIdOrderByPaidAt(UUID t, UUID invoice);

  @Query(
      "select coalesce(sum(p.amount),0) from Payment p where p.tenantId=:t and"
          + " p.invoiceId=:invoice")
  BigDecimal sumPaid(@Param("t") UUID t, @Param("invoice") UUID invoice);
}
