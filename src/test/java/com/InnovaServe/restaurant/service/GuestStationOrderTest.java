package com.InnovaServe.restaurant.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.*;
import com.InnovaServe.platform.service.BarFeatureService;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class GuestStationOrderTest {
  private final UUID tenantId = UUID.randomUUID();
  private final UUID tableId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();
  private final UUID categoryId = UUID.randomUUID();
  private final QRCodeRepository qr = mock(QRCodeRepository.class);
  private final TenantRepository tenants = mock(TenantRepository.class);
  private final MenuCategoryRepository categories = mock(MenuCategoryRepository.class);
  private final MenuItemRepository menu = mock(MenuItemRepository.class);
  private final DiningTableRepository tables = mock(DiningTableRepository.class);
  private final RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
  private final RestaurantBillRepository bills = mock(RestaurantBillRepository.class);
  private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
  private final ModuleEntitlementService entitlements = mock(ModuleEntitlementService.class);
  private final BarFeatureService barFeature = mock(BarFeatureService.class);
  private final RestaurantStationService stations = new RestaurantStationService(
      new TenantContext(), mock(RestaurantStationRepository.class), menu,
      mock(KotBatchRepository.class), barFeature, tables);
  private final GuestOrderingService service = new GuestOrderingService(qr, tenants, categories,
      menu, tables, orders, bills, orderItems, null, null, entitlements,
      mock(NotificationService.class), null, null, null, stations, mock(DishFeedbackService.class));
  private final DiningTable table = new DiningTable(tenantId, "B1", "Main");
  private final List<RestaurantOrder> savedOrders = new ArrayList<>();
  private MenuItem food;
  private MenuItem drink;

  private MenuItem item(String station) {
    MenuItem item = new MenuItem(tenantId, categoryId, station + " item", null,
        BigDecimal.TEN, null, station, true, "VEG");
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    when(menu.findByTenantIdAndId(tenantId, item.getId())).thenReturn(Optional.of(item));
    return item;
  }

  @BeforeEach
  void setup() {
    SecurityContextHolder.clearContext();
    ReflectionTestUtils.setField(table, "id", tableId);
    when(qr.findByTokenAndActiveTrue("test-qr")).thenReturn(Optional.of(
        new QRCode(tenantId, "dining_table", tableId, "test-qr")));
    when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Demo", null, "Main")));
    when(tables.findByTenantIdAndId(tenantId, tableId)).thenReturn(Optional.of(table));
    when(tables.lockByTenantIdAndId(tenantId, tableId)).thenReturn(Optional.of(table));
    when(entitlements.isActive(tenantId, ModuleType.RESTAURANT)).thenReturn(true);
    when(barFeature.enabled(tenantId)).thenReturn(true);
    food = item("kitchen");
    drink = item("bar");
    when(menu.findAllByTenantIdOrderByName(tenantId)).thenReturn(List.of(food, drink));
    when(orders.findAllByTenantIdAndGuestSessionIdOrderByCreatedAtAsc(tenantId, sessionId))
        .thenAnswer(invocation -> List.copyOf(savedOrders));
    when(orders.save(any(RestaurantOrder.class))).thenAnswer(invocation -> {
      RestaurantOrder order = invocation.getArgument(0);
      ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
      savedOrders.add(order);
      when(orders.lockByTenantIdAndId(tenantId, order.getId())).thenReturn(Optional.of(order));
      return order;
    });
  }

  @Test
  void restaurantQrMenuExcludesDrinksWithoutRequiringStaffLogin() {
    Map<String, Object> resolved = service.resolveMenu("test-qr");
    List<?> items = (List<?>) resolved.get("menu_items");
    assertEquals(1, items.size());
    assertEquals("kitchen", ((Map<?, ?>) items.get(0)).get("station"));
  }

  @Test
  void barQrMenuContainsFoodAndDrinksWithoutRequiringStaffLogin() {
    table.assignStation("bar");
    assertEquals(2, ((List<?>) service.resolveMenu("test-qr").get("menu_items")).size());
  }

  @Test
  void restaurantQrRejectsBarItemEvenIfSubmittedDirectly() {
    assertThrows(IllegalArgumentException.class, () -> service.submitOrder("test-qr", sessionId,
        List.of(new GuestOrderingService.GuestItem(drink.getId(), (short) 1, null))));
    verify(orderItems, never()).save(any());
  }

  @Test
  void barQrAppendsLaterFoodToTheSameOrder() {
    table.assignStation("bar");
    service.submitOrder("test-qr", sessionId,
        List.of(new GuestOrderingService.GuestItem(drink.getId(), (short) 1, "No ice")));
    service.submitOrder("test-qr", sessionId,
        List.of(new GuestOrderingService.GuestItem(food.getId(), (short) 2, "Less spice")));
    assertEquals(1, savedOrders.size());
    assertEquals("bar", savedOrders.get(0).getServiceStation());
    verify(orderItems, times(2)).save(argThat(item -> item.getOrderId().equals(savedOrders.get(0).getId())));
    verify(bills, never()).save(any());
  }
}
