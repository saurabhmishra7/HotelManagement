package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantService {
  private final TenantContext tenant;
  private final MenuCategoryRepository categories;
  private final MenuItemRepository items;
  private final DiningTableRepository tables;
  private final RestaurantOrderRepository orders;
  private final OrderItemRepository orderItems;
  private final KotBatchRepository batches;
  private final RestaurantBillRepository bills;
  private final BillingService billing;
  private final ModuleEntitlementService moduleEntitlements;
  private final StayLookupPort stayLookup;

  public RestaurantService(
      TenantContext t,
      MenuCategoryRepository c,
      MenuItemRepository i,
      DiningTableRepository tables,
      RestaurantOrderRepository o,
      OrderItemRepository oi,
      KotBatchRepository b,
      RestaurantBillRepository bills,
      BillingService billing,
      ModuleEntitlementService moduleEntitlements,
      StayLookupPort stayLookup) {
    tenant = t;
    categories = c;
    items = i;
    this.tables = tables;
    orders = o;
    orderItems = oi;
    batches = b;
    this.bills = bills;
    this.billing = billing;
    this.moduleEntitlements = moduleEntitlements;
    this.stayLookup = stayLookup;
  }

  public List<MenuCategory> categories() {
    return categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenant.tenantId());
  }

  @Transactional
  public MenuCategory addCategory(String name, int order) {
    return categories.save(new MenuCategory(tenant.tenantId(), name, (short) order));
  }

  public List<MenuItem> items(UUID category, Boolean active) {
    List<MenuItem> found =
        category == null
            ? items.findAllByTenantIdOrderByName(tenant.tenantId())
            : items.findAllByTenantIdAndCategoryIdOrderByName(tenant.tenantId(), category);
    return active == null ? found : found.stream().filter(i -> i.isActive() == active).toList();
  }

  @Transactional
  public MenuItem addItem(
      UUID cat, String name, java.math.BigDecimal price, UUID tax, String station, boolean veg) {
    categories
        .findByTenantIdAndId(tenant.tenantId(), cat)
        .orElseThrow(() -> new NoSuchElementException("Menu category not found"));
    return items.save(new MenuItem(tenant.tenantId(), cat, name, price, tax, station, veg));
  }

  @Transactional
  public MenuItem updateItem(UUID id, java.math.BigDecimal price, Boolean active) {
    MenuItem item =
        items
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Menu item not found"));
    item.update(price, active);
    return item;
  }

  public List<DiningTable> tables() {
    return tables.findAllByTenantIdOrderByTableNumber(tenant.tenantId());
  }

  @Transactional(readOnly = true)
  public List<Map<String, String>> roomServiceRooms() {
    moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    return stayLookup.findActiveRoomServiceOptions(tenant.tenantId()).stream()
        .map(option -> Map.of(
            "room_number", option.roomNumber(),
            "guest_name", option.guestName()))
        .toList();
  }

  @Transactional
  public DiningTable addTable(String number, String section) {
    if (number == null || number.isBlank() || number.length() > 10)
      throw new IllegalArgumentException("Table number is required and must be at most 10 characters");
    String normalized = number.trim();
    if (tables.existsByTenantIdAndTableNumber(tenant.tenantId(), normalized))
      throw new IllegalArgumentException("Table number already exists");
    return tables.save(new DiningTable(tenant.tenantId(), normalized, section));
  }

  @Transactional
  public RestaurantOrder openOrder(String type, UUID tableId, String roomNumber, UUID legacyStayId) {
    if (!Set.of("dine_in", "room_service", "takeaway").contains(type))
      throw new IllegalArgumentException("Invalid restaurant order type");
    if ("room_service".equals(type))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    UUID stayId = null;
    if ("room_service".equals(type) && (roomNumber == null || roomNumber.isBlank())
        && legacyStayId == null)
      throw new IllegalArgumentException("Choose an occupied room for room service");
    if (roomNumber != null && !roomNumber.isBlank()) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
      String normalizedRoomNumber = roomNumber.trim();
      StayLookupPort.StaySummary stay = stayLookup
          .findActiveByRoomNumber(tenant.tenantId(), normalizedRoomNumber)
          .orElseThrow(() -> new NoSuchElementException(
              "No active guest in Room " + normalizedRoomNumber));
      if (legacyStayId != null && !legacyStayId.equals(stay.id()))
        throw new IllegalArgumentException("The selected room and stay do not match");
      stayId = stay.id();
    } else if (legacyStayId != null) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
      StayLookupPort.StaySummary stay = stayLookup
          .findById(tenant.tenantId(), legacyStayId)
          .orElseThrow(() -> new NoSuchElementException("Stay not found"));
      if (!"active".equals(stay.status()))
        throw new IllegalStateException("Restaurant orders require an active stay");
      stayId = stay.id();
    }
    if (tableId != null) {
      DiningTable table =
          tables
              .findByTenantIdAndId(tenant.tenantId(), tableId)
              .orElseThrow(() -> new NoSuchElementException("Dining table not found"));
      if (!"free".equals(table.getStatus())) throw new IllegalStateException("Table is not free");
      table.setStatus("occupied");
    }
    return orders.save(
        new RestaurantOrder(
            tenant.tenantId(), type, tableId, stayId, "staff", false, tenant.userId()));
  }

  public RestaurantOrder getOrder(UUID id) {
    return orders
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Order not found"));
  }

  public List<RestaurantOrder> orders() {
    return orders.findAllByTenantIdOrderByCreatedAtDesc(tenant.tenantId());
  }

  public List<OrderItem> orderItems(UUID id) {
    getOrder(id);
    return orderItems.findAllByTenantIdAndOrderId(tenant.tenantId(), id);
  }

  @Transactional
  public Map<String, Object> addItems(UUID orderId, List<ItemRequest> request) {
    RestaurantOrder order = getOrder(orderId);
    if (!"open".equals(order.getStatus())) throw new IllegalStateException("Order is not open");
    UUID t = tenant.tenantId();
    Map<String, List<OrderItem>> stationItems = new LinkedHashMap<>();
    for (ItemRequest r : request) {
      MenuItem item =
          items
              .findByTenantIdAndId(t, r.menuItemId())
              .filter(MenuItem::isActive)
              .orElseThrow(() -> new NoSuchElementException("Active menu item not found"));
      OrderItem oi =
          orderItems.save(new OrderItem(t, orderId, item.getId(), r.quantity(), r.notes(), "sent"));
      stationItems.computeIfAbsent(item.getStation(), k -> new ArrayList<>()).add(oi);
    }
    List<KotBatch> savedBatches = new ArrayList<>();
    List<OrderItem> savedItems = new ArrayList<>();
    for (var entry : stationItems.entrySet()) {
      short number =
          (short) (batches.findAllByTenantIdAndOrderIdOrderByBatchNumber(t, orderId).size() + 1);
      KotBatch batch = batches.save(new KotBatch(t, orderId, entry.getKey(), number));
      savedBatches.add(batch);
      for (OrderItem item : entry.getValue()) {
        item.send(batch.getId());
        savedItems.add(item);
      }
    }
    return Map.of("kot_batches", savedBatches, "order_items", savedItems);
  }

  public List<RestaurantOrder> pendingGuestOrders() {
    return orders.findAllByTenantIdAndOrderSourceAndConfirmationStatusOrderByCreatedAt(
        tenant.tenantId(), "qr_guest", "pending");
  }

  @Transactional
  public Map<String, Object> confirmGuestOrder(UUID orderId) {
    RestaurantOrder order = getOrder(orderId);
    if (!"qr_guest".equals(order.getOrderSource()))
      throw new IllegalStateException("Only QR guest orders can be confirmed here");
    order.confirm(tenant.userId());
    List<OrderItem> pendingItems = orderItems.findAllByTenantIdAndOrderId(tenant.tenantId(), orderId);
    Map<String, List<OrderItem>> byStation = new LinkedHashMap<>();
    for (OrderItem item : pendingItems) {
      MenuItem menuItem =
          items
              .findByTenantIdAndId(tenant.tenantId(), item.getMenuItemId())
              .orElseThrow(() -> new NoSuchElementException("Menu item not found"));
      byStation.computeIfAbsent(menuItem.getStation(), ignored -> new ArrayList<>()).add(item);
    }
    List<KotBatch> createdBatches = new ArrayList<>();
    for (Map.Entry<String, List<OrderItem>> entry : byStation.entrySet()) {
      short batchNumber =
          (short)
              (batches.findAllByTenantIdAndOrderIdOrderByBatchNumber(tenant.tenantId(), orderId)
                      .size()
                  + 1);
      KotBatch batch =
          batches.save(new KotBatch(tenant.tenantId(), orderId, entry.getKey(), batchNumber));
      entry.getValue().forEach(item -> item.send(batch.getId()));
      createdBatches.add(batch);
    }
    return Map.of("order", order, "kot_batches", createdBatches);
  }

  public KotBatch kot(UUID id) {
    return batches
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("KOT batch not found"));
  }

  public List<Map<String, Object>> itemsForBatch(UUID id) {
    kot(id);
    return orderItems.findAllByTenantIdAndKotBatchId(tenant.tenantId(), id).stream()
        .map(this::kitchenItem)
        .toList();
  }

  private Map<String, Object> kitchenItem(OrderItem orderItem) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", orderItem.getId());
    item.put("menu_item_id", orderItem.getMenuItemId());
    item.put(
        "name",
        items
            .findByTenantIdAndId(tenant.tenantId(), orderItem.getMenuItemId())
            .map(MenuItem::getName)
            .orElse("Unavailable menu item"));
    item.put("quantity", orderItem.getQuantity());
    item.put("notes", orderItem.getNotes());
    item.put("status", orderItem.getStatus());
    return item;
  }

  public List<Map<String, Object>> pendingKots(String station) {
    if (station != null && !Set.of("kitchen", "bar").contains(station))
      throw new IllegalArgumentException("station must be kitchen or bar");
    return batches.findAllByTenantIdAndPrintedAtIsNull(tenant.tenantId()).stream()
        .filter(batch -> station == null || station.equals(batch.getStation()))
        .sorted(Comparator.comparing(KotBatch::getBatchNumber))
        .map(
            batch ->
                Map.<String, Object>of(
                    "batch", batch,
                    "items", itemsForBatch(batch.getId())))
        .toList();
  }

  @Transactional
  public OrderItem markItemPreparing(UUID itemId) {
    OrderItem item =
        orderItems
            .findByTenantIdAndId(tenant.tenantId(), itemId)
            .orElseThrow(() -> new NoSuchElementException("Order item not found"));
    item.markPreparing();
    return item;
  }

  @Transactional
  public OrderItem markItemServed(UUID itemId) {
    OrderItem item =
        orderItems
            .findByTenantIdAndId(tenant.tenantId(), itemId)
            .orElseThrow(() -> new NoSuchElementException("Order item not found"));
    item.markServed();
    return item;
  }

  @Transactional
  public KotBatch markPrinted(UUID id) {
    KotBatch b = kot(id);
    b.markPrinted();
    return b;
  }

  @Transactional
  public Map<String, Object> generateBill(UUID orderId) {
    RestaurantOrder order = getOrder(orderId);
    if (!"open".equals(order.getStatus())) {
      RestaurantBill existing =
          bills
              .findByTenantIdAndOrderId(tenant.tenantId(), orderId)
              .orElseThrow(() -> new IllegalStateException("Order is not billable"));
      Invoice invoice = billing.getInvoice(existing.getInvoiceId());
      return Map.of(
          "restaurant_bill_id",
          existing.getId(),
          "invoice_id",
          invoice.getId(),
          "total_amount",
          invoice.getTotalAmount().toPlainString());
    }
    List<OrderItem> ordered =
        orderItems.findAllByTenantIdAndOrderId(tenant.tenantId(), orderId).stream()
            .filter(x -> !"cancelled".equals(x.getStatus()))
            .toList();
    if (ordered.isEmpty()) throw new IllegalStateException("Cannot bill an empty order");
    List<BillingService.LineInput> lines = new ArrayList<>();
    for (OrderItem oi : ordered) {
      MenuItem menu =
          items.findByTenantIdAndId(tenant.tenantId(), oi.getMenuItemId()).orElseThrow();
      lines.add(
          new BillingService.LineInput(
              menu.getName(),
              BigDecimal.valueOf(oi.getQuantity()),
              menu.getPrice(),
              menu.getTaxRuleId()));
    }
    UUID customerId = null;
    if (order.getStayId() != null
        && moduleEntitlements.isActive(tenant.tenantId(), ModuleType.STAY)) {
      StayLookupPort.StaySummary stay =
          stayLookup
              .findById(tenant.tenantId(), order.getStayId())
              .orElseThrow(() -> new NoSuchElementException("Stay not found"));
      customerId = stay.customerId();
    }
    var result =
        billing.createInvoice(
            new BillingService.NewInvoice(null, customerId, "restaurant", lines));
    billing.lock(result.invoice().getId());
    RestaurantBill bill =
        bills.save(new RestaurantBill(tenant.tenantId(), orderId, result.invoice().getId()));
    order.markBilled();
    if (order.getTableId() != null)
      tables
          .findByTenantIdAndId(tenant.tenantId(), order.getTableId())
          .ifPresent(table -> table.setStatus("billed"));
    return Map.of(
        "restaurant_bill_id",
        bill.getId(),
        "invoice_id",
        result.invoice().getId(),
        "total_amount",
        result.totalAmount().toPlainString());
  }

  @Transactional
  public String settleBill(UUID billId, String mode, String roomNumber) {
    if (!Set.of("cash", "card", "upi", "account").contains(mode))
      throw new IllegalArgumentException("Invalid settlement mode");
    if ("account".equals(mode))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    RestaurantBill bill =
        bills
            .findByTenantIdAndId(tenant.tenantId(), billId)
            .orElseThrow(() -> new NoSuchElementException("Restaurant bill not found"));
    Invoice invoice = billing.getInvoice(bill.getInvoiceId());
    if ("account".equals(mode)) {
      StayLookupPort.StaySummary stay =
          stayLookup
              .findActiveByRoomNumber(tenant.tenantId(), roomNumber)
              .orElseThrow(() -> new NoSuchElementException("RoomNotFound"));
      RestaurantOrder order = getOrder(bill.getOrderId());
      if (order.getStayId() != null && !order.getStayId().equals(stay.id()))
        throw new IllegalStateException("Order is linked to a different stay");
      order.associateStay(stay.id());
      invoice.associateCustomerIfMissing(stay.customerId());
      billing.postInvoiceToAccount(stay.accountId(), invoice.getId());
    } else if (billing.amountDue(invoice).signum() > 0) {
      billing.payment(
          invoice.getId(),
          mode,
          billing.amountDue(invoice),
          null);
    }
    bill.settle(mode);
    return "settled";
  }

  public record ItemRequest(UUID menuItemId, short quantity, String notes) {}
}
