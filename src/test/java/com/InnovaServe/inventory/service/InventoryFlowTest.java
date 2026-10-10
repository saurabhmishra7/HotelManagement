package com.InnovaServe.inventory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.InnovaServe.inventory.event.*;
import com.InnovaServe.inventory.entity.*;
import com.InnovaServe.inventory.repository.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.*;
import com.InnovaServe.expense.service.ExpenseService;
import com.InnovaServe.restaurant.repository.MenuItemRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.*;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryFlowTest {
  private final UUID tenantId = UUID.randomUUID();
  private final UUID menuId = UUID.randomUUID();
  private final UUID orderItemId = UUID.randomUUID();

  @Test
  void servingDeductionRunsBeforePhysicalCommit() {
    var inventory = mock(InventoryService.class);
    var tx = new RecordingTransactions();
    try (var context = context(inventory)) {
      doAnswer(invocation -> {
        assertFalse(tx.committed, "deductions must happen while writes can still commit");
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        return null;
      }).when(inventory).deductRecipe(tenantId, menuId, orderItemId, (short) 2);
      new TransactionTemplate(tx).executeWithoutResult(status ->
          context.publishEvent(new OrderItemServedEvent(tenantId, menuId, orderItemId, (short) 2)));
      verify(inventory).deductRecipe(tenantId, menuId, orderItemId, (short) 2);
      assertTrue(tx.committed);
    }
  }

  @Test
  void deductionFailureRollsBackServingInsteadOfReportingSuccess() {
    var inventory = mock(InventoryService.class);
    var tx = new RecordingTransactions();
    try (var context = context(inventory)) {
      doThrow(new IllegalStateException("write failed")).when(inventory)
          .deductRecipe(tenantId, menuId, orderItemId, (short) 1);
      assertThrows(IllegalStateException.class, () -> new TransactionTemplate(tx).executeWithoutResult(status ->
          context.publishEvent(new OrderItemServedEvent(tenantId, menuId, orderItemId, (short) 1))));
      assertTrue(tx.rolledBack);
      assertFalse(tx.committed);
    }
  }

  @Test
  void rolledBackServeNeverDeducts() {
    var inventory = mock(InventoryService.class);
    var tx = new RecordingTransactions();
    try (var context = context(inventory)) {
      new TransactionTemplate(tx).executeWithoutResult(status -> {
        context.publishEvent(new OrderItemServedEvent(tenantId, menuId, orderItemId, (short) 1));
        status.setRollbackOnly();
      });
      verifyNoInteractions(inventory);
      assertTrue(tx.rolledBack);
    }
  }

  @Test
  void roomCleanUsesTheSameBeforeCommitBoundary() {
    var inventory = mock(InventoryService.class);
    var tx = new RecordingTransactions();
    UUID room = UUID.randomUUID();
    try (var context = context(inventory)) {
      doAnswer(invocation -> { assertFalse(tx.committed); return null; })
          .when(inventory).deductRoomPar(tenantId, "SINGLE", room);
      new TransactionTemplate(tx).executeWithoutResult(status ->
          context.publishEvent(new RoomMarkedCleanEvent(tenantId, room, "SINGLE")));
      verify(inventory).deductRoomPar(tenantId, "SINGLE", room);
      assertTrue(tx.committed);
    }
  }

  @Test
  void recipeMultipliesQuantityAndAlertsAtThresholdAndOnEveryFurtherServing() {
    var entitlements = mock(ModuleEntitlementService.class);
    var items = mock(InventoryItemRepository.class);
    var recipes = mock(RecipeRepository.class);
    var ingredients = mock(RecipeIngredientRepository.class);
    var consumption = mock(StockConsumptionRepository.class);
    var notifications = mock(NotificationService.class);
    var service = new InventoryService(mock(TenantContext.class), entitlements, items,
        mock(StockPurchaseRepository.class), consumption, recipes, ingredients,
        mock(RoomParItemRepository.class), mock(MenuItemRepository.class), mock(ExpenseService.class), notifications);
    UUID ingredientId = UUID.randomUUID();
    var item = new InventoryItem(tenantId, "Daal", "kg", "restaurant", new BigDecimal("1.000"));
    ReflectionTestUtils.setField(item, "id", ingredientId);
    item.changeStock(new BigDecimal("2.000"));
    var recipe = new Recipe(tenantId, menuId);
    ReflectionTestUtils.setField(recipe, "id", UUID.randomUUID());
    when(entitlements.isActive(tenantId, ModuleType.INVENTORY)).thenReturn(true);
    when(recipes.findByTenantIdAndMenuItemId(tenantId, menuId)).thenReturn(Optional.of(recipe));
    when(ingredients.findAllByRecipeId(recipe.getId())).thenReturn(List.of(
        new RecipeIngredient(recipe.getId(), ingredientId, new BigDecimal("0.250"))));
    when(items.lockByTenantIdAndId(tenantId, ingredientId)).thenReturn(Optional.of(item));

    service.deductRecipe(tenantId, menuId, orderItemId, (short) 2);
    assertEquals(new BigDecimal("1.500"), item.getCurrentStock());
    verifyNoInteractions(notifications);
    service.deductRecipe(tenantId, menuId, UUID.randomUUID(), (short) 2);
    assertEquals(new BigDecimal("1.000"), item.getCurrentStock());
    verify(notifications).lowStock(tenantId, ingredientId, "Daal", "1.000 kg", "1.000 kg");
    service.deductRecipe(tenantId, menuId, UUID.randomUUID(), (short) 1);
    assertEquals(new BigDecimal("0.750"), item.getCurrentStock());
    verify(notifications).lowStock(tenantId, ingredientId, "Daal", "0.750 kg", "1.000 kg");
    var captor = org.mockito.ArgumentCaptor.forClass(StockConsumption.class);
    verify(consumption, times(3)).save(captor.capture());
    assertEquals(new BigDecimal("0.500"), captor.getAllValues().getFirst().getQuantity());
    assertEquals(orderItemId, captor.getAllValues().getFirst().getReferenceId());
    assertEquals("recipe_deduction", captor.getAllValues().getFirst().getConsumptionType());
  }


  @Test
  void repeatedLowStockCreatesImportantAlertsForResponsibleUsersOnly() {
    var repository = mock(com.InnovaServe.core.repository.NotificationRepository.class);
    var users = mock(com.InnovaServe.core.repository.StaffUserRepository.class);
    var roles = mock(com.InnovaServe.core.repository.StaffRoleRepository.class);
    var service = new NotificationService(repository, users, roles,
        mock(com.InnovaServe.core.repository.TenantRepository.class), mock(TenantContext.class));
    var roleRows = new ArrayList<com.InnovaServe.core.entity.StaffRole>();
    var userRows = new ArrayList<com.InnovaServe.core.entity.StaffUser>();
    for (String name : List.of("OWNER", "CUSTOM_STOCK_MANAGER", "WAITER")) {
      var role = new com.InnovaServe.core.entity.StaffRole(tenantId, name,
          name.equals("CUSTOM_STOCK_MANAGER") ? List.of("INVENTORY_MANAGE") : List.of());
      ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
      var user = new com.InnovaServe.core.entity.StaffUser(tenantId, name, "0000000000",
          null, "unused", null, role.getId());
      ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
      roleRows.add(role);
      userRows.add(user);
    }
    when(roles.findAllByTenantIdOrderByName(tenantId)).thenReturn(roleRows);
    when(users.findAllByTenantIdAndActiveTrue(tenantId)).thenReturn(userRows);
    UUID stockItem = UUID.randomUUID();
    service.lowStock(tenantId, stockItem, "Daal", "1 kg", "1 kg");
    service.lowStock(tenantId, stockItem, "Daal", "0.75 kg", "1 kg");
    var captor = org.mockito.ArgumentCaptor.forClass(com.InnovaServe.core.entity.Notification.class);
    verify(repository, times(4)).save(captor.capture());
    var alerts = captor.getAllValues();
    for (var alert : alerts) {
      assertEquals(tenantId, alert.getTenantId());
      assertEquals("IMPORTANT", alert.getCategory());
      assertEquals("inventory", alert.getSource());
      assertNull(alert.getReadAt());
      assertNotEquals(userRows.get(2).getId(), alert.getRecipientUserId());
    }
    assertNotEquals(alerts.get(0).getSourceKey(), alerts.get(2).getSourceKey());
    assertTrue(alerts.get(2).getMessage().contains("0.75 kg"));
  }

  private AnnotationConfigApplicationContext context(InventoryService inventory) {
    var context = new AnnotationConfigApplicationContext();
    context.registerBean(TransactionalEventListenerFactory.class);
    context.registerBean(InventoryDeductionListener.class, () -> new InventoryDeductionListener(inventory));
    context.refresh();
    return context;
  }

  static class RecordingTransactions extends AbstractPlatformTransactionManager {
    boolean committed;
    boolean rolledBack;
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
    @Override protected void doCommit(DefaultTransactionStatus status) { committed = true; }
    @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
  }
}
