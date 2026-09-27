package com.InnovaServe.api;
import com.InnovaServe.restaurant.entity.*;import com.InnovaServe.restaurant.service.RestaurantService;import com.fasterxml.jackson.annotation.JsonProperty;import org.springframework.web.bind.annotation.*;import java.util.*;import java.math.BigDecimal;
@RestController @RequestMapping("/api/v1")public class RestaurantController{
 private final RestaurantService service;public RestaurantController(RestaurantService service){this.service=service;}
 @GetMapping("/menu-categories")public List<MenuCategory> categories(){return service.categories();}
 @PostMapping("/menu-categories")public MenuCategory createCategory(@RequestBody CategoryRequest r){return service.addCategory(r.name(),r.sortOrder()==null?0:r.sortOrder());}
 @GetMapping("/menu-items")public List<MenuItem> items(@RequestParam(name="category_id",required=false)UUID category,@RequestParam(required=false)Boolean active){return service.items(category,active);}
 @PostMapping("/menu-items")public MenuItem createItem(@RequestBody MenuItemRequest r){return service.addItem(r.categoryId(),r.name(),r.price(),r.taxRuleId(),r.station(),r.vegFlag()==null||r.vegFlag());}
 @PatchMapping("/menu-items/{id}")public MenuItem updateItem(@PathVariable UUID id,@RequestBody MenuItemUpdate r){return service.updateItem(id,r.price(),r.active());}
 @GetMapping("/dining-tables")public List<DiningTable> tables(){return service.tables();}
 @PostMapping("/orders")public Map<String,Object> openOrder(@RequestBody OpenOrder r){RestaurantOrder o=service.openOrder(r.orderType(),r.tableId(),r.stayId());return Map.of("order_id",o.getId());}
 @PostMapping("/orders/{id}/items")public Map<String,Object> addItems(@PathVariable UUID id,@RequestBody ItemsRequest r){return service.addItems(id,r.items().stream().map(x->new RestaurantService.ItemRequest(x.menuItemId(),x.quantity(),x.notes())).toList());}
 @GetMapping("/orders/{id}")public Map<String,Object> getOrder(@PathVariable UUID id){return Map.of("order",service.getOrder(id),"items",service.orderItems(id));}
 @PostMapping("/orders/{id}/bill")public Map<String,Object> bill(@PathVariable UUID id){return service.generateBill(id);}
 @PostMapping("/bills/{id}/settle")public Map<String,String> settle(@PathVariable UUID id,@RequestBody SettleRequest r){return Map.of("status",service.settleBill(id,r.settlementMode(),r.roomNumber()));}
 @GetMapping("/orders/pending-guest")public List<RestaurantOrder> pendingGuest(){return service.pendingGuestOrders();}
 @GetMapping("/kot-batches/{id}")public Map<String,Object> kot(@PathVariable UUID id){KotBatch batch=service.kot(id);return Map.of("batch",batch,"items",service.itemsForBatch(id));}
 @PostMapping("/kot-batches/{id}/mark-printed")public Map<String,Object> printed(@PathVariable UUID id){return Map.of("printed_at",service.markPrinted(id).getPrintedAt());}
 public record CategoryRequest(String name,@JsonProperty("sort_order")Integer sortOrder){}
 public record MenuItemRequest(@JsonProperty("category_id")UUID categoryId,String name,BigDecimal price,@JsonProperty("tax_rule_id")UUID taxRuleId,String station,@JsonProperty("veg_flag")Boolean vegFlag){}
 public record MenuItemUpdate(BigDecimal price,Boolean active){}
 public record OpenOrder(@JsonProperty("order_type")String orderType,@JsonProperty("table_id")UUID tableId,@JsonProperty("stay_id")UUID stayId){}
 public record ItemRequest(@JsonProperty("menu_item_id")UUID menuItemId,short quantity,String notes){}
 public record ItemsRequest(List<ItemRequest> items){}
 public record SettleRequest(@JsonProperty("settlement_mode")String settlementMode,@JsonProperty("room_number")String roomNumber){}
}
