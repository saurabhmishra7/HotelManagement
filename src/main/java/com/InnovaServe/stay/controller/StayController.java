package com.InnovaServe.stay.controller;

import com.InnovaServe.stay.entity.*;
import com.InnovaServe.stay.enums.RoomType;
import com.InnovaServe.stay.service.StayService;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.RequiresModule;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiresModule(ModuleType.STAY)
public class StayController {
  private final StayService service;

  public StayController(StayService service) {
    this.service = service;
  }

  @GetMapping("/rooms")
  @PreAuthorize("hasAuthority('PERM_ROOM_READ')")
  public List<Room> rooms() {
    return service.rooms();
  }

  @GetMapping("/room-types")
  @PreAuthorize("hasAuthority('PERM_ROOM_READ')")
  public List<RoomTypeOption> roomTypes() {
    return Arrays.stream(RoomType.values())
        .map(type -> new RoomTypeOption(type.name(), type.label()))
        .toList();
  }

  @PostMapping("/rooms")
  @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
  public Room createRoom(@RequestBody RoomRequest request) {
    return service.createRoom(
        request.roomNumber(), request.roomType(), request.floor(), request.baseTariff());
  }

  @PatchMapping("/rooms/{id}")
  @PreAuthorize("hasAuthority('PERM_ROOM_MANAGE')")
  public Room updateRoom(@PathVariable UUID id, @RequestBody RoomRequest request) {
    return service.updateRoom(
        id, request.roomNumber(), request.roomType(), request.floor(), request.baseTariff());
  }

  @GetMapping("/rooms/{id}")
  @PreAuthorize("hasAuthority('PERM_ROOM_READ')")
  public Room room(@PathVariable UUID id) {
    return service.room(id);
  }

  @PatchMapping("/rooms/{id}/status")
  @PreAuthorize("hasAuthority('PERM_HOUSEKEEPING_UPDATE')")
  public Room roomStatus(@PathVariable UUID id, @RequestBody StatusRequest r) {
    if (!Set.of("vacant", "occupied", "dirty", "clean").contains(r.status()))
      throw new IllegalArgumentException("Invalid room status");
    return service.setRoomStatus(id, r.status());
  }

  @PostMapping("/stays")
  @PreAuthorize("hasAuthority('PERM_STAY_CHECKIN')")
  public Map<String, Object> checkIn(@RequestBody StayService.CheckIn request) {
    Stay s = service.checkIn(request);
    return Map.of("stay_id", s.getId(), "account_id", s.getAccountId());
  }

  @GetMapping("/stays/active")
  @PreAuthorize("hasAuthority('PERM_STAY_READ')")
  public Object active(@RequestParam(name = "room_number", required = false) String roomNumber) {
    if (roomNumber == null || roomNumber.isBlank()) return service.activeStays();
    return service.activeStay(roomNumber.trim());
  }

  @GetMapping("/stays/{id}")
  @PreAuthorize("hasAuthority('PERM_STAY_READ')")
  public Map<String, Object> stay(@PathVariable UUID id) {
    return service.stayDetails(id);
  }

  @PatchMapping("/stays/{id}")
  @PreAuthorize("hasAuthority('PERM_STAY_MANAGE')")
  public Map<String, Object> updateStay(
      @PathVariable UUID id, @RequestBody StayService.StayUpdate request) {
    return service.updateStay(id, request);
  }

  @PatchMapping("/stays/{id}/room")
  @PreAuthorize("hasAuthority('PERM_STAY_MANAGE')")
  public Map<String, Object> changeRoom(
      @PathVariable UUID id, @RequestBody ChangeRoomRequest request) {
    return service.changeRoom(id, request.roomId());
  }

  @PostMapping("/stays/{id}/charges")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PERM_STAY_CHARGE')")
  public StayCharge charge(@PathVariable UUID id, @RequestBody ChargeRequest r) {
    return service.addCharge(id, r.description(), r.amount());
  }

  @PostMapping("/stays/{id}/checkout")
  @PreAuthorize("hasAuthority('PERM_STAY_CHECKOUT')")
  public Map<String, Object> checkout(@PathVariable UUID id) {
    return service.checkout(id);
  }

  @PostMapping("/stays/{id}/confirm-checkout")
  @PreAuthorize("hasAuthority('PERM_STAY_CHECKOUT')")
  public Map<String, String> confirmCheckout(@PathVariable UUID id) {
    service.confirmCheckout(id);
    return Map.of("status", "checked_out");
  }

  @GetMapping("/stays/{id}/form-c")
  @PreAuthorize("hasAuthority('PERM_FORM_C_READ')")
  public FormCSubmission formC(@PathVariable UUID id) {
    return service.formC(id);
  }

  @PostMapping("/stays/{id}/form-c/submit")
  @PreAuthorize("hasAuthority('PERM_FORM_C_SUBMIT')")
  public FormCSubmission submit(@PathVariable UUID id, @RequestBody FormCRequest r) {
    return service.submitFormC(id, r.referenceNumber());
  }

  public record StatusRequest(String status) {}

  public record RoomTypeOption(String value, String label) {}

  public record RoomRequest(
      @JsonProperty("room_number") String roomNumber,
      @JsonProperty("room_type") RoomType roomType,
      String floor,
      @JsonProperty("base_tariff") BigDecimal baseTariff) {}

  public record ChargeRequest(String description, BigDecimal amount) {}

  public record ChangeRoomRequest(UUID roomId) {}

  public record FormCRequest(String referenceNumber) {}
}
