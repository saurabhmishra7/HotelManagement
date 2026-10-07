package com.InnovaServe.contracts;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.UUID;

/** Narrow Stay-module contract for optional integrations from other modules. */
public interface StayLookupPort {
  Optional<StaySummary> findById(UUID tenantId, UUID stayId);

  Optional<StaySummary> findActiveByRoomNumber(UUID tenantId, String roomNumber);

  Optional<StaySummary> findActiveByRoomId(UUID tenantId, UUID roomId);

  List<RoomServiceOption> findActiveRoomServiceOptions(UUID tenantId);

  List<StaySummary> findAllByCustomerId(UUID tenantId, UUID customerId);

  List<CustomerStayStats> findCustomerStayStats(UUID tenantId, Collection<UUID> customerIds);

  List<CustomerStayRecord> findCustomerStayHistory(UUID tenantId, Collection<UUID> customerIds);

  CustomerIdPage findCustomerIdsByStayHistory(UUID tenantId, boolean hasHotelStay, int page, int size);

  List<StayChargeSummary> findCharges(UUID tenantId, UUID stayId);

  Optional<String> findCustomerName(UUID tenantId, UUID customerId);

  record StaySummary(
      UUID id, UUID accountId, UUID customerId, UUID roomId, String status, String guestName) {}

  record RoomServiceOption(UUID stayId, UUID accountId, String roomNumber, String guestName) {}

  record StayChargeSummary(UUID id, String description, BigDecimal amount) {}

  record CustomerStayStats(UUID customerId, long stayCount) {}

  record CustomerStayRecord(
      UUID customerId, UUID stayId, LocalDateTime checkInAt, LocalDateTime actualCheckOutAt) {}

  record CustomerIdPage(List<UUID> customerIds, long totalElements, int totalPages) {}
}
