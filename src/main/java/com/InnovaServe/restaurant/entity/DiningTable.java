package com.InnovaServe.restaurant.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "dining_table", schema = "restaurant")
public class DiningTable extends TenantEntity {
  @Column(name = "table_number", nullable = false, length = 10)
  private String tableNumber;

  @Column(length = 50)
  private String section;

  @Column(nullable = false, length = 32)
  private String station = "kitchen";

  @Column(nullable = false, length = 10)
  private String status;

  protected DiningTable() {}

  public DiningTable(UUID tenantId, String tableNumber, String section) {
    super(tenantId);
    this.tableNumber = tableNumber;
    this.section = section;
    this.status = "free";
  }

  public String getTableNumber() {
    return tableNumber;
  }

  public String getSection() { return section; }

  public String getStation() { return station; }

  public void assignStation(String station) { this.station = station; }

  public String getStatus() {
    return status;
  }

  public void setStatus(String s) {
    status = s;
  }

  public void updateDetails(String number, String section) {
    this.tableNumber = number;
    this.section = section;
  }

  public void markDirty() {
    if (!"billed".equals(status) && !"free".equals(status))
      throw new IllegalStateException("Only vacant or billed tables can be marked dirty");
    status = "dirty";
  }

  public void markReady() {
    if (!"dirty".equals(status))
      throw new IllegalStateException("Only dirty tables can be marked ready");
    status = "free";
  }

  public void markVacant() {
    if (!"occupied".equals(status))
      throw new IllegalStateException("Only occupied tables can be marked vacant");
    status = "free";
  }
}
