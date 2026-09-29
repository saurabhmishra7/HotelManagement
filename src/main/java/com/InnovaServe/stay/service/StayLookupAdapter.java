package com.InnovaServe.stay.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.contracts.ModuleDeactivationGuard;
import com.InnovaServe.core.repository.CustomerRepository;
import com.InnovaServe.stay.entity.Stay;
import com.InnovaServe.stay.repository.RoomRepository;
import com.InnovaServe.stay.repository.StayChargeRepository;
import com.InnovaServe.stay.repository.StayRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
  public List<StaySummary> findAllByCustomerId(UUID tenantId, UUID customerId) {
    return stays.findAllByTenantIdAndCustomerIdOrderByCheckInAtDesc(tenantId, customerId).stream()
        .map(stay -> summary(tenantId, stay))
        .toList();
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
