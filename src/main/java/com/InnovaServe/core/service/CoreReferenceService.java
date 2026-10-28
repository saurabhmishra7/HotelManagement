package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.restaurant.repository.DiningTableRepository;
import com.InnovaServe.restaurant.entity.DiningTable;
import com.InnovaServe.restaurant.repository.GuestQrSessionRepository;
import com.InnovaServe.stay.entity.Room;
import com.InnovaServe.stay.repository.RoomRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoreReferenceService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final TenantContext tenant;
  private final QRCodeRepository qrCodes;
  private final TenantRepository tenants;
  private final CreditNoteRepository creditNotes;
  private final InvoiceRepository invoices;
  private final RoomRepository rooms;
  private final DiningTableRepository tables;
  private final GuestQrSessionRepository guestSessions;
  private final AuditService audit;
  private final ModuleEntitlementService moduleEntitlements;

  public CoreReferenceService(
      TenantContext tenant,
      QRCodeRepository qrCodes,
      TenantRepository tenants,
      CreditNoteRepository creditNotes,
      InvoiceRepository invoices,
      RoomRepository rooms,
      DiningTableRepository tables,
      GuestQrSessionRepository guestSessions,
      AuditService audit,
      ModuleEntitlementService moduleEntitlements) {
    this.tenant = tenant;
    this.qrCodes = qrCodes;
    this.tenants = tenants;
    this.creditNotes = creditNotes;
    this.invoices = invoices;
    this.rooms = rooms;
    this.tables = tables;
    this.guestSessions = guestSessions;
    this.audit = audit;
    this.moduleEntitlements = moduleEntitlements;
  }

  @Transactional
  public QRCode createQRCode(String targetType, UUID targetId) {
    if (!Set.of("room", "dining_table").contains(targetType))
      throw new IllegalArgumentException("target_type must be room or dining_table");
    // Serialize QR creation for this property so two requests cannot both pass the active-code check.
    tenants.lockById(tenant.tenantId())
        .orElseThrow(() -> new java.util.NoSuchElementException("Property not found"));
    moduleEntitlements.requireActive(
        tenant.tenantId(), "room".equals(targetType) ? ModuleType.STAY : ModuleType.RESTAURANT);
    boolean exists =
        "room".equals(targetType)
            ? rooms.findByTenantIdAndId(tenant.tenantId(), targetId).isPresent()
            : tables.findByTenantIdAndId(tenant.tenantId(), targetId).isPresent();
    if (!exists) throw new java.util.NoSuchElementException("QR target not found");
    if (qrCodes.existsByTenantIdAndTargetTypeAndTargetIdAndActiveTrue(tenant.tenantId(), targetType, targetId))
      throw new IllegalStateException("An active QR code already exists for this room or table. Open its card to view or print it.");
    if (!qrCodes.findAllByTenantIdAndTargetTypeAndTargetIdAndActiveFalse(
        tenant.tenantId(), targetType, targetId).isEmpty())
      throw new IllegalStateException("An inactive QR code already exists for this room or table. Activate it from its card.");
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
  public List<QRCodeSummary> qrCodes() {
    UUID tenantId = tenant.tenantId();
    boolean roomsEnabled = moduleEntitlements.isActive(tenantId, ModuleType.STAY);
    boolean tablesEnabled = moduleEntitlements.isActive(tenantId, ModuleType.RESTAURANT);
    Map<UUID, String> roomNumbers = roomsEnabled
        ? rooms.findAllByTenantIdOrderByRoomNumber(tenantId).stream()
            .collect(Collectors.toMap(Room::getId, Room::getRoomNumber))
        : Map.of();
    Map<UUID, String> tableNumbers = tablesEnabled
        ? tables.findAllByTenantIdOrderByTableNumber(tenantId).stream()
            .collect(Collectors.toMap(DiningTable::getId, DiningTable::getTableNumber))
        : Map.of();
    return qrCodes.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
        .filter(
            code ->
                switch (code.getTargetType()) {
                  case "room" -> roomsEnabled;
                  case "dining_table" -> tablesEnabled;
                  default -> false;
                })
        .map(code -> new QRCodeSummary(
            code.getId(), code.getTenantId(), code.getTargetType(), code.getTargetId(),
            code.getToken(), code.isActive(), code.getCreatedAt(), code.getRotatedAt(),
            "room".equals(code.getTargetType())
                ? roomNumbers.get(code.getTargetId())
                : tableNumbers.get(code.getTargetId())))
        .toList();
  }

  public record QRCodeSummary(
      UUID id, UUID tenantId, String targetType, UUID targetId, String token,
      boolean active, LocalDateTime createdAt, LocalDateTime rotatedAt, String targetNumber) {}

  @Transactional
  public QRCode deactivateQRCode(UUID id) {
    UUID tenantId = tenant.tenantId();
    tenants.lockById(tenantId)
        .orElseThrow(() -> new java.util.NoSuchElementException("Property not found"));
    QRCode code = qrCodes.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new java.util.NoSuchElementException("QR code not found"));
    moduleEntitlements.requireActive(tenantId,
        "room".equals(code.getTargetType()) ? ModuleType.STAY : ModuleType.RESTAURANT);
    if (!code.isActive()) return code;
    code.deactivate();
    guestSessions.findAllByTenantIdAndQrCodeIdAndStatus(tenantId, id, "active")
        .forEach(session -> session.close("qr_deactivated"));
    audit.record("deactivate", "qr_code", code.getId(), null,
        Map.of("target_type", code.getTargetType(), "target_id", code.getTargetId().toString()));
    return code;
  }

  @Transactional
  public QRCode activateQRCode(UUID id) {
    UUID tenantId = tenant.tenantId();
    tenants.lockById(tenantId)
        .orElseThrow(() -> new java.util.NoSuchElementException("Property not found"));
    QRCode code = qrCodes.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new java.util.NoSuchElementException("QR code not found"));
    moduleEntitlements.requireActive(tenantId,
        "room".equals(code.getTargetType()) ? ModuleType.STAY : ModuleType.RESTAURANT);
    if (code.isActive()) return code;
    if (qrCodes.existsByTenantIdAndTargetTypeAndTargetIdAndActiveTrue(
        tenantId, code.getTargetType(), code.getTargetId()))
      throw new IllegalStateException("Another QR code is already active for this room or table");
    code.activate();
    audit.record("activate", "qr_code", code.getId(), null,
        Map.of("target_type", code.getTargetType(), "target_id", code.getTargetId().toString()));
    return code;
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
