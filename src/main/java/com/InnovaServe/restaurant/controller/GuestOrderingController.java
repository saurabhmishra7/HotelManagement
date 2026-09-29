package com.InnovaServe.restaurant.controller;

import com.InnovaServe.restaurant.service.GuestOrderingService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/guest/qr/{token}")
public class GuestOrderingController {
  private final GuestOrderingService guestOrdering;

  public GuestOrderingController(GuestOrderingService guestOrdering) {
    this.guestOrdering = guestOrdering;
  }

  @GetMapping
  public Map<String, Object> menu(@PathVariable String token) {
    return guestOrdering.resolveMenu(token);
  }

  @PostMapping("/orders")
  public Map<String, Object> submitOrder(
      @PathVariable String token, @RequestBody GuestOrderRequest request) {
    if (request.items() == null)
      throw new IllegalArgumentException("items is required");
    if (request.items().stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("items cannot contain null entries");
    return guestOrdering.submitOrder(
        token,
        request.items().stream()
            .map(
                item ->
                    new GuestOrderingService.GuestItem(
                        item.menuItemId(), item.quantity(), item.notes()))
            .toList());
  }

  @GetMapping("/orders/{orderId}")
  public Map<String, Object> order(
      @PathVariable String token, @PathVariable UUID orderId) {
    return guestOrdering.order(token, orderId);
  }

  @PostMapping("/orders/{orderId}/request-bill")
  public Map<String, Object> requestBill(
      @PathVariable String token, @PathVariable UUID orderId) {
    return guestOrdering.requestBill(token, orderId);
  }

  public record GuestOrderRequest(List<GuestItemRequest> items) {}

  public record GuestItemRequest(
      @JsonProperty("menu_item_id") UUID menuItemId, short quantity, String notes) {}
}
