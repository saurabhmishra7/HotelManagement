package com.InnovaServe.stay.controller;

import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.RequiresModule;
import com.InnovaServe.stay.service.RoomServiceRequestService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/room-service-requests")
@RequiresModule(ModuleType.STAY)
public class RoomServiceRequestController {
  private final RoomServiceRequestService service;

  public RoomServiceRequestController(RoomServiceRequestService service) { this.service = service; }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_ROOM_SERVICE_REQUEST_HANDLE') or hasAuthority('PERM_STAY_CHECKIN')")
  public List<RoomServiceRequestService.View> list() { return service.forStaff(); }

  @PatchMapping("/{id}/complete")
  @PreAuthorize("hasAuthority('PERM_ROOM_SERVICE_REQUEST_HANDLE')")
  public RoomServiceRequestService.View complete(@PathVariable UUID id) { return service.complete(id); }
}
