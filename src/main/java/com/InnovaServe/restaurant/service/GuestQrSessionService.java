package com.InnovaServe.restaurant.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.core.entity.QRCode;
import com.InnovaServe.core.event.InvoiceFullyPaidEvent;
import com.InnovaServe.core.repository.QRCodeRepository;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.BillingService;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.restaurant.entity.GuestQrSession;
import com.InnovaServe.restaurant.entity.RestaurantOrder;
import com.InnovaServe.restaurant.repository.DiningTableRepository;
import com.InnovaServe.restaurant.repository.GuestQrSessionRepository;
import com.InnovaServe.restaurant.repository.RestaurantBillRepository;
import com.InnovaServe.restaurant.repository.RestaurantOrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestQrSessionService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final QRCodeRepository qrCodes;
  private final GuestQrSessionRepository sessions;
  private final DiningTableRepository tables;
  private final StayLookupPort stays;
  private final RestaurantOrderRepository orders;
  private final RestaurantBillRepository bills;
  private final BillingService billing;
  private final ModuleEntitlementService entitlements;

  public GuestQrSessionService(QRCodeRepository qrCodes, GuestQrSessionRepository sessions,
      DiningTableRepository tables, StayLookupPort stays, RestaurantOrderRepository orders,
      RestaurantBillRepository bills, BillingService billing, ModuleEntitlementService entitlements) {
    this.qrCodes = qrCodes;
    this.sessions = sessions;
    this.tables = tables;
    this.stays = stays;
    this.orders = orders;
    this.bills = bills;
    this.billing = billing;
    this.entitlements = entitlements;
  }

  @Transactional
  public Started start(String qrToken, String existingToken) {
    QRCode qr = qr(qrToken);
    if (existingToken != null && !existingToken.isBlank()) {
      GuestQrSession existing = refreshPaidStatus(session(qr, existingToken));
      return new Started(existing, existingToken);
    }
    if ("dining_table".equals(qr.getTargetType())) {
      entitlements.requireActive(qr.getTenantId(), ModuleType.RESTAURANT);
      String status = tables.findByTenantIdAndId(qr.getTenantId(), qr.getTargetId())
          .orElseThrow(() -> new NoSuchElementException("Table not found")).getStatus();
      if ("dirty".equals(status)
          || ("billed".equals(status) && !latestTableBillPaid(qr.getTenantId(), qr.getTargetId())))
        throw new IllegalStateException("This table is finishing its previous visit. Please ask the team for help.");
    } else if ("room".equals(qr.getTargetType())) {
      entitlements.requireActive(qr.getTenantId(), ModuleType.STAY);
      stays.findActiveByRoomId(qr.getTenantId(), qr.getTargetId())
          .orElseThrow(() -> new IllegalStateException("No active stay is associated with this room"));
    } else {
      throw new NoSuchElementException("QR code target not found");
    }
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    GuestQrSession created = sessions.save(new GuestQrSession(qr.getTenantId(), qr.getId(),
        qr.getTargetType(), qr.getTargetId(), hash(token)));
    return new Started(created, token);
  }

  @Transactional
  public GuestQrSession get(String qrToken, String sessionToken) {
    return refreshPaidStatus(session(qr(qrToken), sessionToken));
  }

  private GuestQrSession refreshPaidStatus(GuestQrSession guestSession) {
    if ("active".equals(guestSession.getStatus())
        && "dining_table".equals(guestSession.getTargetType())) {
      List<RestaurantOrder> visitOrders = orders.findAllByTenantIdAndGuestSessionIdOrderByCreatedAtAsc(
          guestSession.getTenantId(), guestSession.getId());
      if (visitPaid(visitOrders)) guestSession.close("paid");
    }
    return guestSession;
  }

  @Transactional
  public GuestQrSession requireActive(String qrToken, String sessionToken) {
    GuestQrSession session = get(qrToken, sessionToken);
    if (!"active".equals(session.getStatus()))
      throw new IllegalStateException("This guest session has ended. Scan the QR code for a new visit.");
    return session;
  }

  @Transactional
  public void closeForRoom(UUID tenantId, UUID roomId, String reason) {
    sessions.findAllByTenantIdAndTargetTypeAndTargetIdAndStatus(tenantId, "room", roomId, "active")
        .forEach(session -> session.close(reason));
  }

  @Transactional
  public void closeForTable(UUID tenantId, UUID tableId) {
    sessions.findAllByTenantIdAndTargetTypeAndTargetIdAndStatus(tenantId, "dining_table", tableId, "active")
        .forEach(session -> session.close("table_closed"));
  }

  @EventListener
  @Transactional
  public void onInvoiceFullyPaid(InvoiceFullyPaidEvent event) {
    bills.findByTenantIdAndInvoiceId(event.tenantId(), event.invoiceId()).ifPresent(bill ->
        orders.findByTenantIdAndId(event.tenantId(), bill.getOrderId()).ifPresent(order -> {
          if (order.getTableId() != null) {
            List<GuestQrSession> active = sessions.findAllByTenantIdAndTargetTypeAndTargetIdAndStatus(
                event.tenantId(), "dining_table", order.getTableId(), "active").stream()
                .filter(session -> !"account".equals(bill.getSettlementMode())
                    || order.getCreatedAt() == null || session.getCreatedAt() == null
                    || !session.getCreatedAt().isAfter(order.getCreatedAt()))
                .toList();
            active.forEach(session -> {
              List<RestaurantOrder> visitOrders = orders.findAllByTenantIdAndGuestSessionIdOrderByCreatedAtAsc(
                  event.tenantId(), session.getId());
              if (visitPaid(visitOrders)) session.close("paid");
            });
          }
        }));
  }

  private boolean isPaid(RestaurantOrder order) {
    if ("cancelled".equals(order.getStatus())) return true;
    return bills.findByTenantIdAndOrderId(order.getTenantId(), order.getId())
        .map(bill -> billing.amountDue(billing.getInvoice(bill.getInvoiceId())).signum() <= 0)
        .orElse(false);
  }

  private boolean visitPaid(List<RestaurantOrder> visitOrders) {
    List<RestaurantOrder> billable = visitOrders.stream()
        .filter(order -> !"cancelled".equals(order.getStatus()))
        .toList();
    return !billable.isEmpty() && billable.stream().allMatch(this::isPaid);
  }

  @Transactional(readOnly = true)
  public boolean latestTableBillPaid(UUID tenantId, UUID tableId) {
    return orders.findFirstByTenantIdAndTableIdAndStatusOrderByCreatedAtDesc(
            tenantId, tableId, "billed")
        .flatMap(order -> bills.findByTenantIdAndOrderId(tenantId, order.getId()))
        .map(bill -> billing.amountDue(billing.getInvoice(bill.getInvoiceId())).signum() <= 0)
        .orElse(false);
  }

  private GuestQrSession session(QRCode qr, String token) {
    if (token == null || token.isBlank() || token.length() > 128)
      throw new IllegalArgumentException("Guest session token is required");
    GuestQrSession session = sessions.findByTokenHash(hash(token))
        .orElseThrow(() -> new NoSuchElementException("Guest session not found"));
    if (!session.getTenantId().equals(qr.getTenantId()) || !qr.getId().equals(session.getQrCodeId()))
      throw new NoSuchElementException("Guest session not found");
    return session;
  }

  private QRCode qr(String token) {
    return qrCodes.findByTokenAndActiveTrue(token)
        .orElseThrow(() -> new NoSuchElementException("QR code not found"));
  }

  private String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record Started(GuestQrSession session, String token) {}
}
