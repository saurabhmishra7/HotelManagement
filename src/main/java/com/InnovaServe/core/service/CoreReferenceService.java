package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.restaurant.repository.DiningTableRepository;
import com.InnovaServe.stay.repository.RoomRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoreReferenceService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final TenantContext tenant;
  private final QRCodeRepository qrCodes;
  private final CreditNoteRepository creditNotes;
  private final InvoiceRepository invoices;
  private final RoomRepository rooms;
  private final DiningTableRepository tables;
  private final AuditService audit;

  public CoreReferenceService(
      TenantContext tenant,
      QRCodeRepository qrCodes,
      CreditNoteRepository creditNotes,
      InvoiceRepository invoices,
      RoomRepository rooms,
      DiningTableRepository tables,
      AuditService audit) {
    this.tenant = tenant;
    this.qrCodes = qrCodes;
    this.creditNotes = creditNotes;
    this.invoices = invoices;
    this.rooms = rooms;
    this.tables = tables;
    this.audit = audit;
  }

  @Transactional
  public QRCode createQRCode(String targetType, UUID targetId) {
    if (!Set.of("room", "dining_table").contains(targetType))
      throw new IllegalArgumentException("target_type must be room or dining_table");
    boolean exists =
        "room".equals(targetType)
            ? rooms.findByTenantIdAndId(tenant.tenantId(), targetId).isPresent()
            : tables.findByTenantIdAndId(tenant.tenantId(), targetId).isPresent();
    if (!exists) throw new java.util.NoSuchElementException("QR target not found");
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    QRCode saved = qrCodes.save(
        new QRCode(
            tenant.tenantId(),
            targetType,
            targetId,
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)));
    audit.record(
        "create", "qr_code", saved.getId(), null,
        Map.of("target_type", targetType, "target_id", targetId.toString()));
    return saved;
  }

  @Transactional(readOnly = true)
  public List<QRCode> qrCodes() {
    return qrCodes.findAllByTenantIdOrderByCreatedAtDesc(tenant.tenantId());
  }

  @Transactional(readOnly = true)
  public List<CreditNote> creditNotes() {
    return creditNotes.findAllByTenantIdOrderByCreatedAtDesc(tenant.tenantId());
  }

  @Transactional
  public CreditNote createCreditNote(UUID invoiceId, String reason, BigDecimal amount) {
    Invoice invoice =
        invoices
            .findByTenantIdAndId(tenant.tenantId(), invoiceId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Invoice not found"));
    if (!"final".equals(invoice.getStatus()))
      throw new IllegalStateException("Credit notes require a finalized invoice");
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reason is required");
    if (amount == null || amount.signum() <= 0 || amount.compareTo(invoice.getTotalAmount()) > 0)
      throw new IllegalArgumentException("Credit amount must be positive and no more than invoice total");
    BigDecimal existingCredits = creditNotes.totalForInvoice(tenant.tenantId(), invoiceId);
    if (existingCredits.add(amount).compareTo(invoice.getTotalAmount()) > 0)
      throw new IllegalArgumentException("Total credit notes cannot exceed invoice total");
    CreditNote saved =
        creditNotes.save(
            new CreditNote(tenant.tenantId(), invoiceId, reason.trim(), amount, tenant.userId()));
    if (existingCredits.add(amount).compareTo(invoice.getTotalAmount()) == 0)
      invoice.markCredited();
    audit.record(
        "create", "credit_note", saved.getId(), null,
        Map.of("invoice_id", invoiceId.toString(), "amount", amount.toPlainString()));
    return saved;
  }
}
