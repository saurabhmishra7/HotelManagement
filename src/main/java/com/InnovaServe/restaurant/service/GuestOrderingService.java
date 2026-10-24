package com.InnovaServe.restaurant.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.core.entity.QRCode;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.QRCodeRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.NotificationService;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import com.InnovaServe.stay.service.RoomServiceRequestService;
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
  private final RestaurantBillRepository bills;
  private final OrderItemRepository orderItems;
  private final KotBatchRepository kotBatches;
  private final StayLookupPort stayLookup;
  private final ModuleEntitlementService entitlements;
  private final NotificationService notifications;
  private final RestaurantBillBreakdownService billBreakdown;
  private final RoomServiceRequestService roomServiceRequests;
  private final GuestQrSessionService guestQrSessions;
  private final RestaurantStationService stations;

  public GuestOrderingService(
      QRCodeRepository qrCodes,
      TenantRepository tenants,
      MenuCategoryRepository categories,
      MenuItemRepository menuItems,
      DiningTableRepository tables,
      RestaurantOrderRepository orders,
      RestaurantBillRepository bills,
      OrderItemRepository orderItems,
      KotBatchRepository kotBatches,
      StayLookupPort stayLookup,
      ModuleEntitlementService entitlements,
      NotificationService notifications,
      RestaurantBillBreakdownService billBreakdown,
      RoomServiceRequestService roomServiceRequests,
      GuestQrSessionService guestQrSessions,
      RestaurantStationService stations) {
    this.qrCodes = qrCodes;
    this.tenants = tenants;
    this.categories = categories;
    this.menuItems = menuItems;
    this.tables = tables;
    this.orders = orders;
    this.bills = bills;
    this.orderItems = orderItems;
    this.kotBatches = kotBatches;
    this.stayLookup = stayLookup;
    this.entitlements = entitlements;
    this.notifications = notifications;
    this.billBreakdown = billBreakdown;
    this.roomServiceRequests = roomServiceRequests;
    this.guestQrSessions = guestQrSessions;
    this.stations = stations;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> resolveMenu(String token) {
    QRCode qrCode = resolve(token);
    UUID tenantId = qrCode.getTenantId();
    Tenant tenant =
        tenants
            .findById(tenantId)
            .orElseThrow(() -> new NoSuchElementException("Property not found"));
    boolean restaurantAvailable = entitlements.isActive(tenantId, ModuleType.RESTAURANT);
    boolean barAvailable = restaurantAvailable && stations.barAvailable(tenantId);
    DiningTable targetTable = "dining_table".equals(qrCode.getTargetType())
        ? tables.findByTenantIdAndId(tenantId, qrCode.getTargetId())
            .orElseThrow(() -> new NoSuchElementException("QR table not found")) : null;
    String serviceStation = targetTable == null ? "kitchen" : targetTable.getStation();
    List<MenuItem> availableItems = restaurantAvailable
        ? menuItems.findAllByTenantIdOrderByName(tenantId).stream()
            .filter(MenuItem::isActive)
            .filter(item -> StationOrderingPolicy.allows(serviceStation, item.getStation()))
            .filter(item -> barAvailable || "kitchen".equals(item.getStation())).toList()
        : List.of();
    Set<UUID> categoryIds =
        availableItems.stream().map(MenuItem::getCategoryId).collect(java.util.stream.Collectors.toSet());
    List<Map<String, Object>> availableCategories =
        (restaurantAvailable ? categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId) : List.<MenuCategory>of()).stream()
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
                        "name", item.getDisplayName(),
                        "price", item.getPrice(),
                        "station", item.getStation(),
                        "veg_flag", item.isVegFlag(),
                        "food_type", item.getFoodType()))
            .toList();

    Map<String, Object> target = new LinkedHashMap<>();
    target.put("type", qrCode.getTargetType());
    target.put("id", qrCode.getTargetId());
    if (targetTable != null) {
      target.put("table_number", targetTable.getTableNumber());
      target.put("station", serviceStation);
    } else {
      stayLookup.findRoomNumber(tenantId, qrCode.getTargetId())
          .ifPresent(number -> target.put("room_number", number));
    }
    Map<String, Object> tenantInfo = new LinkedHashMap<>();
    tenantInfo.put("name", tenant.getName());
    tenantInfo.put("address", tenant.getAddress());
    tenantInfo.put("palette", tenant.getUiPalette());
    if (tenant.getLogoVersion() != null) {
      tenantInfo.put("logo_version", tenant.getLogoVersion());
      tenantInfo.put("logo_url", "/api/v1/guest/qr/" + token + "/logo?v=" + tenant.getLogoVersion());
    }
    return Map.of(
        "tenant", tenantInfo,
        "target", target,
        "restaurant_order_available", restaurantAvailable,
        "menu_categories", availableCategories,
        "menu_items", menu);
  }

  @Transactional
  public Map<String, Object> submitOrder(String token, UUID guestSessionId, List<GuestItem> requestedItems) {
    QRCode qrCode = resolve(token);
    UUID tenantId = qrCode.getTenantId();
    entitlements.requireActive(tenantId, ModuleType.RESTAURANT);
    if (requestedItems == null || requestedItems.isEmpty() || requestedItems.size() > 100)
      throw new IllegalArgumentException("Provide between 1 and 100 order items");
    List<RestaurantOrder> sessionOrders = orders.findAllByTenantIdAndGuestSessionIdOrderByCreatedAtAsc(tenantId, guestSessionId);
    boolean billingStarted = sessionOrders.stream().anyMatch(order ->
        ("dining_table".equals(qrCode.getTargetType()) || "open".equals(order.getStatus()))
            && (order.getBillRequestedAt() != null || "billed".equals(order.getStatus())
                || bills.findByTenantIdAndOrderId(tenantId, order.getId()).isPresent()));
    if (billingStarted) throw new IllegalStateException("Billing has started for this visit; no more items can be added");

    UUID tableId = null;
    UUID stayId = null;
    String serviceStation = "kitchen";
    String orderType;
    String location;
    if ("dining_table".equals(qrCode.getTargetType())) {
      DiningTable table =
          tables
              .lockByTenantIdAndId(tenantId, qrCode.getTargetId())
              .orElseThrow(() -> new NoSuchElementException("QR table not found"));
      if ("dirty".equals(table.getStatus())
          || ("billed".equals(table.getStatus())
              && !guestQrSessions.latestTableBillPaid(tenantId, table.getId())))
        throw new IllegalStateException("This table is not accepting new orders");
      tableId = table.getId();
      stations.requireActive(tenantId, table.getStation());
      serviceStation = table.getStation();
      location = "Table " + table.getTableNumber();
      if (Set.of("free", "billed").contains(table.getStatus())) table.setStatus("occupied");
      orderType = "dine_in";
    } else {
      entitlements.requireActive(tenantId, ModuleType.STAY);
      StayLookupPort.StaySummary stay =
          stayLookup
              .findActiveByRoomId(tenantId, qrCode.getTargetId())
              .orElseThrow(() -> new IllegalStateException("No active stay is associated with this room"));
      stayId = stay.id();
      location = "Room " + stayLookup.findRoomNumber(tenantId, qrCode.getTargetId()).orElseThrow();
      orderType = "room_service";
    }

    UUID targetTableId = tableId;
    UUID targetStayId = stayId;
    RestaurantOrder existingOrder = sessionOrders.stream()
        .filter(candidate -> "open".equals(candidate.getStatus()))
        .filter(candidate -> Objects.equals(candidate.getTableId(), targetTableId)
            && Objects.equals(candidate.getStayId(), targetStayId))
        .findFirst().orElse(null);
    RestaurantOrder order;
    if (existingOrder == null) {
      order = new RestaurantOrder(tenantId, orderType, tableId, stayId, "qr_guest", true, null);
      order.assignServiceStation(serviceStation);
      order.attachGuestSession(guestSessionId);
      orders.save(order);
    } else {
      order = orders.lockByTenantIdAndId(tenantId, existingOrder.getId())
          .orElseThrow(() -> new NoSuchElementException("Order not found"));
      if (!"open".equals(order.getStatus()) || order.getBillRequestedAt() != null
          || bills.findByTenantIdAndOrderId(tenantId, order.getId()).isPresent())
        throw new IllegalStateException("Billing has started for this visit; no more items can be added");
      order.awaitGuestConfirmation();
    }
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
      stations.requireActive(tenantId, item.getStation());
      StationOrderingPolicy.requireAllowed(order.getServiceStation(), item.getStation());
      orderItems.save(new OrderItem(tenantId, order.getId(), item.getId(),
          requested.quantity(), requested.notes(), "pending_confirmation"));
    }
    notifications.qrOrderNeedsConfirmation(tenantId, order.getId(), location, stayId != null);
    return orderView(order, orderItems.findAllByTenantIdAndOrderId(tenantId, order.getId()));
  }

  @Transactional
  public RoomServiceRequestService.View submitServiceRequest(String token, UUID guestSessionId,
      String serviceType, String notes) {
    QRCode qrCode = resolve(token);
    if (!"room".equals(qrCode.getTargetType()))
      throw new IllegalArgumentException("Room service requests require a room QR code");
    return roomServiceRequests.submit(qrCode.getTenantId(), qrCode.getTargetId(),
        guestSessionId, serviceType, notes);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> order(String token, UUID guestSessionId, UUID orderId) {
    QRCode qrCode = resolve(token);
    entitlements.requireActive(qrCode.getTenantId(), ModuleType.RESTAURANT);
    RestaurantOrder order =
        orders
            .findByTenantIdAndId(qrCode.getTenantId(), orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found"));
    requireQrOwnsOrder(qrCode, order);
    requireSessionOwnsOrder(order, guestSessionId);
    return orderView(
        order, orderItems.findAllByTenantIdAndOrderId(qrCode.getTenantId(), order.getId()));
  }

  @Transactional
  public Map<String, Object> requestBill(String token, UUID guestSessionId, UUID orderId) {
    QRCode qrCode = resolve(token);
    entitlements.requireActive(qrCode.getTenantId(), ModuleType.RESTAURANT);
    RestaurantOrder order =
        orders
            .lockByTenantIdAndId(qrCode.getTenantId(), orderId)
            .orElseThrow(() -> new NoSuchElementException("Order not found"));
    requireQrOwnsOrder(qrCode, order);
    requireSessionOwnsOrder(order, guestSessionId);
    if (!"confirmed".equals(order.getConfirmationStatus()))
      throw new IllegalStateException("Wait for the waiter to confirm this order before requesting the bill");
    List<OrderItem> items = orderItems.findAllByTenantIdAndOrderId(qrCode.getTenantId(), orderId);
    List<OrderItem> billableItems = items.stream()
        .filter(item -> !"cancelled".equals(item.getStatus())).toList();
    if (billableItems.isEmpty() || billableItems.stream().anyMatch(item -> !"served".equals(item.getStatus())))
      throw new IllegalStateException("Wait until every item has been served before requesting the bill");
    if (order.getBillRequestedAt() == null) {
      order.requestBill();
      String location = "dining_table".equals(qrCode.getTargetType())
          ? tables.findByTenantIdAndId(qrCode.getTenantId(), qrCode.getTargetId())
              .map(table -> "Table " + table.getTableNumber()).orElse("the table")
          : stayLookup.findRoomNumber(qrCode.getTenantId(), qrCode.getTargetId())
              .map(number -> "Room " + number).orElse("the room");
      notifications.restaurantBillRequested(qrCode.getTenantId(), orderId, location);
    } else {
      order.requestBill();
    }
    RestaurantBill bill = bills.findByTenantIdAndOrderId(qrCode.getTenantId(), orderId).orElse(null);
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("order_id", order.getId());
    response.put("status", "bill_requested");
    response.put("bill_details", billBreakdown.forGuest(qrCode.getTenantId(), items, bill));
    return response;
  }

  private QRCode resolve(String token) {
    QRCode qrCode =
        qrCodes
            .findByTokenAndActiveTrue(token)
            .orElseThrow(() -> new NoSuchElementException("QR code not found"));
    UUID tenantId = qrCode.getTenantId();
    if ("dining_table".equals(qrCode.getTargetType()))
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

  @Transactional(readOnly = true)
  public List<Map<String, Object>> ordersForSession(UUID tenantId, UUID guestSessionId,
      boolean isDiningTable) {
    List<RestaurantOrder> sessionOrders = orders.findAllByTenantIdAndGuestSessionIdOrderByCreatedAtAsc(
        tenantId, guestSessionId);
    if (sessionOrders.isEmpty()) return List.of();
    List<UUID> ids = sessionOrders.stream().map(RestaurantOrder::getId).toList();
    Map<UUID, List<OrderItem>> itemsByOrder = orderItems.findAllByTenantIdAndOrderIdIn(tenantId, ids)
        .stream().collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderId));
    Map<UUID, RestaurantBill> billsByOrder = bills.findAllByTenantIdAndOrderIdIn(tenantId, ids)
        .stream().collect(java.util.stream.Collectors.toMap(RestaurantBill::getOrderId, bill -> bill));
    return sessionOrders.stream()
        .map(order -> {
          List<OrderItem> orderedItems = itemsByOrder.getOrDefault(order.getId(), List.of());
          Map<String, Object> view = new LinkedHashMap<>(orderView(order, orderedItems));
          RestaurantBill bill = billsByOrder.get(order.getId());
          view.put("bill_created", bill != null);
          List<OrderItem> billableItems = orderedItems.stream()
              .filter(item -> !"cancelled".equals(item.getStatus())).toList();
          boolean mealServed = !billableItems.isEmpty()
              && billableItems.stream().allMatch(item -> "served".equals(item.getStatus()));
          if (bill != null || order.getBillRequestedAt() != null
              || (!isDiningTable && mealServed))
            view.put("bill_details", billBreakdown.forGuest(tenantId, orderedItems, bill));
          return view;
        })
        .toList();
  }

  private void requireSessionOwnsOrder(RestaurantOrder order, UUID guestSessionId) {
    if (!guestSessionId.equals(order.getGuestSessionId()))
      throw new NoSuchElementException("Order not found");
  }

  @Transactional(readOnly = true)
  public byte[] logo(String token) {
    QRCode qrCode = resolve(token);
    byte[] logo = tenants.findById(qrCode.getTenantId())
        .orElseThrow(() -> new NoSuchElementException("Property not found")).getLogo();
    if (logo == null) throw new NoSuchElementException("Property logo not set");
    return logo;
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
