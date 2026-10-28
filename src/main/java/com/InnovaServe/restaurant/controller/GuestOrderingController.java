package com.InnovaServe.restaurant.controller;

import com.InnovaServe.restaurant.service.GuestOrderingService;
import com.InnovaServe.restaurant.service.GuestQrSessionService;
import com.InnovaServe.restaurant.service.DishFeedbackService;
import com.InnovaServe.restaurant.entity.GuestQrSession;
import com.InnovaServe.stay.service.RoomServiceRequestService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/v1/guest/qr/{token}")
public class GuestOrderingController {
  private final GuestOrderingService guestOrdering;
  private final GuestQrSessionService sessions;
  private final RoomServiceRequestService roomServiceRequests;

  public GuestOrderingController(GuestOrderingService guestOrdering, GuestQrSessionService sessions,
      RoomServiceRequestService roomServiceRequests) {
    this.guestOrdering = guestOrdering;
    this.sessions = sessions;
    this.roomServiceRequests = roomServiceRequests;
  }

  @PostMapping("/session")
  public Map<String, Object> startSession(@PathVariable String token,
      @RequestBody(required = false) SessionRequest request) {
    GuestQrSessionService.Started started = sessions.start(token, request == null ? null : request.sessionToken());
    Map<String, Object> response = sessionView(started.session());
    response.put("session_token", started.token());
    if ("active".equals(started.session().getStatus())) response.putAll(guestOrdering.resolveMenu(token));
    return response;
  }

  @GetMapping("/session")
  public Map<String, Object> session(@PathVariable String token,
      @RequestHeader("X-Guest-Session") String sessionToken) {
    return sessionView(sessions.get(token, sessionToken));
  }

  @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
  public byte[] logo(@PathVariable String token) { return guestOrdering.logo(token); }

  private Map<String, Object> sessionView(GuestQrSession session) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("session_status", session.getStatus());
    view.put("target_type", session.getTargetType());
    view.put("closed_reason", session.getClosedReason());
    view.put("orders", guestOrdering.ordersForSession(session.getTenantId(), session.getId(),
        "dining_table".equals(session.getTargetType())));
    if ("room".equals(session.getTargetType()))
      view.put("service_requests", roomServiceRequests.forGuest(session.getTenantId(), session.getId()));
    return view;
  }

  @GetMapping
  public Map<String, Object> menu(@PathVariable String token) {
    return guestOrdering.resolveMenu(token);
  }

  @PostMapping("/orders")
  public Map<String, Object> submitOrder(
      @PathVariable String token, @RequestHeader("X-Guest-Session") String sessionToken,
      @RequestBody GuestOrderRequest request) {
    GuestQrSession session = sessions.requireActive(token, sessionToken);
    if (request.items() == null)
      throw new IllegalArgumentException("items is required");
    if (request.items().stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("items cannot contain null entries");
    return guestOrdering.submitOrder(
        token, session.getId(),
        request.items().stream()
            .map(
                item ->
                    new GuestOrderingService.GuestItem(
                        item.menuItemId(), item.quantity(), item.notes()))
            .toList());
  }

  @PostMapping("/service-requests")
  public RoomServiceRequestService.View serviceRequest(@PathVariable String token,
      @RequestHeader("X-Guest-Session") String sessionToken, @RequestBody ServiceRequest request) {
    GuestQrSession session = sessions.requireActive(token, sessionToken);
    return guestOrdering.submitServiceRequest(token, session.getId(), request.serviceType(), request.notes());
  }

  @PostMapping("/service-requests/{id}/confirm")
  public RoomServiceRequestService.View confirmServiceReceived(@PathVariable String token,
      @RequestHeader("X-Guest-Session") String sessionToken, @PathVariable UUID id) {
    GuestQrSession session = sessions.requireActive(token, sessionToken);
    if (!"room".equals(session.getTargetType()))
      throw new IllegalArgumentException("Room service confirmation requires a room QR code");
    return roomServiceRequests.confirmGuest(session.getTenantId(), session.getId(), id);
  }

  @GetMapping("/orders/{orderId}")
  public Map<String, Object> order(
      @PathVariable String token, @RequestHeader("X-Guest-Session") String sessionToken,
      @PathVariable UUID orderId) {
    GuestQrSession session = sessions.requireActive(token, sessionToken);
    return guestOrdering.order(token, session.getId(), orderId);
  }

  @PostMapping("/orders/{orderId}/request-bill")
  public Map<String, Object> requestBill(
      @PathVariable String token, @RequestHeader("X-Guest-Session") String sessionToken,
      @PathVariable UUID orderId, @RequestBody(required = false) BillRequest request) {
    GuestQrSession session = sessions.requireActive(token, sessionToken);
    List<DishFeedbackService.Rating> ratings = request == null || request.ratings() == null
        ? null : request.ratings().stream().map(row -> row == null ? null
            : new DishFeedbackService.Rating(row.menuItemId(), row.stars())).toList();
    return guestOrdering.requestBill(token, session.getId(), orderId, ratings,
        request == null ? null : request.feedback());
  }

  public record BillRequest(List<RatingRequest> ratings, String feedback) {}

  public record RatingRequest(@JsonProperty("menu_item_id") UUID menuItemId,
      BigDecimal stars) {}

  public record GuestOrderRequest(List<GuestItemRequest> items) {}

  public record SessionRequest(@JsonProperty("session_token") String sessionToken) {}

  public record ServiceRequest(@JsonProperty("service_type") String serviceType, String notes) {}

  public record GuestItemRequest(
      @JsonProperty("menu_item_id") UUID menuItemId, short quantity, String notes) {}
}
