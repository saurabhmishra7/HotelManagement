package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.event.InvoiceFullyPaidEvent;
import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.inventory.event.OrderItemServedEvent;
import com.InnovaServe.inventory.service.InventoryService;
import org.springframework.context.ApplicationEventPublisher;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantService {
  private final TenantContext tenant;
  private final TenantRepository tenants;
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
  private final TaxRuleRepository taxRules;
  private final InventoryService inventory;
  private final ApplicationEventPublisher events;

  public RestaurantService(
      TenantContext t,
      TenantRepository tenants,
      MenuCategoryRepository c,
      MenuItemRepository i,
      DiningTableRepository tables,
      RestaurantOrderRepository o,
      OrderItemRepository oi,
      KotBatchRepository b,
      RestaurantBillRepository bills,
      BillingService billing,
      ModuleEntitlementService moduleEntitlements,
      StayLookupPort stayLookup,
      TaxRuleRepository taxRules,
      InventoryService inventory,
      ApplicationEventPublisher events) {
    tenant = t;
    this.tenants = tenants;
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
    this.taxRules = taxRules;
    this.inventory = inventory;
    this.events = events;
  }

  public Map<String, Object> recipe(UUID menuItemId) {
    moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.INVENTORY);
    return inventory.recipe(menuItemId);
  }

  @Transactional
  public Map<String, Object> saveRecipe(UUID menuItemId, List<InventoryService.IngredientRequest> ingredients) {
    moduleEntitlements.requireActive(tenant.tenantId(), ModuleType.INVENTORY);
    return inventory.saveRecipe(menuItemId, ingredients);
  }

  public List<MenuCategory> categories() {
    return categories.findAllByTenantIdOrderBySortOrderAscNameAsc(tenant.tenantId());
  }

  public List<TaxRule> restaurantTaxRules() {
    return billing.taxes("restaurant", true);
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
      UUID cat,
      String name,
      String itemCode,
      java.math.BigDecimal price,
      UUID tax,
      String station,
      boolean veg) {
    categories
        .findByTenantIdAndId(tenant.tenantId(), cat)
        .orElseThrow(() -> new NoSuchElementException("Menu category not found"));
    String normalizedCode = normalizeItemCode(itemCode);
    ensureItemCodeAvailable(normalizedCode, null);
    return items.save(new MenuItem(tenant.tenantId(), cat, name, normalizedCode, price, tax, station, veg));
  }

  @Transactional
  public MenuItem updateItem(
      UUID id,
      UUID categoryId,
      String name,
      String itemCode,
      java.math.BigDecimal price,
      UUID taxRuleId,
      Boolean clearTaxRule,
      String station,
      Boolean vegFlag,
      Boolean active) {
    MenuItem item =
        items
            .findByTenantIdAndId(tenant.tenantId(), id)
            .orElseThrow(() -> new NoSuchElementException("Menu item not found"));
    if (categoryId != null) {
      categories.findByTenantIdAndId(tenant.tenantId(), categoryId)
          .orElseThrow(() -> new NoSuchElementException("Menu category not found"));
    }
    if (itemCode != null) {
      String normalizedCode = normalizeItemCode(itemCode);
      ensureItemCodeAvailable(normalizedCode, id);
      itemCode = normalizedCode == null ? "" : normalizedCode;
    }
    item.update(categoryId, name, itemCode, price, taxRuleId, clearTaxRule, station, vegFlag, active);
    return item;
  }

  private String normalizeItemCode(String itemCode) {
    if (itemCode == null || itemCode.isBlank()) return null;
    return itemCode.trim().toUpperCase(Locale.ROOT);
  }

  private void ensureItemCodeAvailable(String itemCode, UUID currentItemId) {
    if (itemCode == null) return;
    items.findByTenantIdAndItemCodeIgnoreCase(tenant.tenantId(), itemCode)
        .filter(existing -> !existing.getId().equals(currentItemId))
        .ifPresent(existing -> { throw new IllegalArgumentException("Dish code is already in use"); });
  }

  public List<DiningTable> tables() {
    return tables.findAllByTenantIdOrderByTableNumber(tenant.tenantId());
  }

  @Transactional
  public DiningTable markTableDirty(UUID tableId) {
    DiningTable table = tables.findByTenantIdAndId(tenant.tenantId(), tableId)
        .orElseThrow(() -> new NoSuchElementException("Dining table not found"));
    if (!"billed".equals(table.getStatus()))
      throw new IllegalStateException("Create the bill before marking this table dirty");
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
      throw new IllegalStateException("Serve or cancel every item before marking the table dirty");

    RestaurantBill bill = bills.findByTenantIdAndOrderId(tenant.tenantId(), latestOrder.getId())
        .orElseThrow(() -> new IllegalStateException("Create the bill before marking this table dirty"));
    Invoice invoice = billing.getInvoice(bill.getInvoiceId());
    boolean postedToGuestAccount = "account".equals(bill.getSettlementMode())
        && invoice.getAccountId() != null;
    if (billing.amountDue(invoice).signum() > 0 && !postedToGuestAccount)
      throw new IllegalStateException("Settle the bill before clearing this table");

    table.markDirty();
    return table;
  }

  @Transactional
  public DiningTable markTableReady(UUID tableId) {
    DiningTable table = tables.findByTenantIdAndId(tenant.tenantId(), tableId)
        .orElseThrow(() -> new NoSuchElementException("Dining table not found"));
    table.markReady();
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
  public DiningTable updateTable(UUID id, String number, String section) {
    if (number == null || number.isBlank() || number.length() > 10)
      throw new IllegalArgumentException("Table number is required and must be at most 10 characters");
    String normalizedNumber = number.trim();
    if (section != null && section.length() > 50)
      throw new IllegalArgumentException("Section must be at most 50 characters");
    DiningTable table = tables.findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Dining table not found"));
    if (!"free".equals(table.getStatus()))
      throw new IllegalStateException("Only vacant tables can be edited");
    if (tables.existsByTenantIdAndTableNumberAndIdNot(tenant.tenantId(), normalizedNumber, id))
      throw new IllegalArgumentException("Table number already exists");
    table.updateDetails(normalizedNumber, section == null || section.isBlank() ? null : section.trim());
    return table;
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

  public Map<String, Object> orderDetails(UUID id) {
    RestaurantOrder order = getOrder(id);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("order", order);
    details.put("items", orderItemsView(id));
    bills.findByTenantIdAndOrderId(tenant.tenantId(), id).ifPresent(bill -> {
      details.put("restaurant_bill", bill);
      details.put("invoice_details", billing.invoice(bill.getInvoiceId()));
    });
    if (!details.containsKey("invoice_details")) {
      details.put("bill_preview", billPreview(id));
    }
    return details;
  }

  private Map<String, Object> billPreview(UUID orderId) {
    BigDecimal subtotal = BigDecimal.ZERO;
    BigDecimal taxTotal = BigDecimal.ZERO;
    List<Map<String, Object>> lines = new ArrayList<>();
    LocalDate today = LocalDate.now();
    Map<UUID, MenuItem> menuItemsById = items.findAllByTenantIdOrderByName(tenant.tenantId())
        .stream().collect(java.util.stream.Collectors.toMap(MenuItem::getId, item -> item));
    Map<UUID, TaxRule> taxRulesById = taxRules.findAllByTenantId(tenant.tenantId())
        .stream().collect(java.util.stream.Collectors.toMap(TaxRule::getId, rule -> rule));

    for (OrderItem orderItem : orderItems(orderId)) {
      if ("cancelled".equals(orderItem.getStatus())) continue;
      MenuItem menuItem = Optional.ofNullable(menuItemsById.get(orderItem.getMenuItemId()))
          .orElseThrow(() -> new NoSuchElementException("Menu item not found"));
      BigDecimal quantity = BigDecimal.valueOf(orderItem.getQuantity());
      BigDecimal unitPrice = menuItem.getPrice();
      BigDecimal base = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
      BigDecimal rate = BigDecimal.ZERO;

      if (menuItem.getTaxRuleId() != null) {
        TaxRule taxRule = Optional.ofNullable(taxRulesById.get(menuItem.getTaxRuleId()))
            .orElseThrow(() -> new NoSuchElementException("Tax rule not found"));
        if (!taxRule.getEffectiveFrom().isAfter(today)
            && (taxRule.getEffectiveTo() == null || !taxRule.getEffectiveTo().isBefore(today))) {
          rate = taxRule.getRatePercent();
        }
      }

      BigDecimal tax = base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      BigDecimal lineTotal = base.add(tax);
      subtotal = subtotal.add(base);
      taxTotal = taxTotal.add(tax);
      Map<String, Object> line = new LinkedHashMap<>();
      line.put("description", menuItem.getName());
      line.put("quantity", quantity);
      line.put("unitPrice", unitPrice);
      line.put("taxAmount", tax);
      line.put("lineTotal", lineTotal);
      lines.add(line);
    }

    return Map.of(
        "line_items", lines,
        "subtotal", subtotal,
        "tax_amount", taxTotal,
        "total_amount", subtotal.add(taxTotal),
        "paid_total", BigDecimal.ZERO,
        "credit_total", BigDecimal.ZERO,
        "amount_due", subtotal.add(taxTotal));
  }

  public List<RestaurantOrder> orders() {
    return orders.findAllByTenantIdOrderByCreatedAtDesc(tenant.tenantId());
  }

  public List<Map<String, Object>> tableService() {
    List<DiningTable> diningTables = tables.findAllByTenantIdOrderByTableNumber(tenant.tenantId());
    Map<UUID, String> tableStatuses = diningTables.stream()
        .collect(java.util.stream.Collectors.toMap(DiningTable::getId, DiningTable::getStatus));
    List<RestaurantOrder> openDineInOrders =
        orders.findAllByTenantIdAndOrderTypeAndStatusAndTableIdIsNotNullOrderByCreatedAtDesc(
            tenant.tenantId(), "dine_in", "open");
    Map<UUID, List<Map<String, Object>>> ordersByTable = new HashMap<>();
    for (RestaurantOrder order : openDineInOrders) {
      Map<String, Object> orderView = new LinkedHashMap<>();
      orderView.put("order", order);
      orderView.put("items", orderItems(order.getId()).stream().map(this::kitchenItem).toList());
      bills.findByTenantIdAndOrderId(tenant.tenantId(), order.getId()).ifPresent(bill -> {
        orderView.put("restaurant_bill", bill);
        orderView.put("invoice_details", billing.invoice(bill.getInvoiceId()));
      });
      ordersByTable.computeIfAbsent(order.getTableId(), ignored -> new ArrayList<>()).add(orderView);
    }
    Set<UUID> billedOrderTablesAdded = new HashSet<>();
    List<RestaurantOrder> billedDineInOrders =
        orders.findAllByTenantIdAndOrderTypeAndStatusAndTableIdIsNotNullOrderByCreatedAtDesc(
            tenant.tenantId(), "dine_in", "billed");
    for (RestaurantOrder order : billedDineInOrders) {
      UUID tableId = order.getTableId();
      if (!"billed".equals(tableStatuses.get(tableId))
          || ordersByTable.containsKey(tableId)
          || !billedOrderTablesAdded.add(tableId)) continue;
      Map<String, Object> orderView = new LinkedHashMap<>();
      orderView.put("order", order);
      orderView.put("items", orderItems(order.getId()).stream().map(this::kitchenItem).toList());
      bills.findByTenantIdAndOrderId(tenant.tenantId(), order.getId()).ifPresent(bill -> {
        orderView.put("restaurant_bill", bill);
        orderView.put("invoice_details", billing.invoice(bill.getInvoiceId()));
      });
      ordersByTable.put(tableId, new ArrayList<>(List.of(orderView)));
    }
    return diningTables.stream()
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
    if (bills.findByTenantIdAndOrderId(tenant.tenantId(), orderId).isPresent())
      throw new IllegalStateException("A bill is already prepared for this order; no more items can be added");
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
    Map<UUID, List<OrderItem>> itemsByBatch = new LinkedHashMap<>();
    for (var entry : stationItems.entrySet()) {
      short number =
          (short) (batches.findAllByTenantIdAndOrderIdOrderByBatchNumber(t, orderId).size() + 1);
      KotBatch batch = batches.save(new KotBatch(t, orderId, entry.getKey(), number));
      savedBatches.add(batch);
      for (OrderItem item : entry.getValue()) {
        item.send(batch.getId());
        savedItems.add(item);
      }
      itemsByBatch.put(batch.getId(), entry.getValue());
    }
    return Map.of("kot_batches", savedBatches, "order_items", savedItems,
        "print_tickets", kotPrintTickets(savedBatches, itemsByBatch));
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
    Map<UUID, List<OrderItem>> itemsByBatch = new LinkedHashMap<>();
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
      itemsByBatch.put(batch.getId(), entry.getValue());
    }
    return Map.of("order", order, "kot_batches", createdBatches,
        "print_tickets", kotPrintTickets(createdBatches, itemsByBatch));
  }

  private List<Map<String, Object>> kotPrintTickets(
      List<KotBatch> createdBatches, Map<UUID, List<OrderItem>> itemsByBatch) {
    return createdBatches.stream().map(batch -> {
      Map<String, Object> ticket = new LinkedHashMap<>();
      ticket.put("id", batch.getId());
      ticket.put("station", batch.getStation());
      ticket.put("batch_number", batch.getBatchNumber());
      ticket.put("items", itemsByBatch.getOrDefault(batch.getId(), List.of()).stream()
          .map(this::kitchenItem).toList());
      return ticket;
    }).toList();
  }

  public KotBatch kot(UUID id) {
    return batches
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("KOT batch not found"));
  }

  public List<Map<String, Object>> orderItemsView(UUID id) {
    return orderItems(id).stream().map(this::kitchenItem).toList();
  }

  public List<Map<String, Object>> itemsForBatch(UUID id) {
    kot(id);
    return orderItems.findAllByTenantIdAndKotBatchIdOrderByPlacedAtAscIdAsc(tenant.tenantId(), id).stream()
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
    return batches.findAllByTenantId(tenant.tenantId()).stream()
        .filter(batch -> station == null || station.equals(batch.getStation()))
        .filter(batch -> orderItems.existsByTenantIdAndKotBatchIdAndStatusNotIn(
            tenant.tenantId(), batch.getId(), List.of("served", "cancelled")))
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
            .lockByTenantIdAndId(tenant.tenantId(), itemId)
            .orElseThrow(() -> new NoSuchElementException("Order item not found"));
    boolean printerOnly = tenants.findById(tenant.tenantId())
        .map(Tenant::getRestaurantServiceMode)
        .filter("thermal_printer"::equals)
        .isPresent();
    item.markServed(printerOnly);
    events.publishEvent(new OrderItemServedEvent(tenant.tenantId(), item.getMenuItemId(), item.getId(), item.getQuantity()));
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
    var existing = bills.findByTenantIdAndOrderId(tenant.tenantId(), orderId);
    if (existing.isPresent()) {
      RestaurantBill existingBill = existing.get();
      Invoice invoice = billing.getInvoice(existingBill.getInvoiceId());
      return Map.of(
          "restaurant_bill_id",
          existingBill.getId(),
          "invoice_id",
          invoice.getId(),
          "total_amount",
          invoice.getTotalAmount().toPlainString(),
          "invoice_details",
          billing.invoice(invoice.getId()));
    }
    if (!"open".equals(order.getStatus())) throw new IllegalStateException("Order is not billable");
    List<OrderItem> ordered =
        orderItems.findAllByTenantIdAndOrderId(tenant.tenantId(), orderId).stream()
            .filter(x -> !"cancelled".equals(x.getStatus()))
            .toList();
    if (ordered.isEmpty()) throw new IllegalStateException("Cannot bill an empty order");
    if (ordered.stream().anyMatch(item -> !"served".equals(item.getStatus())))
      throw new IllegalStateException("Serve every order item before creating the bill");
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
    return Map.of(
        "restaurant_bill_id",
        bill.getId(),
        "invoice_id",
        result.invoice().getId(),
        "total_amount",
        result.totalAmount().toPlainString(),
        "invoice_details",
        billing.invoice(result.invoice().getId()));
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
      order.markBilled();
      if (order.getTableId() != null) {
        tables.findByTenantIdAndId(tenant.tenantId(), order.getTableId())
            .ifPresent(table -> table.setStatus("billed"));
      }
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

  @EventListener
  @Transactional
  public void onInvoiceFullyPaid(InvoiceFullyPaidEvent event) {
    bills.findByTenantIdAndInvoiceId(event.tenantId(), event.invoiceId()).ifPresent(bill ->
        orders.findByTenantIdAndId(event.tenantId(), bill.getOrderId()).ifPresent(order -> {
          boolean newlyBilled = !"billed".equals(order.getStatus());
          if (newlyBilled) order.markBilled();
          if (newlyBilled && order.getTableId() != null) {
            tables.findByTenantIdAndId(event.tenantId(), order.getTableId())
                .ifPresent(table -> table.setStatus("billed"));
          }
        }));
  }

  public record ItemRequest(UUID menuItemId, short quantity, String notes) {}
}
