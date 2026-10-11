package com.InnovaServe.stay.entity;

import com.InnovaServe.core.entity.TenantEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_service_request", schema = "stay")
public class RoomServiceRequest extends TenantEntity {
  @Column(name = "room_id", nullable = false) private UUID roomId;
  @Column(name = "stay_id", nullable = false) private UUID stayId;
  @Column(name = "guest_session_id", nullable = false) private UUID guestSessionId;
  @Column(name = "room_number", nullable = false, length = 10) private String roomNumber;
  @Column(name = "service_type", nullable = false, length = 20) private String serviceType;
  @Column(length = 200) private String notes;
  @Column(nullable = false, length = 12) private String status = "pending";
  @Column(name = "requested_at", nullable = false) private Instant requestedAt = Instant.now();
  @Column(name = "attendant_confirmed_at") private Instant attendantConfirmedAt;
  @Column(name = "attendant_confirmed_by") private UUID attendantConfirmedBy;
  @Column(name = "guest_confirmed_at") private Instant guestConfirmedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "completed_by") private UUID completedBy;

  protected RoomServiceRequest() {}

  public RoomServiceRequest(UUID tenantId, UUID roomId, UUID stayId, UUID guestSessionId,
      String roomNumber, String serviceType, String notes) {
    super(tenantId);
    this.roomId = roomId;
    this.stayId = stayId;
    this.guestSessionId = guestSessionId;
    this.roomNumber = roomNumber;
    this.serviceType = serviceType;
    this.notes = notes;
  }

  public UUID getRoomId() { return roomId; }
  public UUID getStayId() { return stayId; }
  public UUID getGuestSessionId() { return guestSessionId; }
  public String getRoomNumber() { return roomNumber; }
  public String getServiceType() { return serviceType; }
  public String getNotes() { return notes; }
  public String getStatus() { return status; }
  public Instant getRequestedAt() { return requestedAt; }
  public Instant getAttendantConfirmedAt() { return attendantConfirmedAt; }
  public Instant getGuestConfirmedAt() { return guestConfirmedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public UUID getCompletedBy() { return completedBy; }

  public void confirmAttendant(UUID userId) {
    if (!"pending".equals(status) || attendantConfirmedAt != null) return;
    attendantConfirmedAt = Instant.now();
    attendantConfirmedBy = userId;
    finishWhenConfirmed();
  }

  public void confirmGuest() {
    if (!"pending".equals(status) || guestConfirmedAt != null) return;
    guestConfirmedAt = Instant.now();
    finishWhenConfirmed();
  }

  private void finishWhenConfirmed() {
    if (attendantConfirmedAt == null || guestConfirmedAt == null) return;
    status = "completed";
    completedAt = Instant.now();
    completedBy = attendantConfirmedBy;
  }
}
