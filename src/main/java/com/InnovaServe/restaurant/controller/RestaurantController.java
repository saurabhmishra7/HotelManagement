package com.InnovaServe.restaurant.controller;

import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.service.RestaurantService;
import com.InnovaServe.restaurant.service.GuestStaySpendService;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.RequiresModule;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiresModule(ModuleType.RESTAURANT)
public class RestaurantController {
  private final RestaurantService service;
  private final GuestStaySpendService spendHistory;

  public RestaurantController(RestaurantService service, GuestStaySpendService spendHistory) {
    this.service = service;
    this.spendHistory = spendHistory;
  }

  @GetMapping("/stays/{id}/spend-history")
  @RequiresModule(ModuleType.STAY)
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public Map<String, Object> staySpendHistory(
      @PathVariable UUID id, @RequestParam(defaultValue = "all") String source) {
    return spendHistory.forStay(id, source);
  }

  @GetMapping("/customers/{customerId}/spend-history")
  @RequiresModule(ModuleType.STAY)
  @PreAuthorize("hasAuthority('PERM_BILLING_READ')")
  public Map<String, Object> customerSpendHistory(
      @PathVariable UUID customerId, @RequestParam(defaultValue = "all") String source) {
    return spendHistory.forCustomer(customerId, source);
  }

  @GetMapping("/menu-categories")
  @PreAuthorize("hasAuthority('PERM_MENU_READ')")
  public List<MenuCategory> categories() {
    return service.categories();
  }

  @PostMapping("/menu-categories")
  @PreAuthorize("hasAuthority('PERM_MENU_MANAGE')")
  public MenuCategory createCategory(@RequestBody CategoryRequest r) {
    return service.addCategory(r.name(), r.sortOrder() == null ? 0 : r.sortOrder());
  }

  @GetMapping("/menu-items")
  @PreAuthorize("hasAuthority('PERM_MENU_READ')")
  public List<MenuItem> items(
      @RequestParam(name = "category_id", required = false) UUID category,
      @RequestParam(required = false) Boolean active) {
    return service.items(category, active);
  }

  @PostMapping("/menu-items")
  @PreAuthorize("hasAuthority('PERM_MENU_MANAGE')")
  public MenuItem createItem(@RequestBody MenuItemRequest r) {
    return service.addItem(
        r.categoryId(),
        r.name(),
        r.price(),
        r.taxRuleId(),
        r.station(),
        r.vegFlag() == null || r.vegFlag());
  }

  @PatchMapping("/menu-items/{id}")
  @PreAuthorize("hasAuthority('PERM_MENU_MANAGE')")
  public MenuItem updateItem(@PathVariable UUID id, @RequestBody MenuItemUpdate r) {
    return service.updateItem(id, r.price(), r.active());
  }

  @GetMapping("/dining-tables")
  @PreAuthorize("hasAuthority('PERM_TABLE_READ')")
  public List<DiningTable> tables() {
    return service.tables();
  }

  @PostMapping("/dining-tables")
  @PreAuthorize("hasAuthority('PERM_TABLE_MANAGE')")
  public DiningTable createTable(@RequestBody DiningTableRequest r) {
    return service.addTable(r.tableNumber(), r.section());
  }

  @PostMapping("/orders")
  @PreAuthorize("hasAuthority('PERM_ORDER_CREATE')")
  public Map<String, Object> openOrder(@RequestBody OpenOrder r) {
    RestaurantOrder o = service.openOrder(r.orderType(), r.tableId(), r.stayId());
    return Map.of("order_id", o.getId());
  }

  @GetMapping("/orders")
  @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
  public List<RestaurantOrder> orders() {
    return service.orders();
  }

  @PostMapping("/orders/{id}/items")
  @PreAuthorize("hasAuthority('PERM_ORDER_CREATE')")
  public Map<String, Object> addItems(@PathVariable UUID id, @RequestBody ItemsRequest r) {
    return service.addItems(
        id,
        r.items().stream()
            .map(x -> new RestaurantService.ItemRequest(x.menuItemId(), x.quantity(), x.notes()))
            .toList());
  }

