package com.InnovaServe.inventory.service;

import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.NotificationService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.expense.entity.Expense;
import com.InnovaServe.expense.service.ExpenseService;
import com.InnovaServe.inventory.entity.*;
import com.InnovaServe.inventory.repository.*;
import com.InnovaServe.restaurant.entity.MenuItem;
import com.InnovaServe.restaurant.repository.MenuItemRepository;
import com.InnovaServe.stay.enums.RoomType;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {
  private final TenantContext tenant;
  private final ModuleEntitlementService entitlements;
  private final InventoryItemRepository items;
  private final StockPurchaseRepository purchases;
  private final StockConsumptionRepository consumption;
  private final RecipeRepository recipes;
  private final RecipeIngredientRepository ingredients;
  private final RoomParItemRepository roomPar;
  private final MenuItemRepository menuItems;
  private final ExpenseService expenses;
  private final NotificationService notifications;

  public InventoryService(TenantContext tenant, ModuleEntitlementService entitlements, InventoryItemRepository items,
      StockPurchaseRepository purchases, StockConsumptionRepository consumption, RecipeRepository recipes,
      RecipeIngredientRepository ingredients, RoomParItemRepository roomPar, MenuItemRepository menuItems,
      ExpenseService expenses, NotificationService notifications) {
    this.tenant = tenant; this.entitlements = entitlements; this.items = items; this.purchases = purchases;
    this.consumption = consumption; this.recipes = recipes; this.ingredients = ingredients;
    this.roomPar = roomPar; this.menuItems = menuItems; this.expenses = expenses; this.notifications = notifications;
  }

  public void requireInventory(UUID tenantId) { entitlements.requireActive(tenantId, ModuleType.INVENTORY); }
  public List<InventoryItem> items(String usageType, Boolean active) {
    UUID tid = tenant.tenantId();
    return items.findAllByTenantIdOrderByName(tid).stream()
        .filter(item -> usageType == null || usageType.equals(item.getUsageType()))
        .filter(item -> active == null || active == item.isActive()).toList();
  }
  public List<InventoryItem> lowStock() { return items.findLowStockByTenant(tenant.tenantId()); }

  @Transactional
  public InventoryItem createItem(String name, String unit, String usageType, BigDecimal threshold) {
    validateItem(name, unit, usageType, threshold);
    UUID tenantId = tenant.tenantId();
    InventoryItem saved = items.save(new InventoryItem(tenantId, name.trim(), unit.trim(), usageType, threshold));
    if (saved.getCurrentStock().compareTo(saved.getReorderThreshold()) <= 0) notifyLowStock(saved);
    return saved;
  }

  @Transactional
  public InventoryItem updateItem(UUID id, String name, String unit, String usageType, BigDecimal threshold, Boolean active) {
    InventoryItem item = ownedItem(id, true);
    if (name != null && (name.isBlank() || name.trim().length() > 150)) throw new IllegalArgumentException("Item name is required and must be at most 150 characters");
    if (unit != null && (unit.isBlank() || unit.trim().length() > 20)) throw new IllegalArgumentException("Unit is required and must be at most 20 characters");
    if (usageType != null && !Set.of("restaurant", "room", "other").contains(usageType)) throw new IllegalArgumentException("Usage type must be restaurant, room, or other");
    if (threshold != null && (threshold.signum() < 0 || threshold.scale() > 3)) throw new IllegalArgumentException("Reorder threshold must be zero or greater with at most three decimals");
    BigDecimal before = item.getCurrentStock();
    boolean wasActive = item.isActive();
    boolean wasLow = item.isActive() && before.compareTo(item.getReorderThreshold()) <= 0;
    item.update(name == null ? null : name.trim(), unit == null ? null : unit.trim(), usageType, threshold, active);
    if (item.isActive() && (!wasActive || !wasLow) && before.compareTo(item.getReorderThreshold()) <= 0) notifyLowStock(item);
    return item;
  }

  @Transactional
  public void deleteItem(UUID id) {
    InventoryItem item = ownedItem(id, true);
    UUID tenantId = tenant.tenantId();
    ingredients.deleteAllByInventoryItemId(id);
    roomPar.deleteAllByTenantIdAndInventoryItemId(tenantId, id);
    purchases.deleteAllByTenantIdAndItemId(tenantId, id);
    consumption.deleteAllByTenantIdAndItemId(tenantId, id);
    items.delete(item);
  }

  @Transactional
  public PurchaseResult purchase(UUID id, BigDecimal quantity, BigDecimal unitCost, String vendor, String paymentMode) {
    if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 3) throw new IllegalArgumentException("Purchase quantity must be greater than zero with at most three decimals");
    if (unitCost == null || unitCost.signum() < 0 || unitCost.scale() > 2) throw new IllegalArgumentException("Unit cost must be zero or greater with at most two decimals");
    if (paymentMode == null || !Set.of("cash", "card", "upi", "petty_cash").contains(paymentMode)) throw new IllegalArgumentException("Payment mode must be cash, card, UPI, or petty cash");
    if (vendor != null && vendor.trim().length() > 150) throw new IllegalArgumentException("Vendor name must be at most 150 characters");
    InventoryItem item = ownedItem(id, true);
    BigDecimal total = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    String department = switch (item.getUsageType()) { case "restaurant" -> "restaurant"; case "room" -> "rooms"; default -> "general"; };
    Expense expense = expenses.addInventoryPurchaseExpense(department, vendor == null || vendor.isBlank() ? null : vendor.trim(), total, paymentMode);
    StockPurchase purchase = purchases.save(new StockPurchase(tenant.tenantId(), id, quantity, unitCost, total,
        vendor == null || vendor.isBlank() ? null : vendor.trim(), expense.getId(), tenant.userId()));
    item.purchase(quantity, unitCost);
    return new PurchaseResult(purchase, item.getAverageUnitCost(), expense.getId());
  }

  @Transactional
  public StockConsumption adjust(UUID id, BigDecimal quantity, String note) {
    if (quantity == null || quantity.signum() == 0 || quantity.scale() > 3) throw new IllegalArgumentException("Adjustment quantity must be non-zero with at most three decimals; use a negative value to remove stock");
    if (note == null || note.isBlank()) throw new IllegalArgumentException("A reason is required for every stock adjustment");
    if (note.trim().length() > 2000) throw new IllegalArgumentException("Adjustment reason must be at most 2000 characters");
    InventoryItem item = ownedItem(id, true);
    BigDecimal before = item.getCurrentStock();
    item.changeStock(quantity);
    if (quantity.signum() < 0 && before.compareTo(item.getReorderThreshold()) > 0
        && item.getCurrentStock().compareTo(item.getReorderThreshold()) <= 0) notifyLowStock(item);
    return consumption.save(new StockConsumption(tenant.tenantId(), id, quantity, "manual_adjustment", null, null, note.trim(), tenant.userId()));
  }

  public List<Map<String, Object>> history(UUID id) {
    UUID tid = tenant.tenantId(); ownedItem(id, false);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (StockPurchase purchase : purchases.findAllByTenantIdAndItemIdOrderByPurchasedAtDesc(tid, id)) {
      Map<String, Object> row = new LinkedHashMap<>(); row.put("type", "purchase"); row.put("quantity", purchase.getQuantity());
      row.put("unit_cost", purchase.getUnitCost()); row.put("total_cost", purchase.getTotalCost()); row.put("vendor_name", purchase.getVendorName()); row.put("expense_id", purchase.getExpenseId()); row.put("created_at", purchase.getPurchasedAt()); rows.add(row);
    }
    for (StockConsumption entry : consumption.findAllByTenantIdAndItemIdOrderByCreatedAtDesc(tid, id)) {
      Map<String, Object> row = new LinkedHashMap<>(); row.put("type", entry.getConsumptionType()); row.put("quantity", entry.getQuantity());
      row.put("reference_type", entry.getReferenceType()); row.put("reference_id", entry.getReferenceId()); row.put("note", entry.getNote()); row.put("created_at", entry.getCreatedAt()); rows.add(row);
    }
    rows.sort(Comparator.comparing(row -> (Instant) row.get("created_at"), Comparator.nullsLast(Comparator.reverseOrder())));
    return rows;
  }

  public Map<String, Object> recipe(UUID menuItemId) {
    UUID tid = tenant.tenantId(); requireInventory(tid);
    menuItems.findByTenantIdAndId(tid, menuItemId).orElseThrow(() -> new NoSuchElementException("Menu item not found"));
    Map<String, Object> result = new LinkedHashMap<>(); result.put("menu_item_id", menuItemId);
    Recipe recipe = recipes.findByTenantIdAndMenuItemId(tid, menuItemId).orElse(null);
    List<Map<String, Object>> rows = new ArrayList<>();
    if (recipe != null) for (RecipeIngredient ingredient : ingredients.findAllByRecipeId(recipe.getId())) {
      InventoryItem item = items.findByTenantIdAndId(tid, ingredient.getInventoryItemId()).orElse(null);
      if (item != null) rows.add(Map.of("inventory_item_id", item.getId(), "name", item.getName(), "unit", item.getUnit(), "quantity_required", ingredient.getQuantityRequired()));
    }
    result.put("ingredients", rows); return result;
  }

  @Transactional
  public Map<String, Object> saveRecipe(UUID menuItemId, List<IngredientRequest> rows) {
    UUID tid = tenant.tenantId(); requireInventory(tid);
    MenuItem menuItem = menuItems.findByTenantIdAndId(tid, menuItemId).orElseThrow(() -> new NoSuchElementException("Menu item not found"));
    if (!menuItem.isActive()) throw new IllegalStateException("A recipe cannot be changed for a hidden menu item");
    List<IngredientRequest> requested = rows == null ? List.of() : rows;
    Set<UUID> seen = new HashSet<>();
    for (IngredientRequest row : requested) {
      if (row.inventoryItemId() == null || !seen.add(row.inventoryItemId())) throw new IllegalArgumentException("Each recipe ingredient must be selected only once");
      InventoryItem item = items.findByTenantIdAndId(tid, row.inventoryItemId()).orElseThrow(() -> new NoSuchElementException("Inventory item not found"));
      if (!item.isActive()) throw new IllegalArgumentException("Inactive inventory items cannot be used in a recipe");
      requirePositiveQuantity(row.quantity(), "Recipe ingredient quantity");
    }
    Recipe recipe = recipes.findByTenantIdAndMenuItemId(tid, menuItemId).orElse(null);
    if (requested.isEmpty()) {
      if (recipe != null) recipes.delete(recipe);
      return recipe(menuItemId);
    }
    if (recipe == null) recipe = recipes.save(new Recipe(tid, menuItemId));
    ingredients.deleteAllByRecipeId(recipe.getId());
    for (IngredientRequest row : requested) ingredients.save(new RecipeIngredient(recipe.getId(), row.inventoryItemId(), row.quantity()));
    return recipe(menuItemId);
  }

  public List<Map<String, Object>> roomParList(String roomType) {
    UUID tid = tenant.tenantId(); String type = roomType(roomType); requireInventory(tid); entitlements.requireActive(tid, ModuleType.STAY);
    return roomPar.findAllByTenantIdAndRoomTypeOrderById(tid, type).stream().map(row -> {
      InventoryItem item = items.findByTenantIdAndId(tid, row.getInventoryItemId()).orElse(null);
      if (item == null) return Map.<String, Object>of("inventory_item_id", row.getInventoryItemId(), "quantity_per_clean", row.getQuantityPerClean());
      return Map.<String, Object>of("inventory_item_id", item.getId(), "name", item.getName(), "unit", item.getUnit(), "quantity_per_clean", row.getQuantityPerClean());
    }).toList();
  }

  @Transactional
  public List<Map<String, Object>> saveRoomParList(String roomType, List<IngredientRequest> requested) {
    UUID tid = tenant.tenantId(); String type = roomType(roomType); requireInventory(tid); entitlements.requireActive(tid, ModuleType.STAY);
    List<IngredientRequest> rows = requested == null ? List.of() : requested;
    Set<UUID> seen = new HashSet<>();
    for (IngredientRequest row : rows) {
      if (row.inventoryItemId() == null || !seen.add(row.inventoryItemId())) throw new IllegalArgumentException("Each par-list item must be selected only once");
      InventoryItem item = items.findByTenantIdAndId(tid, row.inventoryItemId()).orElseThrow(() -> new NoSuchElementException("Inventory item not found"));
      if (!item.isActive()) throw new IllegalArgumentException("Inactive inventory items cannot be used in a par list");
      requirePositiveQuantity(row.quantity(), "Quantity per clean");
    }
    roomPar.deleteAllByTenantIdAndRoomType(tid, type);
    for (IngredientRequest row : rows) roomPar.save(new RoomParItem(tid, type, row.inventoryItemId(), row.quantity()));
    return roomParList(type);
  }

  @Transactional
  public void deductRecipe(UUID tenantId, UUID menuItemId, UUID orderItemId, short orderedQuantity) {
    if (!entitlements.isActive(tenantId, ModuleType.INVENTORY)) return;
    Recipe recipe = recipes.findByTenantIdAndMenuItemId(tenantId, menuItemId).orElse(null);
    if (recipe == null) return;
    for (RecipeIngredient ingredient : ingredients.findAllByRecipeId(recipe.getId())) {
      InventoryItem item = items.lockByTenantIdAndId(tenantId, ingredient.getInventoryItemId()).orElse(null);
      if (item == null || !item.isActive()) continue;
      BigDecimal used = ingredient.getQuantityRequired().multiply(BigDecimal.valueOf(orderedQuantity)).setScale(3, RoundingMode.HALF_UP);
      item.changeStock(used.negate());
      if (item.getCurrentStock().compareTo(item.getReorderThreshold()) <= 0) notifyLowStock(item);
      consumption.save(new StockConsumption(tenantId, item.getId(), used, "recipe_deduction", "order_item", orderItemId, null, null));
    }
  }

  @Transactional
  public void deductRoomPar(UUID tenantId, String rawRoomType, UUID roomId) {
    if (!entitlements.isActive(tenantId, ModuleType.INVENTORY)) return;
    String type = roomType(rawRoomType);
    for (RoomParItem row : roomPar.findAllByTenantIdAndRoomTypeOrderById(tenantId, type)) {
      InventoryItem item = items.lockByTenantIdAndId(tenantId, row.getInventoryItemId()).orElse(null);
      if (item == null || !item.isActive()) continue;
      BigDecimal before = item.getCurrentStock();
      item.changeStock(row.getQuantityPerClean().negate());
      if (before.compareTo(item.getReorderThreshold()) > 0 && item.getCurrentStock().compareTo(item.getReorderThreshold()) <= 0) notifyLowStock(item);
      consumption.save(new StockConsumption(tenantId, item.getId(), row.getQuantityPerClean(), "room_par_deduction", "room", roomId, null, null));
    }
  }

  private InventoryItem ownedItem(UUID id, boolean lock) {
    return (lock ? items.lockByTenantIdAndId(tenant.tenantId(), id) : items.findByTenantIdAndId(tenant.tenantId(), id))
        .orElseThrow(() -> new NoSuchElementException("Inventory item not found"));
  }
  private void notifyLowStock(InventoryItem item) {
    notifications.lowStock(item.getTenantId(), item.getId(), item.getName(),
        item.getCurrentStock() + " " + item.getUnit(), item.getReorderThreshold() + " " + item.getUnit());
  }
  private static void validateItem(String name, String unit, String usageType, BigDecimal threshold) {
    if (name == null || name.isBlank() || name.trim().length() > 150) throw new IllegalArgumentException("Item name is required and must be at most 150 characters");
    if (unit == null || unit.isBlank() || unit.trim().length() > 20) throw new IllegalArgumentException("Unit is required and must be at most 20 characters");
    if (usageType == null || !Set.of("restaurant", "room", "other").contains(usageType)) throw new IllegalArgumentException("Usage type must be restaurant, room, or other");
    if (threshold == null || threshold.signum() < 0 || threshold.scale() > 3) throw new IllegalArgumentException("Reorder threshold must be zero or greater with at most three decimals");
  }
  private static void requirePositiveQuantity(BigDecimal value, String label) {
    if (value == null || value.signum() <= 0 || value.scale() > 3) throw new IllegalArgumentException(label + " must be greater than zero with at most three decimals");
  }
  private static String roomType(String value) {
    try { return RoomType.valueOf(value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT)).name(); }
    catch (RuntimeException exception) { throw new IllegalArgumentException("Unknown room type: " + value); }
  }
  public record IngredientRequest(UUID inventoryItemId, BigDecimal quantity) {}
  public record PurchaseResult(StockPurchase purchase, BigDecimal averageUnitCost, UUID expenseId) {}
}
