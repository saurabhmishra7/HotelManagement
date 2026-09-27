package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kot_batch", schema = "restaurant")
public class KotBatch extends TenantEntity {
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(nullable = false, length = 10)
  private String station;

  @Column(name = "batch_number", nullable = false)
  private short batchNumber;

  @Column(name = "printed_at")
  private LocalDateTime printedAt;

  protected KotBatch() {}

  public KotBatch(UUID t, UUID order, String station, short number) {
    super(t);
    orderId = order;
    this.station = station;
    batchNumber = number;
  }

  public UUID getOrderId() {
    return orderId;
  }

  public String getStation() {
    return station;
  }

  public short getBatchNumber() {
    return batchNumber;
  }

  public LocalDateTime getPrintedAt() {
    return printedAt;
  }

  public void markPrinted() {
    printedAt = LocalDateTime.now();
  }
}
