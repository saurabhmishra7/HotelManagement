package com.InnovaServe.core.repository;

import com.InnovaServe.core.entity.CreditNote;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {
  List<CreditNote> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<CreditNote> findAllByTenantIdAndOriginalInvoiceIdOrderByCreatedAtDesc(
      UUID tenantId, UUID invoiceId);

  @Query(
      "select coalesce(sum(note.amount), 0) from CreditNote note"
          + " where note.tenantId = :tenantId and note.originalInvoiceId = :invoiceId")
  BigDecimal totalForInvoice(
      @Param("tenantId") UUID tenantId, @Param("invoiceId") UUID invoiceId);
}
