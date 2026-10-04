package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
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

  @Column(name = "placed_at", insertable = false, updatable = false)
  private LocalDateTime placedAt;

  @Column(name = "preparing_at")
  private LocalDateTime preparingAt;

  @Column(name = "served_at")
  private LocalDateTime servedAt;

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

  public LocalDateTime getPlacedAt() {
    return placedAt;
  }

  public LocalDateTime getPreparingAt() {
    return preparingAt;
  }

  public LocalDateTime getServedAt() {
    return servedAt;
  }

  public void send(UUID batch) {
    status = "sent";
    kotBatchId = batch;
  }

  public void markPreparing() {
    if (!"sent".equals(status))
      throw new IllegalStateException("Only sent order items can be marked preparing");
    status = "preparing";
    preparingAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public void markServed() {
    if (!"preparing".equals(status))
      throw new IllegalStateException("Only preparing order items can be marked served");
    status = "served";
    servedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  public void reject(String reason) {
    status = "cancelled";
    notes = reason;
  }
}
