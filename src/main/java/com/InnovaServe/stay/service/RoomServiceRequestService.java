package com.InnovaServe.stay.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.core.service.NotificationService;
import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.stay.entity.RoomServiceRequest;
import com.InnovaServe.stay.repository.RoomServiceRequestRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomServiceRequestService {
  private final RoomServiceRequestRepository requests;
  private final StayLookupPort stayLookup;
  private final NotificationService notifications;
  private final TenantContext context;

  public RoomServiceRequestService(RoomServiceRequestRepository requests, StayLookupPort stayLookup,
      NotificationService notifications, TenantContext context) {
    this.requests = requests;
    this.stayLookup = stayLookup;
    this.notifications = notifications;
    this.context = context;
  }

  @Transactional
  public View submit(UUID tenantId, UUID roomId, UUID guestSessionId, String serviceType, String notes) {
    String serviceName = switch (serviceType == null ? "" : serviceType) {
      case "cleaning" -> "room cleaning";
      case "towels" -> "fresh towels";
      case "water" -> "drinking water";
      case "other" -> "assistance";
      default -> throw new IllegalArgumentException("Choose a valid room service request");
    };
    String cleanNotes = notes == null ? null : notes.trim();
    if (cleanNotes != null && cleanNotes.length() > 200)
      throw new IllegalArgumentException("Notes must be at most 200 characters");
    UUID stayId = stayLookup.findActiveByRoomId(tenantId, roomId)
        .orElseThrow(() -> new IllegalStateException("No active stay is associated with this room")).id();
    String roomNumber = stayLookup.findRoomNumber(tenantId, roomId).orElseThrow();
    RoomServiceRequest request = requests.save(new RoomServiceRequest(tenantId, roomId, stayId,
        guestSessionId, roomNumber, serviceType, cleanNotes));
    notifications.roomServiceRequested(tenantId, request.getId(), roomNumber, serviceName, cleanNotes);
    return view(request);
  }

  @Transactional(readOnly = true)
  public List<View> forGuest(UUID tenantId, UUID guestSessionId) {
    return requests.findAllByTenantIdAndGuestSessionIdOrderByRequestedAtDesc(tenantId, guestSessionId)
        .stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public List<View> forStaff() {
    return requests.findAllByTenantIdOrderByRequestedAtDesc(context.tenantId()).stream()
        .map(this::view).toList();
  }

  @Transactional
  public View complete(UUID id) {
    UUID tenantId = context.tenantId();
    RoomServiceRequest request = requests.lockByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new NoSuchElementException("Room service request not found"));
    if (!"pending".equals(request.getStatus()) || request.getAttendantConfirmedAt() != null)
      return view(request);
    request.confirmAttendant(context.userId());
    notifications.roomServiceAttendantConfirmed(tenantId, request.getId());
    if ("completed".equals(request.getStatus()))
      notifications.roomServiceCompleted(tenantId, request.getId(), request.getRoomNumber(), request.getServiceType());
    return view(request);
  }

  @Transactional
  public View confirmGuest(UUID tenantId, UUID guestSessionId, UUID id) {
    RoomServiceRequest request = requests.lockByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new NoSuchElementException("Room service request not found"));
    if (!guestSessionId.equals(request.getGuestSessionId()))
      throw new NoSuchElementException("Room service request not found");
    if (!"pending".equals(request.getStatus()) || request.getGuestConfirmedAt() != null)
      return view(request);
    request.confirmGuest();
    if ("completed".equals(request.getStatus()))
      notifications.roomServiceCompleted(tenantId, request.getId(), request.getRoomNumber(), request.getServiceType());
    return view(request);
  }

  private View view(RoomServiceRequest request) {
    return new View(request.getId(), request.getRoomNumber(), request.getServiceType(),
        request.getNotes(), request.getStatus(), request.getRequestedAt(),
        request.getAttendantConfirmedAt(), request.getGuestConfirmedAt(), request.getCompletedAt());
  }

  public record View(UUID id, String room_number, String service_type, String notes,
      String status, Instant requested_at, Instant attendant_confirmed_at,
      Instant guest_confirmed_at, Instant completed_at) {}
}
