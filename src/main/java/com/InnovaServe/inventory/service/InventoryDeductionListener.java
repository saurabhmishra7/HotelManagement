package com.InnovaServe.inventory.service;

import com.InnovaServe.inventory.event.OrderItemServedEvent;
import com.InnovaServe.inventory.event.RoomMarkedCleanEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InventoryDeductionListener {
  private final InventoryService inventory;
  public InventoryDeductionListener(InventoryService inventory) { this.inventory = inventory; }

  // Join the active transaction so stock, history, and alerts commit with the status change.
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onItemServed(OrderItemServedEvent event) {
    inventory.deductRecipe(event.tenantId(), event.menuItemId(), event.orderItemId(), event.quantity());
  }

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onRoomCleaned(RoomMarkedCleanEvent event) {
    inventory.deductRoomPar(event.tenantId(), event.roomType(), event.roomId());
  }
}
