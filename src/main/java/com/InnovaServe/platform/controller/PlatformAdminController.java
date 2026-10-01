package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.PlatformAdminService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/admins")
@PreAuthorize("hasAuthority('PLATFORM_ADMINS_MANAGE')")
public class PlatformAdminController {
  private final PlatformAdminService admins;

  public PlatformAdminController(PlatformAdminService admins) {
    this.admins = admins;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return admins.list();
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody CreateAdminRequest request) {
    return admins.create(
        request.name(), request.email(), request.password(), request.role(), currentAdminId());
  }

  @PatchMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable UUID id, @RequestBody UpdateAdminRequest request) {
    return admins.update(id, request.role(), request.active(), currentAdminId());
  }

  private UUID currentAdminId() {
    PlatformAdmin admin =
        (PlatformAdmin) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return admin.getId();
  }

  public record CreateAdminRequest(String name, String email, String password, String role) {}

  public record UpdateAdminRequest(String role, Boolean active) {}
}
