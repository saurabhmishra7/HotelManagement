package com.InnovaServe.stay.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.contracts.ModuleDeactivationGuard;
import com.InnovaServe.core.repository.CustomerRepository;
import com.InnovaServe.stay.entity.Stay;
import com.InnovaServe.stay.repository.RoomRepository;
import com.InnovaServe.stay.repository.StayChargeRepository;
import com.InnovaServe.stay.repository.StayRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class StayLookupAdapter implements StayLookupPort, ModuleDeactivationGuard {
  private final StayRepository stays;
  private final RoomRepository rooms;
  private final StayChargeRepository charges;
  private final CustomerRepository customers;

  public StayLookupAdapter(
      StayRepository stays,
      RoomRepository rooms,
      StayChargeRepository charges,
      CustomerRepository customers) {
    this.stays = stays;
    this.rooms = rooms;
    this.charges = charges;
    this.customers = customers;
  }

  @Override
  public Optional<StaySummary> findById(UUID tenantId, UUID stayId) {
    return stays.findByTenantIdAndId(tenantId, stayId).map(stay -> summary(tenantId, stay));
  }

  @Override
  public Optional<StaySummary> findActiveByRoomNumber(UUID tenantId, String roomNumber) {
    return rooms.findAllByTenantIdOrderByRoomNumber(tenantId).stream()
        .filter(room -> room.getRoomNumber().equals(roomNumber))
        .findFirst()
        .flatMap(
            room ->
                stays
                    .findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
                        tenantId, room.getId(), "active"))
        .map(stay -> summary(tenantId, stay));
  }

  @Override
  public Optional<StaySummary> findActiveByRoomId(UUID tenantId, UUID roomId) {
    return stays
        .findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(tenantId, roomId, "active")
        .map(stay -> summary(tenantId, stay));
  }

  @Override
  public List<RoomServiceOption> findActiveRoomServiceOptions(UUID tenantId) {
    var roomNumbers =
        rooms.findAllByTenantIdOrderByRoomNumber(tenantId).stream()
            .collect(Collectors.toMap(
                com.InnovaServe.stay.entity.Room::getId,
                com.InnovaServe.stay.entity.Room::getRoomNumber));
    List<Stay> activeStays = stays.findAllByTenantIdAndStatusOrderByCheckInAtDesc(tenantId, "active");
    Map<UUID, Stay> stayByRoomId = new java.util.LinkedHashMap<>();
    activeStays.stream()
        .filter(stay -> roomNumbers.containsKey(stay.getRoomId()))
        .forEach(stay -> stayByRoomId.putIfAbsent(stay.getRoomId(), stay));
    var customerIds =
        stayByRoomId.values().stream().map(Stay::getCustomerId).collect(Collectors.toSet());
    if (customerIds.isEmpty()) return List.of();
    Map<UUID, String> guestNames = customers.findAllByTenantIdAndIdIn(tenantId, customerIds)
        .stream()
        .collect(Collectors.toMap(
            com.InnovaServe.core.entity.Customer::getId,
            com.InnovaServe.core.entity.Customer::getName));
    return stayByRoomId.values().stream()
        .map(stay -> new RoomServiceOption(
            stay.getId(),
            stay.getAccountId(),
            roomNumbers.get(stay.getRoomId()),
            guestNames.getOrDefault(stay.getCustomerId(), "")))
        .toList();
  }

  @Override
  public List<StaySummary> findAllByCustomerId(UUID tenantId, UUID customerId) {
    return stays.findAllByTenantIdAndCustomerIdOrderByCheckInAtDesc(tenantId, customerId).stream()
        .map(stay -> summary(tenantId, stay))
        .toList();
  }

  @Override
  public List<CustomerStayStats> findCustomerStayStats(
      UUID tenantId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) return List.of();
    return stays.findCustomerStayStats(tenantId, customerIds).stream()
        .map(row -> new CustomerStayStats(row.getCustomerId(), row.getStayCount()))
        .toList();
  }

  @Override
  public List<CustomerStayRecord> findCustomerStayHistory(
      UUID tenantId, Collection<UUID> customerIds) {
    if (customerIds.isEmpty()) return List.of();
    return stays.findAllByTenantIdAndCustomerIdInOrderByCheckInAtDesc(tenantId, customerIds).stream()
        .map(stay -> new CustomerStayRecord(
            stay.getCustomerId(), stay.getId(), stay.getCheckInAt(), stay.getActualCheckOutAt()))
        .toList();
  }

  @Override
  public CustomerIdPage findCustomerIdsByStayHistory(
      UUID tenantId, boolean hasHotelStay, int page, int size) {
    var customerPage = hasHotelStay
        ? stays.findCustomerIdsWithHotelStays(tenantId, PageRequest.of(page, size))
        : stays.findCustomerIdsWithoutHotelStays(tenantId, PageRequest.of(page, size));
    return new CustomerIdPage(
        customerPage.getContent(), customerPage.getTotalElements(), customerPage.getTotalPages());
  }

  @Override
  public List<StayChargeSummary> findCharges(UUID tenantId, UUID stayId) {
    return charges.findAllByTenantIdAndStayId(tenantId, stayId).stream()
        .map(charge -> new StayChargeSummary(charge.getId(), charge.getDescription(), charge.getAmount()))
        .toList();
  }

  @Override
  public Optional<String> findCustomerName(UUID tenantId, UUID customerId) {
    return customers.findByTenantIdAndId(tenantId, customerId).map(customer -> customer.getName());
  }

  @Override
  public com.InnovaServe.core.security.ModuleType module() {
    return com.InnovaServe.core.security.ModuleType.STAY;
  }

  @Override
  public Optional<String> blockingReason(UUID tenantId) {
    return stays.existsByTenantIdAndStatus(tenantId, "active")
        ? Optional.of("Check out all active stays before disabling the Stay module")
        : Optional.empty();
  }

  private StaySummary summary(UUID tenantId, Stay stay) {
    String guestName =
        customers
            .findByTenantIdAndId(tenantId, stay.getCustomerId())
            .map(customer -> customer.getName())
            .orElse("");
    return new StaySummary(
        stay.getId(),
        stay.getAccountId(),
        stay.getCustomerId(),
        stay.getRoomId(),
        stay.getStatus(),
        guestName);
  }
}
