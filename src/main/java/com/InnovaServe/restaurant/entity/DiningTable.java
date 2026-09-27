package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "dining_table", schema = "restaurant")
public class DiningTable extends TenantEntity {
  @Column(name = "table_number", nullable = false, length = 10)
  private String tableNumber;

  @Column(length = 50)
  private String section;

  @Column(nullable = false, length = 10)
  private String status;

  protected DiningTable() {}

  public String getTableNumber() {
    return tableNumber;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String s) {
    status = s;
  }
}
