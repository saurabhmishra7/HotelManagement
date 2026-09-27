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
  @PreAuthorize("hasAuthority('PERM_ROLE_READ')")
  public List<StaffRole> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
  public StaffRole create(@RequestBody RoleRequest request) {
    return service.create(request.role(), request.permissions());
  }

  public record RoleRequest(@JsonProperty("name") RoleType role, List<Permission> permissions) {}
}
