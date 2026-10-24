package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.StaffApprovalService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff-approvals")
@PreAuthorize("hasAuthority('PERM_STAFF_MANAGE')")
public class StaffApprovalController {
  private final StaffApprovalService service;
  public StaffApprovalController(StaffApprovalService service) { this.service = service; }

  @GetMapping
  public List<Map<String, Object>> pending() { return service.pending(); }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable UUID id) { return service.get(id); }

  @PostMapping("/{id}/approve")
  public Map<String, Object> approve(@PathVariable UUID id, @RequestBody PinRequest request) {
    return service.review(id, request.pin(), true);
  }

  @PostMapping("/{id}/reject")
  public Map<String, Object> reject(@PathVariable UUID id, @RequestBody PinRequest request) {
    return service.review(id, request.pin(), false);
  }

  public record PinRequest(String pin) {}
}
