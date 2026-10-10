package com.InnovaServe.inventory.controller;

import com.InnovaServe.core.security.*;
import com.InnovaServe.inventory.entity.InventoryItem;
import com.InnovaServe.inventory.service.InventoryService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiresModule(ModuleType.INVENTORY)
public class InventoryController {
  private final InventoryService service;
  public InventoryController(InventoryService service) { this.service = service; }

  @GetMapping("/items") @PreAuthorize("hasAuthority('PERM_INVENTORY_READ') or hasAuthority('PERM_INVENTORY_MANAGE')")
  public List<InventoryItem> items(@RequestParam(name="usage_type", required=false) String usageType, @RequestParam(required=false) Boolean active) { return service.items(usageType, active); }
  @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PERM_INVENTORY_MANAGE')")
  public InventoryItem create(@RequestBody ItemRequest r) { return service.createItem(r.name(), r.unit(), r.usageType(), r.reorderThreshold()); }
  @PatchMapping("/items/{id}") @PreAuthorize("hasAuthority('PERM_INVENTORY_MANAGE')")
  public InventoryItem update(@PathVariable UUID id, @RequestBody ItemRequest r) { return service.updateItem(id, r.name(), r.unit(), r.usageType(), r.reorderThreshold(), r.active()); }
  @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('PERM_INVENTORY_MANAGE')")
  public void delete(@PathVariable UUID id) { service.deleteItem(id); }
  @PostMapping("/items/{id}/purchase") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PERM_INVENTORY_MANAGE')")
  public Map<String,Object> purchase(@PathVariable UUID id, @RequestBody PurchaseRequest r) { return Map.of("purchase_result", service.purchase(id, r.quantity(), r.unitCost(), r.vendorName(), r.paymentMode())); }
  @PostMapping("/items/{id}/adjust") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('PERM_INVENTORY_MANAGE')")
  public Map<String,Object> adjust(@PathVariable UUID id, @RequestBody AdjustRequest r) { return Map.of("consumption", service.adjust(id, r.quantity(), r.note())); }
  @GetMapping("/items/{id}/history") @PreAuthorize("hasAuthority('PERM_INVENTORY_READ') or hasAuthority('PERM_INVENTORY_MANAGE')")
  public List<Map<String,Object>> history(@PathVariable UUID id) { return service.history(id); }
  @GetMapping("/low-stock") @PreAuthorize("hasAuthority('PERM_INVENTORY_READ') or hasAuthority('PERM_INVENTORY_MANAGE')")
  public List<InventoryItem> lowStock() { return service.lowStock(); }

  public record ItemRequest(String name, String unit, @JsonProperty("usage_type") String usageType,
      @JsonProperty("reorder_threshold") BigDecimal reorderThreshold,
      Boolean active) {}
  public record PurchaseRequest(BigDecimal quantity, @JsonProperty("unit_cost") BigDecimal unitCost,
      @JsonProperty("vendor_name") String vendorName, @JsonProperty("payment_mode") String paymentMode) {}
  public record AdjustRequest(BigDecimal quantity, String note) {}
}
