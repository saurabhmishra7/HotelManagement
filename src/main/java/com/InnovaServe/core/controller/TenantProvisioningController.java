package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.service.TenantProvisioningService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantProvisioningController {
  private final TenantProvisioningService service;
  private final String provisioningKey;

  public TenantProvisioningController(
      TenantProvisioningService service,
      @Value("${app.tenant-provisioning-key:}") String provisioningKey) {
    this.service = service;
    this.provisioningKey = provisioningKey;
  }

  @PostMapping
  public Map<String, Object> createTenant(
      @RequestHeader("X-Provisioning-Key") String suppliedKey,
      @RequestBody ProvisionTenantRequest request) {
    if (provisioningKey == null || provisioningKey.length() < 32)
      throw new IllegalStateException("Tenant provisioning is not configured");
    if (!MessageDigest.isEqual(
        provisioningKey.getBytes(StandardCharsets.UTF_8),
        suppliedKey.getBytes(StandardCharsets.UTF_8))) {
      throw new AccessDeniedException("Invalid provisioning key");
    }
    if (request.name() == null || request.name().isBlank())
      throw new IllegalArgumentException("Tenant name is required");
    if (request.ownerName() == null || request.ownerName().isBlank())
      throw new IllegalArgumentException("Owner name is required");
    if (request.ownerPhone() == null || request.ownerPhone().isBlank())
      throw new IllegalArgumentException("Owner phone is required");
    if (request.ownerPassword() == null || request.ownerPassword().length() < 12)
      throw new IllegalArgumentException("Owner password must be at least 12 characters");
    if (request.ownerPin() == null || !request.ownerPin().matches("[0-9]{4,8}"))
      throw new IllegalArgumentException("Owner PIN must contain 4 to 8 digits");

    var result =
        service.create(
            new TenantProvisioningService.ProvisionTenant(
                request.name(),
                request.gstin(),
                request.address(),
                request.ownerName(),
                request.ownerPhone(),
                request.ownerEmail(),
                request.ownerPassword(),
                request.ownerPin()));
    Tenant tenant = result.tenant();
    StaffUser owner = result.owner();
    StaffRole role = result.ownerRole();
    return Map.of(
        "tenant_id",
        tenant.getId(),
        "tenant_name",
        tenant.getName(),
        "owner",
        Map.of(
            "id", owner.getId(),
            "name", owner.getName(),
            "phone", owner.getPhone(),
            "role_id", role.getId(),
            "role", role.getName()));
  }

  public record ProvisionTenantRequest(
      String name,
      String gstin,
      String address,
      @JsonProperty("owner_name") String ownerName,
      @JsonProperty("owner_phone") String ownerPhone,
      @JsonProperty("owner_email") String ownerEmail,
      @JsonProperty("owner_password") String ownerPassword,
      @JsonProperty("owner_pin") String ownerPin) {}
}
