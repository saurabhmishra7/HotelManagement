package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "order_item", schema = "restaurant")
public class OrderItem extends TenantEntity {
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "menu_item_id", nullable = false)
  private UUID menuItemId;

  @Column(nullable = false)
  private short quantity;

  @Column(length = 200)
  private String notes;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "kot_batch_id")
  private UUID kotBatchId;

  protected OrderItem() {}

  public OrderItem(UUID t, UUID order, UUID item, short quantity, String notes, String status) {
    super(t);
    orderId = order;
    menuItemId = item;
    this.quantity = quantity;
    this.notes = notes;
    this.status = status;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public UUID getMenuItemId() {
    return menuItemId;
  }

  public short getQuantity() {
    return quantity;
  }

  public String getNotes() {
    return notes;
  }

  public String getStatus() {
    return status;
  }

  public UUID getKotBatchId() {
    return kotBatchId;
  }

  public void send(UUID batch) {
    status = "sent";
    kotBatchId = batch;
  }

  public void markPreparing() {
    if (!"sent".equals(status))
      throw new IllegalStateException("Only sent order items can be marked preparing");
    status = "preparing";
  }

  public void markServed() {
    if (!"preparing".equals(status))
      throw new IllegalStateException("Only preparing order items can be marked served");
    status = "served";
  }

  public void reject(String reason) {
    status = "cancelled";
    notes = reason;
  }
}
