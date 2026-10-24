package com.InnovaServe.restaurant.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.restaurant.entity.*;
import com.InnovaServe.restaurant.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StationOrderFlowTest {
  private final UUID tenantId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID tableId = UUID.randomUUID();
  private final TenantContext tenant = mock(TenantContext.class);
  private final MenuItemRepository menu = mock(MenuItemRepository.class);
  private final DiningTableRepository tables = mock(DiningTableRepository.class);
  private final RestaurantOrderRepository orders = mock(RestaurantOrderRepository.class);
  private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
  private final KotBatchRepository batches = mock(KotBatchRepository.class);
  private final RestaurantBillRepository bills = mock(RestaurantBillRepository.class);
  private final RestaurantStationService stations = mock(RestaurantStationService.class);
  private final RestaurantService service = new RestaurantService(
      tenant, null, null, menu, tables, orders, orderItems, batches, bills,
      null, null, null, null, null, null, null, null, stations);

  private void order(String station) {
    when(tenant.tenantId()).thenReturn(tenantId);
    RestaurantOrder order = new RestaurantOrder(tenantId, "dine_in", tableId, null, "staff", false, null);
    order.assignServiceStation(station);
    ReflectionTestUtils.setField(order, "id", orderId);
    when(orders.lockByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
  }

  private UUID menuItem(String station) {
    UUID id = UUID.randomUUID();
    MenuItem item = new MenuItem(tenantId, UUID.randomUUID(), station + " item", null,
        BigDecimal.TEN, null, station, true, "VEG");
    ReflectionTestUtils.setField(item, "id", id);
    when(menu.findByTenantIdAndId(tenantId, id)).thenReturn(Optional.of(item));
    return id;
  }

  @Test
  void stationAllowsOwnItemsAndKitchenButRejectsOtherStations() {
    assertTrue(StationOrderingPolicy.allows("bar", "kitchen"));
    assertTrue(StationOrderingPolicy.allows("bar", "bar"));
    assertTrue(StationOrderingPolicy.allows("dessert", "dessert"));
    assertTrue(StationOrderingPolicy.allows("dessert", "kitchen"));
    assertFalse(StationOrderingPolicy.allows("kitchen", "bar"));
    assertFalse(StationOrderingPolicy.allows("bar", "dessert"));
  }

  @Test
  void restaurantCannotAddBarItemsThroughTheApi() {
    order("kitchen");
    UUID drink = menuItem("bar");
    assertThrows(IllegalArgumentException.class, () -> service.addItems(orderId,
        List.of(new RestaurantService.ItemRequest(drink, (short) 1, null))));
    verify(orderItems, never()).save(any());
    verify(batches, never()).save(any());
  }

  @Test
  void barFoodAndDrinksProduceSeparateTicketsForOneExistingOrder() {
    order("bar");
    UUID food = menuItem("kitchen");
    UUID drink = menuItem("bar");
    when(orderItems.save(any(OrderItem.class))).thenAnswer(invocation -> {
      OrderItem item = invocation.getArgument(0);
      ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
      return item;
    });
    when(batches.save(any(KotBatch.class))).thenAnswer(invocation -> {
      KotBatch batch = invocation.getArgument(0);
      ReflectionTestUtils.setField(batch, "id", UUID.randomUUID());
      return batch;
    });
    Map<String, Object> result = service.addItems(orderId, List.of(
        new RestaurantService.ItemRequest(food, (short) 2, "Less spice"),
        new RestaurantService.ItemRequest(drink, (short) 1, "No ice")));
    List<?> tickets = (List<?>) result.get("kot_batches");
    assertEquals(2, tickets.size());
    assertEquals(Set.of("kitchen", "bar"), tickets.stream()
        .map(ticket -> ((KotBatch) ticket).getStation()).collect(java.util.stream.Collectors.toSet()));
    assertTrue(tickets.stream().allMatch(ticket -> ((KotBatch) ticket).getOrderId().equals(orderId)));
    verify(orders, never()).save(any());
    verify(bills, never()).save(any());
  }

  @Test
  void tableReassignmentDoesNotChangeItsPreviousOrderStation() {
    when(tenant.tenantId()).thenReturn(tenantId);
    DiningTable table = new DiningTable(tenantId, "B1", "Lounge");
    table.assignStation("bar");
    when(tables.lockByTenantIdAndId(tenantId, tableId)).thenReturn(Optional.of(table));
    when(orders.save(any(RestaurantOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    RestaurantOrder opened = service.openOrder("dine_in", tableId, null);
    assertEquals("bar", opened.getServiceStation());
    assertEquals("occupied", table.getStatus());
    table.setStatus("free");
    service.updateTable(tableId, "B1", "Main", "kitchen");
    assertEquals("kitchen", table.getStation());
    assertEquals("bar", opened.getServiceStation());
  }

  @Test
  void occupiedTableCannotBeReassigned() {
    when(tenant.tenantId()).thenReturn(tenantId);
    DiningTable table = new DiningTable(tenantId, "R1", "Main");
    table.setStatus("occupied");
    when(tables.lockByTenantIdAndId(tenantId, tableId)).thenReturn(Optional.of(table));
    assertThrows(IllegalStateException.class, () -> service.updateTable(tableId, "R1", "Main", "bar"));
    assertEquals("kitchen", table.getStation());
  }
}