  @GetMapping("/orders/{id}")
  @PreAuthorize("hasAuthority('PERM_ORDER_READ')")
  public Map<String, Object> getOrder(@PathVariable UUID id) {
    return Map.of("order", service.getOrder(id), "items", service.orderItems(id));
  }

  @PostMapping("/orders/{id}/bill")
  @PreAuthorize("hasAuthority('PERM_BILL_CREATE')")
  public Map<String, Object> bill(@PathVariable UUID id) {
    return service.generateBill(id);
  }

  @PostMapping("/bills/{id}/settle")
  @PreAuthorize("hasAuthority('PERM_BILL_SETTLE')")
  public Map<String, String> settle(@PathVariable UUID id, @RequestBody SettleRequest r) {
    return Map.of("status", service.settleBill(id, r.settlementMode(), r.roomNumber()));
  }

  @GetMapping("/orders/pending-guest")
  @PreAuthorize("hasAuthority('PERM_ORDER_CONFIRM')")
  public List<RestaurantOrder> pendingGuest() {
    return service.pendingGuestOrders();
  }

  @PostMapping("/orders/{id}/confirm-guest")
  @PreAuthorize("hasAuthority('PERM_ORDER_CONFIRM')")
  public Map<String, Object> confirmGuestOrder(@PathVariable UUID id) {
    return service.confirmGuestOrder(id);
  }

  @GetMapping("/kot-batches/{id}")
  @PreAuthorize("hasAuthority('PERM_KITCHEN_READ')")
  public Map<String, Object> kot(@PathVariable UUID id) {
    KotBatch batch = service.kot(id);
    return Map.of("batch", batch, "items", service.itemsForBatch(id));
  }

  @GetMapping("/kot-batches/pending")
  @PreAuthorize("hasAuthority('PERM_KITCHEN_READ')")
  public List<Map<String, Object>> pendingKots(
      @RequestParam(required = false) String station) {
    return service.pendingKots(station);
  }

  @PostMapping("/order-items/{id}/preparing")
  @PreAuthorize("hasAuthority('PERM_KITCHEN_MANAGE')")
  public OrderItem markItemPreparing(@PathVariable UUID id) {
    return service.markItemPreparing(id);
  }

  @PostMapping("/order-items/{id}/served")
  @PreAuthorize("hasAuthority('PERM_KITCHEN_MANAGE')")
  public OrderItem markItemServed(@PathVariable UUID id) {
    return service.markItemServed(id);
  }

  @PostMapping("/kot-batches/{id}/mark-printed")
  @PreAuthorize("hasAuthority('PERM_KITCHEN_MANAGE')")
  public Map<String, Object> printed(@PathVariable UUID id) {
    return Map.of("printed_at", service.markPrinted(id).getPrintedAt());
  }

  public record CategoryRequest(String name, @JsonProperty("sort_order") Integer sortOrder) {}

  public record DiningTableRequest(
      @JsonProperty("table_number") String tableNumber, String section) {}

  public record MenuItemRequest(
      @JsonProperty("category_id") UUID categoryId,
      String name,
      BigDecimal price,
      @JsonProperty("tax_rule_id") UUID taxRuleId,
      String station,
      @JsonProperty("veg_flag") Boolean vegFlag) {}

  public record MenuItemUpdate(BigDecimal price, Boolean active) {}

  public record OpenOrder(
      @JsonProperty("order_type") String orderType,
      @JsonProperty("table_id") UUID tableId,
      @JsonProperty("stay_id") UUID stayId) {}

  public record ItemRequest(
      @JsonProperty("menu_item_id") UUID menuItemId, short quantity, String notes) {}

  public record ItemsRequest(List<ItemRequest> items) {}

  public record SettleRequest(
      @JsonProperty("settlement_mode") String settlementMode,
      @JsonProperty("room_number") String roomNumber) {}
}
