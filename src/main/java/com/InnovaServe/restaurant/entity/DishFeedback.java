package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "dish_feedback", schema = "restaurant")
public class DishFeedback extends TenantEntity {
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "menu_item_id", nullable = false)
  private UUID menuItemId;

  @Column(name = "guest_session_id", nullable = false)
  private UUID guestSessionId;

  @Column(nullable = false, precision = 2, scale = 1)
  private BigDecimal stars;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected DishFeedback() {}

  public DishFeedback(UUID tenantId, UUID orderId, UUID menuItemId, UUID guestSessionId,
      BigDecimal stars) {
    super(tenantId);
    this.orderId = orderId;
    this.menuItemId = menuItemId;
    this.guestSessionId = guestSessionId;
    this.stars = stars;
  }

  public UUID getOrderId() { return orderId; }
  public UUID getMenuItemId() { return menuItemId; }
  public UUID getGuestSessionId() { return guestSessionId; }
  public BigDecimal getStars() { return stars; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
