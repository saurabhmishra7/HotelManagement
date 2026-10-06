package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.core.security.RoleType;
import com.InnovaServe.core.service.StaffRoleService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class StaffRoleController {
  private final StaffRoleService service;

  public StaffRoleController(StaffRoleService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('PERM_ROLE_READ', 'PERM_ROLE_MANAGE')")
  public List<StaffRole> list() {
    return service.list();
  }

  @GetMapping("/summary")
  @PreAuthorize("hasAnyAuthority('PERM_ROLE_READ', 'PERM_ROLE_MANAGE')")
  public List<RoleSummary> listSummaries() {
    return service.list().stream()
        .map(role -> new RoleSummary(role.getId(), role.getName()))
        .toList();
  }

  @GetMapping("/presets")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public List<RolePreset> presets() {
    return Arrays.stream(RoleType.values())
        .map(role -> new RolePreset(
            role.name(), role.defaultPermissions().stream().map(Enum::name).sorted().toList()))
        .toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('PERM_ROLE_READ', 'PERM_ROLE_MANAGE')")
  public StaffRole get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public StaffRole create(@RequestBody RoleRequest request) {
    return service.create(request.role(), request.permissions());
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public StaffRole update(@PathVariable UUID id, @RequestBody UpdateRoleRequest request) {
    return service.update(id, request.permissions());
  }

  public record RoleRequest(@JsonProperty("name") RoleType role, List<Permission> permissions) {}

  public record UpdateRoleRequest(List<Permission> permissions) {}

  public record RolePreset(String name, List<String> permissions) {}

  public record RoleSummary(UUID id, String name) {}
}
