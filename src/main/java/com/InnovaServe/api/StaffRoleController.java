package com.InnovaServe.api;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.service.StaffRoleService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
public class StaffRoleController {
  private final StaffRoleService service;

  public StaffRoleController(StaffRoleService service) {
    this.service = service;
  }

  @GetMapping
  public List<StaffRole> list() {
    return service.list();
  }

  @PostMapping
  public StaffRole create(@RequestBody RoleRequest request) {
    return service.create(
        request.name(), request.permissions() == null ? List.of() : request.permissions());
  }

  public record RoleRequest(String name, List<String> permissions) {}
}
