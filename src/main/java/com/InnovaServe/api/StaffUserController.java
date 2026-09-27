package com.InnovaServe.api;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.service.StaffUserService;
import com.InnovaServe.core.service.TokenService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class StaffUserController {
  private final StaffUserService service;
  private final TokenService tokens;

  public StaffUserController(StaffUserService s, TokenService tokens) {
    service = s;
    this.tokens = tokens;
  }

  @PostMapping("/auth/login")
  public Map<String, Object> login(
      @RequestHeader("X-Tenant-Id") UUID tenant, @RequestBody LoginRequest r) {
    StaffUser u = service.authenticate(tenant, r.identity(), r.password());
    return Map.of("token", tokens.issue(tenant, u.getId(), u.getRoleId()), "user", user(u));
  }

  @GetMapping("/users")
  public List<StaffUser> users() {
    return service.list();
  }

  @PostMapping("/users")
  public Map<String, Object> create(@RequestBody CreateUser r) {
    StaffUser u = service.create(r.name(), r.phone(), r.email(), r.password(), r.pin(), r.roleId());
    return user(u);
  }

  @PatchMapping("/users/{id}")
  public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateUser r) {
    return user(service.update(id, r.name(), r.roleId(), r.active()));
  }

  @PostMapping("/users/{id}/verify-pin")
  public Map<String, Boolean> pin(@PathVariable UUID id, @RequestBody PinRequest r) {
    return Map.of("valid", service.verifyPin(id, r.pin()));
  }

  private Map<String, Object> user(StaffUser u) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", u.getId());
    m.put("name", u.getName());
    m.put("phone", u.getPhone());
    m.put("email", u.getEmail());
    m.put("role_id", u.getRoleId());
    m.put("active", u.isActive());
    return m;
  }

  public record LoginRequest(String phone, String email, String password) {
    public String identity() {
      return phone != null ? phone : email;
    }
  }

  public record CreateUser(
      String name,
      String phone,
      String email,
      String password,
      @JsonProperty("role_id") UUID roleId,
      String pin) {}

  public record UpdateUser(String name, @JsonProperty("role_id") UUID roleId, Boolean active) {}

  public record PinRequest(String pin) {}
}
