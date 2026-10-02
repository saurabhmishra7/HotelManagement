package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantProvisioningService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantProvisioningController {
  private final TenantProvisioningService service;
  private final ModuleEntitlementService modules;
  private final String provisioningKey;

  public TenantProvisioningController(
      TenantProvisioningService service,
      ModuleEntitlementService modules,
      @Value("${app.tenant-provisioning-key:}") String provisioningKey) {
    this.service = service;
    this.modules = modules;
    this.provisioningKey = provisioningKey;
  }

  @PostMapping
  public Map<String, Object> createTenant(
      @RequestHeader("X-Provisioning-Key") String suppliedKey,
      @RequestBody ProvisionTenantRequest request) {
    verifyProvisioningKey(suppliedKey);
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
                request.ownerPin(),
                requestedModules(request.modules()),
                request.selectedPlanId(),
                request.subscriptionRequestMessage()));
    Tenant tenant = result.tenant();
    StaffUser owner = result.owner();
    StaffRole role = result.ownerRole();
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("tenant_id", tenant.getId());
    response.put("tenant_name", tenant.getName());
    response.put("tenant_code", tenant.getTenantCode());
    response.put("modules", modules.activeModules(tenant.getId()));
    response.put(
        "owner",
        Map.of(
            "id", owner.getId(),
            "name", owner.getName(),
            "phone", owner.getPhone(),
            "role_id", role.getId(),
            "role", role.getName()));
    if (result.subscriptionRequest() != null) {
      response.put(
          "subscription_request",
          Map.of(
              "id", result.subscriptionRequest().getId(),
              "plan_id", result.subscriptionRequest().getRequestedPlanId(),
              "request_type", result.subscriptionRequest().getRequestType(),
              "status", result.subscriptionRequest().getStatus()));
    }
    return response;
  }

  @PatchMapping("/{tenantId}/modules")
  public Map<String, Object> replaceModules(
      @PathVariable UUID tenantId,
      @RequestHeader("X-Provisioning-Key") String suppliedKey,
      @RequestBody ModuleSelectionRequest request) {
    verifyProvisioningKey(suppliedKey);
    if (request.modules() == null)
      throw new IllegalArgumentException("modules is required when changing tenant modules");
    return Map.of(
        "tenant_id",
        tenantId,
        "modules",
        modules.replaceModules(tenantId, requestedModules(request.modules())));
  }

  private void verifyProvisioningKey(String suppliedKey) {
    if (provisioningKey == null || provisioningKey.length() < 32)
      throw new IllegalStateException("Tenant provisioning is not configured");
    if (suppliedKey == null
        || !MessageDigest.isEqual(
            provisioningKey.getBytes(StandardCharsets.UTF_8),
            suppliedKey.getBytes(StandardCharsets.UTF_8))) {
      throw new AccessDeniedException("Invalid provisioning key");
    }
  }

  private Set<ModuleType> requestedModules(List<String> requested) {
    if (requested == null) return EnumSet.allOf(ModuleType.class);
    EnumSet<ModuleType> parsed = EnumSet.noneOf(ModuleType.class);
    for (String value : requested) {
      ModuleType module = ModuleType.from(value);
      if (!parsed.add(module)) throw new IllegalArgumentException("Duplicate module: " + value);
    }
    return parsed;
  }

  public record ProvisionTenantRequest(
      String name,
      String gstin,
      String address,
      @JsonProperty("owner_name") String ownerName,
      @JsonProperty("owner_phone") String ownerPhone,
      @JsonProperty("owner_email") String ownerEmail,
      @JsonProperty("owner_password") String ownerPassword,
      @JsonProperty("owner_pin") String ownerPin,
      List<String> modules,
      @JsonProperty("selected_plan_id") UUID selectedPlanId,
      @JsonProperty("subscription_request_message") String subscriptionRequestMessage) {}

  public record ModuleSelectionRequest(List<String> modules) {}
}
