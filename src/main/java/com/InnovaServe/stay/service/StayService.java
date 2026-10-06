package com.InnovaServe.stay.service;

import com.InnovaServe.core.entity.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.*;
import com.InnovaServe.stay.entity.*;
import com.InnovaServe.stay.enums.RoomType;
import com.InnovaServe.stay.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StayService {
  private final RoomRepository rooms;
  private final StayRepository stays;
  private final StayChargeRepository charges;
  private final FormCSubmissionRepository forms;
  private final AccountRepository accounts;
  private final CustomerService customers;
  private final CustomerRepository customerRepository;
  private final TenantContext tenant;
  private final BillingService billing;

  public StayService(
      RoomRepository rooms,
      StayRepository stays,
      StayChargeRepository charges,
      FormCSubmissionRepository forms,
      AccountRepository accounts,
      CustomerService customers,
      CustomerRepository customerRepository,
      TenantContext tenant,
      BillingService billing) {
    this.rooms = rooms;
    this.stays = stays;
    this.charges = charges;
    this.forms = forms;
    this.accounts = accounts;
    this.customers = customers;
    this.customerRepository = customerRepository;
    this.tenant = tenant;
    this.billing = billing;
  }

  public List<Room> rooms() {
    return rooms.findAllByTenantIdOrderByRoomNumber(tenant.tenantId());
  }

  @Transactional
  public Room createRoom(String roomNumber, RoomType roomType, String floor, BigDecimal baseTariff) {
    String normalizedRoomNumber = validateRoomDetails(roomNumber, roomType, floor, baseTariff);
    UUID tenantId = tenant.tenantId();
    if (rooms.existsByTenantIdAndRoomNumber(tenantId, normalizedRoomNumber)) {
      throw new IllegalArgumentException("Room number already exists");
    }

    return rooms.save(
        new Room(tenantId, normalizedRoomNumber, roomType.name(), floor, baseTariff, "vacant"));
  }

  @Transactional
  public Room updateRoom(
      UUID roomId, String roomNumber, RoomType roomType, String floor, BigDecimal baseTariff) {
    UUID tenantId = tenant.tenantId();
    Room room =
        rooms.findByTenantIdAndId(tenantId, roomId)
            .orElseThrow(() -> new NoSuchElementException("Room not found"));
    String normalizedRoomNumber = validateRoomDetails(roomNumber, roomType, floor, baseTariff);

    if (!normalizedRoomNumber.equals(room.getRoomNumber())
        && stays.findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
                tenantId, roomId, "active")
            .isPresent()) {
      throw new IllegalStateException("Room number cannot be changed while a guest is staying in it");
    }
    if (rooms.existsByTenantIdAndRoomNumberAndIdNot(tenantId, normalizedRoomNumber, roomId)) {
      throw new IllegalArgumentException("Room number already exists");
    }

    room.setRoomNumber(normalizedRoomNumber);
    room.setRoomType(roomType.name());
    room.setFloor(floor);
    room.setBaseTariff(baseTariff);
    return room;
  }

  private String validateRoomDetails(
      String roomNumber, RoomType roomType, String floor, BigDecimal baseTariff) {
    String normalizedRoomNumber = roomNumber == null ? "" : roomNumber.trim();
    if (normalizedRoomNumber.isBlank() || normalizedRoomNumber.length() > 10) {
      throw new IllegalArgumentException("Room number is required and must be at most 10 characters");
    }
    if (roomType == null) {
      throw new IllegalArgumentException("Room type is required");
    }
    if (floor != null && floor.length() > 10) {
      throw new IllegalArgumentException("Floor must be at most 10 characters");
    }
    if (baseTariff == null || baseTariff.signum() <= 0) {
      throw new IllegalArgumentException("Base tariff must be greater than zero");
    }
    return normalizedRoomNumber;
  }

  public Room room(UUID id) {
    return rooms
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Room not found"));
  }

  @Transactional
  public Room setRoomStatus(UUID id, String status) {
    Room r = room(id);
    r.setStatus(status);
    return r;
  }

  @Transactional
  public Stay checkIn(CheckIn r) {
    UUID tid = tenant.tenantId();
    Room room =
        rooms
            .lockByTenantIdAndId(tid, r.roomId())
            .orElseThrow(() -> new NoSuchElementException("Room not found"));
    if (!Set.of("vacant", "clean").contains(room.getStatus()))
      throw new IllegalStateException("RoomNotAvailable");
    CustomerService.CustomerResult c =
        customers.createOrUpdate(
            r.customer().name(),
            r.customer().phone(),
            r.customer().idProofType(),
            r.customer().idProofNumber(),
            r.customer().idProofTypeOther(),
            r.customer().address());
    Account account = accounts.save(new Account(tid, "stay", "stay", null));
    Stay stay =
        stays.save(
            new Stay(
                tid,
                room.getId(),
                c.customer().getId(),
                account.getId(),
                r.guestCount(),
                r.isForeignGuest(),
                r.plan() == null ? "EP" : r.plan(),
                r.tariff(),
                r.expectedCheckOutAt(),
                r.advancePaid() == null ? BigDecimal.ZERO : r.advancePaid()));
    account.setLinkedEntityId(stay.getId());
    room.setStatus("occupied");
    if (r.isForeignGuest()) forms.save(new FormCSubmission(tid, stay.getId()));
    return stay;
  }

  public Stay getStay(UUID id) {
    return stays
        .findByTenantIdAndId(tenant.tenantId(), id)
        .orElseThrow(() -> new NoSuchElementException("Stay not found"));
  }

  public Map<String, Object> stayDetails(UUID id) {
    Stay stay = getStay(id);
    Room room = room(stay.getRoomId());
    Customer customer = findCustomer(stay.getCustomerId());
    return Map.of("stay", stayView(stay, room, customer), "charges", charges(id));
  }

  private Customer findCustomer(UUID customerId) {
    return customerRepository
        .findByTenantIdAndId(tenant.tenantId(), customerId)
        .orElseThrow(() -> new NoSuchElementException("Guest not found"));
  }

  private Map<String, Object> stayView(Stay stay, Room room, Customer customer) {
    Map<String, Object> customerDetails = new LinkedHashMap<>();
    customerDetails.put("id", customer.getId());
    customerDetails.put("name", customer.getName());
    customerDetails.put("phone", customer.getPhone());
    customerDetails.put("idProofType", customer.getIdProofType());
    customerDetails.put("idProofNumber", customer.getIdProofNumber());
    customerDetails.put("idProofTypeOther", customer.getIdProofTypeOther());
    customerDetails.put("address", customer.getAddress());
    customerDetails.put("createdAt", customer.getCreatedAt());

    Map<String, Object> roomDetails = new LinkedHashMap<>();
    roomDetails.put("id", room.getId());
    roomDetails.put("roomNumber", room.getRoomNumber());
    roomDetails.put("roomType", room.getRoomType());
    roomDetails.put("floor", room.getFloor());
    roomDetails.put("baseTariff", room.getBaseTariff());
    roomDetails.put("status", room.getStatus());

    Map<String, Object> stayDetails = new LinkedHashMap<>();
    stayDetails.put("id", stay.getId());
    stayDetails.put("roomId", stay.getRoomId());
    stayDetails.put("room", roomDetails);
    stayDetails.put("customerId", stay.getCustomerId());
    stayDetails.put("customer", customerDetails);
    stayDetails.put("accountId", stay.getAccountId());
    stayDetails.put("checkoutInvoiceId", stay.getCheckoutInvoiceId());
    stayDetails.put("guestCount", stay.getGuestCount());
    stayDetails.put("isForeignGuest", stay.isForeignGuest());
    stayDetails.put("plan", stay.getPlan());
    stayDetails.put("tariff", stay.getTariff());
    stayDetails.put("checkInAt", stay.getCheckInAt());
    stayDetails.put("expectedCheckOutAt", stay.getExpectedCheckOutAt());
    stayDetails.put("actualCheckOutAt", stay.getActualCheckOutAt());
    stayDetails.put("advancePaid", stay.getAdvancePaid());
    stayDetails.put("status", stay.getStatus());
    return stayDetails;
  }

  public Map<String, Object> activeStay(String roomNumber) {
    Room room =
        rooms.findAllByTenantIdOrderByRoomNumber(tenant.tenantId()).stream()
            .filter(x -> x.getRoomNumber().equals(roomNumber))
            .findFirst()
            .orElse(null);
    if (room == null) return null;
    Stay s =
        stays
            .findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
                tenant.tenantId(), room.getId(), "active")
            .orElse(null);
    if (s == null) return null;
    String guest =
        customerRepository
            .findByTenantIdAndId(tenant.tenantId(), s.getCustomerId())
            .map(com.InnovaServe.core.entity.Customer::getName)
            .orElse("");
    return Map.of("stay_id", s.getId(), "account_id", s.getAccountId(), "guest_name", guest);
  }

  public List<Map<String, Object>> activeStays() {
    UUID tenantId = tenant.tenantId();
    Map<UUID, Room> roomById = new LinkedHashMap<>();
    rooms.findAllByTenantIdOrderByRoomNumber(tenantId)
        .forEach(room -> roomById.put(room.getId(), room));

    List<Stay> activeStays =
        stays.findAllByTenantIdAndStatusOrderByCheckInAtDesc(tenantId, "active");
    if (activeStays.isEmpty()) return List.of();

    Set<UUID> customerIds =
        activeStays.stream().map(Stay::getCustomerId).collect(java.util.stream.Collectors.toSet());
    Map<UUID, Customer> customersById =
        customerRepository.findAllByTenantIdAndIdIn(tenantId, customerIds).stream()
            .collect(java.util.stream.Collectors.toMap(Customer::getId, customer -> customer));
    Set<UUID> stayIds = activeStays.stream().map(Stay::getId).collect(java.util.stream.Collectors.toSet());
    Map<UUID, List<StayCharge>> chargesByStayId =
        charges.findAllByTenantIdAndStayIdIn(tenantId, stayIds).stream()
            .collect(java.util.stream.Collectors.groupingBy(StayCharge::getStayId));

    return activeStays.stream()
        .filter(stay -> roomById.containsKey(stay.getRoomId()))
        .filter(stay -> customersById.containsKey(stay.getCustomerId()))
        .map(stay -> {
          Room room = roomById.get(stay.getRoomId());
          Customer customer = customersById.get(stay.getCustomerId());
          Map<String, Object> option = new LinkedHashMap<>();
          option.put("stay_id", stay.getId());
          option.put("account_id", stay.getAccountId());
          option.put("room_number", room.getRoomNumber());
          option.put("guest_name", customer.getName());
          option.put("stay", stayView(stay, room, customer));
          option.put("charges", chargesByStayId.getOrDefault(stay.getId(), List.of()));
          return option;
        })
        .toList();
  }

  @Transactional
  public Map<String, Object> updateStay(UUID stayId, StayUpdate request) {
    UUID tenantId = tenant.tenantId();
    Stay stay = stays.findByTenantIdAndIdForUpdate(tenantId, stayId)
        .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    requireActiveStay(stay);
    if (stay.getCheckoutInvoiceId() != null) {
      throw new IllegalStateException("Stay details cannot be changed after the final bill is prepared");
    }
    if (request == null || request.customer() == null) {
      throw new IllegalArgumentException("Lead guest details are required");
    }

    Guest guest = request.customer();
    String name = guest.name() == null ? "" : guest.name().trim();
    String phone = guest.phone() == null ? "" : guest.phone().trim();
    if (name.isBlank() || name.length() > 150) {
      throw new IllegalArgumentException("Guest name is required and must be at most 150 characters");
    }
    if (phone.isBlank() || phone.length() > 15) {
      throw new IllegalArgumentException("Guest phone is required and must be at most 15 characters");
    }
    customers.updateProfile(
        stay.getCustomerId(),
        name,
        phone,
        guest.idProofType(),
        guest.idProofNumber(),
        guest.idProofTypeOther(),
        guest.address());
    validateStayUpdate(request, stay);
    stay.updateDetails(
        request.guestCount(),
        request.isForeignGuest(),
        request.plan(),
        request.tariff().setScale(2, RoundingMode.HALF_UP),
        request.expectedCheckOutAt(),
        request.advancePaid().setScale(2, RoundingMode.HALF_UP));
    if (request.isForeignGuest()
        && forms.findByTenantIdAndStayId(tenantId, stayId).isEmpty()) {
      forms.save(new FormCSubmission(tenantId, stayId));
    }
    return stayDetails(stayId);
  }

  private void validateStayUpdate(StayUpdate request, Stay stay) {
    if (request.guestCount() < 1) {
      throw new IllegalArgumentException("Guest count must be at least one");
    }
    if (request.plan() == null || !Set.of("EP", "CP", "MAP", "AP").contains(request.plan())) {
      throw new IllegalArgumentException("Invalid meal plan");
    }
    if (request.tariff() == null || request.tariff().signum() <= 0) {
      throw new IllegalArgumentException("Nightly tariff must be greater than zero");
    }
    if (request.advancePaid() == null || request.advancePaid().signum() < 0) {
      throw new IllegalArgumentException("Advance paid cannot be negative");
    }
    if (request.expectedCheckOutAt() == null
        || !request.expectedCheckOutAt().isAfter(stay.getCheckInAt())) {
      throw new IllegalArgumentException("Expected checkout must be after check-in");
    }
  }

  @Transactional
  public Map<String, Object> changeRoom(UUID stayId, UUID targetRoomId) {
    UUID tenantId = tenant.tenantId();
    Stay stay = stays.findByTenantIdAndIdForUpdate(tenantId, stayId)
        .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    requireActiveStay(stay);
    if (stay.getCheckoutInvoiceId() != null) {
      throw new IllegalStateException("Room cannot be changed after the final bill is prepared");
    }
    if (targetRoomId == null || targetRoomId.equals(stay.getRoomId())) {
      throw new IllegalArgumentException("Choose a different available room");
    }

    Map<UUID, Room> lockedRooms = new HashMap<>();
    List.of(stay.getRoomId(), targetRoomId).stream().sorted()
        .forEach(id -> rooms.lockByTenantIdAndId(tenantId, id)
            .ifPresent(room -> lockedRooms.put(id, room)));
    Room currentRoom = lockedRooms.get(stay.getRoomId());
    Room targetRoom = lockedRooms.get(targetRoomId);
    if (currentRoom == null || targetRoom == null) {
      throw new NoSuchElementException("Room not found");
    }
    if (!Set.of("vacant", "clean").contains(targetRoom.getStatus())) {
      throw new IllegalStateException("Selected room is no longer available");
    }

    currentRoom.setStatus("dirty");
    targetRoom.setStatus("occupied");
    stay.changeRoom(targetRoomId);
    return stayDetails(stayId);
  }

  private void requireActiveStay(Stay stay) {
    if (!"active".equals(stay.getStatus())) {
      throw new IllegalStateException("Stay is not active");
    }
  }

  @Transactional
  public StayCharge addCharge(UUID stayId, String description, BigDecimal amount) {
    Stay stay =
        stays
            .findByTenantIdAndIdForUpdate(tenant.tenantId(), stayId)
            .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    if (!"active".equals(stay.getStatus())) throw new IllegalStateException("Stay is not active");
    if (description == null || description.isBlank() || description.trim().length() > 200) {
      throw new IllegalArgumentException("Charge description is required and must be at most 200 characters");
    }
    if (amount == null || amount.signum() <= 0) {
      throw new IllegalArgumentException("Charge amount must be greater than zero");
    }

    BigDecimal normalizedAmount = amount.setScale(2, RoundingMode.HALF_UP);
    if (normalizedAmount.signum() <= 0) {
      throw new IllegalArgumentException("Charge amount must be at least 0.01");
    }
    StayCharge charge =
        charges.saveAndFlush(
            new StayCharge(
                tenant.tenantId(), stayId, description.trim(), normalizedAmount, tenant.userId()));
    UUID taxId =
        billing.taxes("room").stream()
            .findFirst()
            .map(com.InnovaServe.core.entity.TaxRule::getId)
            .orElse(null);
    var invoice =
        billing.createInvoice(
            new BillingService.NewInvoice(
                stay.getAccountId(),
                stay.getCustomerId(),
                "stay",
                List.of(
                    new BillingService.LineInput(
                        charge.getDescription(), BigDecimal.ONE, normalizedAmount, taxId))));
    billing.lock(invoice.invoice().getId());
    charge.setInvoiceId(invoice.invoice().getId());
    return charge;
  }

  public List<StayCharge> charges(UUID id) {
    getStay(id);
    return charges.findAllByTenantIdAndStayId(tenant.tenantId(), id);
  }

  @Transactional
  public Map<String, Object> checkout(UUID stayId) {
    Stay stay =
        stays
            .findByTenantIdAndIdForUpdate(tenant.tenantId(), stayId)
            .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    if (!"active".equals(stay.getStatus())) throw new IllegalStateException("Stay is not active");
    List<StayCharge> unbilledCharges =
        charges.findAllByTenantIdAndStayId(tenant.tenantId(), stayId).stream()
            .filter(charge -> charge.getInvoiceId() == null)
            .toList();
    if (stay.getCheckoutInvoiceId() == null) {
      long nights =
          Math.max(
              1,
              java.time.temporal.ChronoUnit.DAYS.between(
                  stay.getCheckInAt().toLocalDate(), LocalDate.now()));
      List<BillingService.LineInput> lines = new ArrayList<>();
      UUID taxId =
          billing.taxes("room").stream()
              .findFirst()
              .map(com.InnovaServe.core.entity.TaxRule::getId)
              .orElse(null);
      lines.add(
          new BillingService.LineInput(
              "Room tariff (" + nights + " nights)",
              BigDecimal.valueOf(nights),
              stay.getTariff(),
              taxId));
      for (StayCharge c : unbilledCharges)
        lines.add(
            new BillingService.LineInput(c.getDescription(), BigDecimal.ONE, c.getAmount(), taxId));
      var invoice =
          billing.createInvoice(
              new BillingService.NewInvoice(
                  stay.getAccountId(), stay.getCustomerId(), "stay", lines));
      billing.lock(invoice.invoice().getId());
      stay.setCheckoutInvoiceId(invoice.invoice().getId());
      unbilledCharges.forEach(charge -> charge.setInvoiceId(invoice.invoice().getId()));
    } else if (!unbilledCharges.isEmpty()) {
      UUID taxId =
          billing.taxes("room").stream()
              .findFirst()
              .map(com.InnovaServe.core.entity.TaxRule::getId)
              .orElse(null);
      List<BillingService.LineInput> chargeLines =
          unbilledCharges.stream()
              .map(charge -> new BillingService.LineInput(
                  charge.getDescription(), BigDecimal.ONE, charge.getAmount(), taxId))
              .toList();
      var chargeInvoice =
          billing.createInvoice(
              new BillingService.NewInvoice(
                  stay.getAccountId(), stay.getCustomerId(), "stay", chargeLines));
      billing.lock(chargeInvoice.invoice().getId());
      unbilledCharges.forEach(charge -> charge.setInvoiceId(chargeInvoice.invoice().getId()));
    }
    List<com.InnovaServe.core.entity.Invoice> all = billing.invoicesForAccount(stay.getAccountId());
    BigDecimal due = BigDecimal.ZERO;
    List<Map<String, Object>> invoiceBreakdown = new ArrayList<>();
    for (var invoice : all) {
      BigDecimal invoiceDue = billing.amountDue(invoice);
      BigDecimal paid = billing.amountPaid(invoice.getId());
      BigDecimal credited =
          invoice.getTotalAmount().subtract(invoiceDue).subtract(paid).max(BigDecimal.ZERO);
      due = due.add(invoiceDue);

      Map<String, Object> invoiceDetails = billing.invoice(invoice.getId());
      Map<String, Object> breakdown = new LinkedHashMap<>();
      breakdown.put("invoice", invoice);
      breakdown.put("line_items", invoiceDetails.get("line_items"));
      breakdown.put("amount_paid", paid);
      breakdown.put("amount_credited", credited);
      breakdown.put("amount_due", invoiceDue);
      invoiceBreakdown.add(breakdown);
    }
    return Map.of(
        "invoices", all,
        "invoice_breakdown", invoiceBreakdown,
        "total_due", due.toPlainString());
  }

  @Transactional
  public void confirmCheckout(UUID stayId) {
    Stay stay =
        stays
            .findByTenantIdAndIdForUpdate(tenant.tenantId(), stayId)
            .orElseThrow(() -> new NoSuchElementException("Stay not found"));
    billing.closeAccount(stay.getAccountId());
    stay.checkout();
    rooms.findByTenantIdAndId(tenant.tenantId(), stay.getRoomId()).orElseThrow().setStatus("dirty");
  }

  @Transactional
  public FormCSubmission submitFormC(UUID stayId, String reference) {
    getStay(stayId);
    FormCSubmission f =
        forms
            .findByTenantIdAndStayId(tenant.tenantId(), stayId)
            .orElseThrow(() -> new NoSuchElementException("Form C submission not found"));
    f.submit(reference);
    return f;
  }

  public FormCSubmission formC(UUID stayId) {
    getStay(stayId);
    return forms
        .findByTenantIdAndStayId(tenant.tenantId(), stayId)
        .orElseThrow(() -> new NoSuchElementException("Form C submission not found"));
  }

  public record Guest(
      String name,
      String phone,
      String idProofType,
      String idProofNumber,
      String idProofTypeOther,
      String address) {}

  public record CheckIn(
      UUID roomId,
      Guest customer,
      short guestCount,
      String plan,
      BigDecimal tariff,
      BigDecimal advancePaid,
      boolean isForeignGuest,
      LocalDateTime expectedCheckOutAt) {}

  public record StayUpdate(
      Guest customer,
      short guestCount,
      String plan,
      BigDecimal tariff,
      BigDecimal advancePaid,
      boolean isForeignGuest,
      LocalDateTime expectedCheckOutAt) {}
}
