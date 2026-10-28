package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurant_order_feedback", schema = "restaurant")
public class RestaurantOrderFeedback extends TenantEntity {
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "guest_session_id", nullable = false)
  private UUID guestSessionId;

  @Column(nullable = false, length = 1000)
  private String feedback;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  protected RestaurantOrderFeedback() {}

  public RestaurantOrderFeedback(UUID tenantId, UUID orderId, UUID guestSessionId, String feedback) {
    super(tenantId);
    this.orderId = orderId;
    this.guestSessionId = guestSessionId;
    this.feedback = feedback;
  }

  public UUID getOrderId() { return orderId; }
  public UUID getGuestSessionId() { return guestSessionId; }
  public String getFeedback() { return feedback; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
