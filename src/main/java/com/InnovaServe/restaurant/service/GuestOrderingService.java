package com.InnovaServe.restaurant.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.core.entity.QRCode;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.QRCodeRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuestOrderingService {
  private final QRCodeRepository qrCodes;
  private final TenantRepository tenants;
  private final MenuCategoryRepository categories;
  private final MenuItemRepository menuItems;
  private final DiningTableRepository tables;
  private final RestaurantOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final KotBatchRepository kotBatches;
  private final StayLookupPort stayLookup;
  private final ModuleEntitlementService entitlements;

  public GuestOrderingService(
      QRCodeRepository qrCodes,
      TenantRepository tenants,
      MenuCategoryRepository categories,
      MenuItemRepository menuItems,
      DiningTableRepository tables,
      RestaurantOrderRepository orders,
      OrderItemRepository orderItems,
      KotBatchRepository kotBatches,
      StayLookupPort stayLookup,
      ModuleEntitlementService entitlements) {
    this.qrCodes = qrCodes;
    this.tenants = tenants;
    this.categories = categories;
    this.menuItems = menuItems;
    this.tables = tables;
    this.orders = orders;
    this.orderItems = orderItems;
    this.kotBatches = kotBatches;
    this.stayLookup = stayLookup;
    this.entitlements = entitlements;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> resolveMenu(String token) {
    QRCode qrCode = resolve(token);
    UUID tenantId = qrCode.getTenantId();
    Tenant tenant =
        tenants
            .findById(tenantId)
            .orElseThrow(() -> new NoSuchElementException("Property not found"));
    List<MenuItem> availableItems =
        menuItems.findAllByTenantIdOrderByName(tenantId).stream()
            .filter(MenuItem::isActive)
            .toList();
    Set<UUID> categoryIds =
        availableItems.stream().map(MenuItem::getCategoryId).collect(java.util.stream.Collectors.toSet());
    List<Map<String, Object>> availableCategories =
        categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
            .filter(category -> categoryIds.contains(category.getId()))
            .map(
                category ->
                    Map.<String, Object>of(
                        "id", category.getId(),
                        "name", category.getName(),
                        "sort_order", category.getSortOrder()))
            .toList();
    List<Map<String, Object>> menu =
        availableItems.stream()
            .map(
                item ->
                    Map.<String, Object>of(
                        "id", item.getId(),
                        "category_id", item.getCategoryId(),
                        "name", item.getName(),
                        "price", item.getPrice(),
                        "station", item.getStation(),
                        "veg_flag", item.isVegFlag()))
            .toList();

    Map<String, Object> target = new LinkedHashMap<>();
    target.put("type", qrCode.getTargetType());
    target.put("id", qrCode.getTargetId());
    if ("dining_table".equals(qrCode.getTargetType())) {
      tables
          .findByTenantIdAndId(tenantId, qrCode.getTargetId())
          .ifPresent(table -> target.put("table_number", table.getTableNumber()));
    }
    return Map.of(
        "tenant", Map.of("id", tenant.getId(), "name", tenant.getName()),
        "target", target,
        "menu_categories", availableCategories,
        "menu_items", menu);
  }

  @Transactional
  public Map<String, Object> submitOrder(String token, List<GuestItem> requestedItems) {
    QRCode qrCode = resolve(token);
    UUID tenantId = qrCode.getTenantId();
    if (requestedItems == null || requestedItems.isEmpty() || requestedItems.size() > 100)
      throw new IllegalArgumentException("Provide between 1 and 100 order items");

    UUID tableId = null;
    UUID stayId = null;
    String orderType;
    if ("dining_table".equals(qrCode.getTargetType())) {
      DiningTable table =
          tables
              .findByTenantIdAndId(tenantId, qrCode.getTargetId())
              .orElseThrow(() -> new NoSuchElementException("QR table not found"));
      if ("billed".equals(table.getStatus()))
        throw new IllegalStateException("This table is not accepting new orders");
      tableId = table.getId();
      if ("free".equals(table.getStatus())) table.setStatus("occupied");
      orderType = "dine_in";
    } else {
      entitlements.requireActive(tenantId, ModuleType.STAY);
      StayLookupPort.StaySummary stay =
          stayLookup
              .findActiveByRoomId(tenantId, qrCode.getTargetId())
              .orElseThrow(() -> new IllegalStateException("No active stay is associated with this room"));
      stayId = stay.id();
      orderType = "room_service";
    }

    RestaurantOrder order =
        orders.save(new RestaurantOrder(tenantId, orderType, tableId, stayId, "qr_guest", true, null));
    List<OrderItem> createdItems = new ArrayList<>();
    for (GuestItem requested : requestedItems) {
      if (requested == null || requested.menuItemId() == null || requested.quantity() < 1)
        throw new IllegalArgumentException("Each item requires a menu_item_id and positive quantity");
      if (requested.notes() != null && requested.notes().length() > 200)
        throw new IllegalArgumentException("Item notes must be at most 200 characters");
      MenuItem item =
          menuItems
              .findByTenantIdAndId(tenantId, requested.menuItemId())
              .filter(MenuItem::isActive)
              .orElseThrow(() -> new NoSuchElementException("Active menu item not found"));
      createdItems.add(
          orderItems.save(
              new OrderItem(
                  tenantId,
                  order.getId(),
                  item.getId(),
                  requested.quantity(),
                  requested.notes(),
                  "pending_confirmation")));
    }
    return orderView(order, createdItems);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> order(String token, UUID orderId) {
    QRCode qrCode = resolve(token);
    RestaurantOrder order =
        orders
            .findByTenantIdAndId(qrCode.getTenantId(), orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found"));
    requireQrOwnsOrder(qrCode, order);
    return orderView(
        order, orderItems.findAllByTenantIdAndOrderId(qrCode.getTenantId(), order.getId()));
  }

  @Transactional
  public Map<String, Object> requestBill(String token, UUID orderId) {
    QRCode qrCode = resolve(token);
    RestaurantOrder order =
        orders
            .findByTenantIdAndId(qrCode.getTenantId(), orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found"));
    requireQrOwnsOrder(qrCode, order);
    order.requestBill();
    return Map.of("order_id", order.getId(), "status", "bill_requested");
  }

  private QRCode resolve(String token) {
    QRCode qrCode =
        qrCodes
            .findByTokenAndActiveTrue(token)
            .orElseThrow(() -> new NoSuchElementException("QR code not found"));
    UUID tenantId = qrCode.getTenantId();
    entitlements.requireActive(tenantId, ModuleType.RESTAURANT);
    if ("room".equals(qrCode.getTargetType()))
      entitlements.requireActive(tenantId, ModuleType.STAY);
    if (!Set.of("room", "dining_table").contains(qrCode.getTargetType()))
      throw new NoSuchElementException("QR code target not found");
    return qrCode;
  }

  private void requireQrOwnsOrder(QRCode qrCode, RestaurantOrder order) {
    if (!"qr_guest".equals(order.getOrderSource()))
      throw new NoSuchElementException("Order not found");
    if ("dining_table".equals(qrCode.getTargetType())) {
      if (!qrCode.getTargetId().equals(order.getTableId()))
        throw new NoSuchElementException("Order not found");
      return;
    }
    if (order.getStayId() == null
        || !"room_service".equals(order.getOrderType())
        || stayLookup
            .findById(qrCode.getTenantId(), order.getStayId())
            .filter(stay -> qrCode.getTargetId().equals(stay.roomId()))
            .isEmpty()) throw new NoSuchElementException("Order not found");
  }

  private Map<String, Object> orderView(RestaurantOrder order, List<OrderItem> items) {
    return Map.of(
        "order_id", order.getId(),
        "order_type", order.getOrderType(),
        "status", order.getStatus(),
        "confirmation_status", order.getConfirmationStatus(),
        "bill_requested_at", order.getBillRequestedAt() == null ? "" : order.getBillRequestedAt(),
        "items", items);
  }

  public record GuestItem(UUID menuItemId, short quantity, String notes) {}
}
