package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.*;
import java.time.*;
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

  @Transactional
  public DiningTable clearTable(UUID tableId) {
    DiningTable table = tables.findByTenantIdAndId(tenant.tenantId(), tableId)
        .orElseThrow(() -> new NoSuchElementException("Dining table not found"));
    if (!"billed".equals(table.getStatus()))
      throw new IllegalStateException("Create the bill before clearing this table");
    if (orders.existsByTenantIdAndTableIdAndStatus(tenant.tenantId(), tableId, "open"))
      throw new IllegalStateException("An open order is still linked to this table");

    RestaurantOrder latestOrder = orders
        .findFirstByTenantIdAndTableIdAndStatusOrderByCreatedAtDesc(
            tenant.tenantId(), tableId, "billed")
        .orElseThrow(() -> new IllegalStateException("No billed order was found for this table"));
    boolean itemsNotServed = orderItems.findAllByTenantIdAndOrderId(
            tenant.tenantId(), latestOrder.getId()).stream()
        .anyMatch(item -> !Set.of("served", "cancelled").contains(item.getStatus()));
    if (itemsNotServed)
      throw new IllegalStateException("Serve or cancel every item before clearing the table");

    RestaurantBill bill = bills.findByTenantIdAndOrderId(tenant.tenantId(), latestOrder.getId())
        .orElseThrow(() -> new IllegalStateException("Create the bill before clearing this table"));
    Invoice invoice = billing.getInvoice(bill.getInvoiceId());
    boolean postedToGuestAccount = "account".equals(bill.getSettlementMode())
        && invoice.getAccountId() != null;
    if (billing.amountDue(invoice).signum() > 0 && !postedToGuestAccount)
      throw new IllegalStateException("Settle the bill before clearing this table");

    table.clear();
    return table;
  }

  @Transactional(readOnly = true)
  public List<Map<String, String>> roomServiceRooms() {
    moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    return stayLookup.findActiveRoomServiceOptions(tenant.tenantId()).stream()
        .map(option -> Map.of(
            "stay_id", option.stayId().toString(),
            "account_id", option.accountId().toString(),
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
  public RestaurantOrder openOrder(String type, UUID tableId, String roomNumber) {
    if (!Set.of("dine_in", "room_service", "takeaway").contains(type))
      throw new IllegalArgumentException("Invalid restaurant order type");
    if ("room_service".equals(type))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    UUID stayId = null;
    if ("room_service".equals(type) && (roomNumber == null || roomNumber.isBlank()))
      throw new IllegalArgumentException("Choose an occupied room for room service");
    if (roomNumber != null && !roomNumber.isBlank()) {
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
      String normalizedRoomNumber = roomNumber.trim();
      StayLookupPort.StaySummary stay = stayLookup
          .findActiveByRoomNumber(tenant.tenantId(), normalizedRoomNumber)
          .orElseThrow(() -> new NoSuchElementException(
              "No active guest in Room " + normalizedRoomNumber));
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

  public List<Map<String, Object>> tableService() {
    List<RestaurantOrder> openDineInOrders =
        orders.findAllByTenantIdAndOrderTypeAndStatusAndTableIdIsNotNullOrderByCreatedAtDesc(
            tenant.tenantId(), "dine_in", "open");
    Map<UUID, List<Map<String, Object>>> ordersByTable = new HashMap<>();
    for (RestaurantOrder order : openDineInOrders) {
      Map<String, Object> orderView = new LinkedHashMap<>();
      orderView.put("order", order);
      orderView.put("items", orderItems(order.getId()).stream().map(this::kitchenItem).toList());
      ordersByTable.computeIfAbsent(order.getTableId(), ignored -> new ArrayList<>()).add(orderView);
    }
    return tables.findAllByTenantIdOrderByTableNumber(tenant.tenantId()).stream()
        .map(table -> {
          Map<String, Object> tableView = new LinkedHashMap<>();
          tableView.put("table", table);
          tableView.put("orders", ordersByTable.getOrDefault(table.getId(), List.of()));
          return tableView;
        })
        .toList();
  }

  public Map<String, Object> servicePerformance() {
    LocalDateTime since = LocalDateTime.now(ZoneOffset.UTC).minusDays(30);
    List<Map<String, Object>> completedItems =
        orderItems.findAllByTenantIdAndServedAtGreaterThanEqualOrderByServedAtDesc(
                tenant.tenantId(), since).stream()
            .filter(item -> item.getPlacedAt() != null && item.getPreparingAt() != null)
            .map(item -> {
              long waitSeconds = Duration.between(item.getPlacedAt(), item.getPreparingAt()).toSeconds();
              long preparationSeconds = Duration.between(item.getPreparingAt(), item.getServedAt()).toSeconds();
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("item_id", item.getId());
              row.put("order_id", item.getOrderId());
              row.put("menu_item_id", item.getMenuItemId());
              row.put("name", items.findByTenantIdAndId(tenant.tenantId(), item.getMenuItemId())
                  .map(MenuItem::getName).orElse("Unavailable menu item"));
              row.put("placed_at", item.getPlacedAt().atOffset(ZoneOffset.UTC));
              row.put("preparing_at", item.getPreparingAt().atOffset(ZoneOffset.UTC));
              row.put("served_at", item.getServedAt().atOffset(ZoneOffset.UTC));
              row.put("waiting_seconds", waitSeconds);
              row.put("preparation_seconds", preparationSeconds);
              row.put("total_seconds", waitSeconds + preparationSeconds);
              return row;
            })
            .toList();
    return Map.of(
        "period_days", 30,
        "completed_items", completedItems.size(),
        "average_wait_seconds", averageSeconds(completedItems, "waiting_seconds"),
        "average_preparation_seconds", averageSeconds(completedItems, "preparation_seconds"),
        "average_total_seconds", averageSeconds(completedItems, "total_seconds"),
        "items", completedItems);
  }

  private long averageSeconds(List<Map<String, Object>> rows, String key) {
    return rows.isEmpty()
        ? 0
        : Math.round(rows.stream().mapToLong(row -> (Long) row.get(key)).average().orElse(0));
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
    item.put("placed_at", utcTimestamp(orderItem.getPlacedAt()));
    item.put("preparing_at", utcTimestamp(orderItem.getPreparingAt()));
    item.put("served_at", utcTimestamp(orderItem.getServedAt()));
    return item;
  }

  private OffsetDateTime utcTimestamp(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
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
    if (mode == null || !Set.of("cash", "card", "upi", "account").contains(mode))
      throw new IllegalArgumentException("Invalid settlement mode");
    if ("account".equals(mode))
      moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.STAY);
    RestaurantBill bill =
        bills
            .findByTenantIdAndId(tenant.tenantId(), billId)
            .orElseThrow(() -> new NoSuchElementException("Restaurant bill not found"));
    Invoice invoice = billing.getInvoice(bill.getInvoiceId());
    if ("account".equals(mode)) {
      RestaurantOrder order = getOrder(bill.getOrderId());
      StayLookupPort.StaySummary stay;
      if (roomNumber != null && !roomNumber.isBlank()) {
        stay = stayLookup
            .findActiveByRoomNumber(tenant.tenantId(), roomNumber.trim())
            .orElseThrow(() -> new NoSuchElementException(
                "No active guest in Room " + roomNumber.trim()));
      } else if (order.getStayId() != null) {
        stay = stayLookup
            .findById(tenant.tenantId(), order.getStayId())
            .filter(candidate -> "active".equals(candidate.status()))
            .orElseThrow(() -> new IllegalStateException(
                "The order does not have an active linked stay"));
      } else {
        throw new IllegalArgumentException(
            "Provide a room number or create the order linked to an active guest stay");
      }
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
