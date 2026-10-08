package com.InnovaServe.core.controller;

import com.InnovaServe.core.service.TenantProfileService;
import com.InnovaServe.core.service.TenantLogoService;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tenant/profile")
public class TenantProfileController {
  private final TenantProfileService service;
  private final TenantLogoService logos;

  public TenantProfileController(TenantProfileService service, TenantLogoService logos) {
    this.service = service;
    this.logos = logos;
  }

  @GetMapping
  public TenantProfileService.Profile get() { return service.profile(); }

  @PutMapping
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile update(@RequestBody TenantProfileService.Details request) {
    return service.updateDetails(request);
  }

  @PutMapping("/rules")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile rules(@RequestBody TenantProfileService.Rules request) {
    return service.updateRules(request);
  }

  @PutMapping("/restaurant-settings")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile restaurantSettings(
      @RequestBody TenantProfileService.RestaurantSettings request) {
    return service.updateRestaurantSettings(request);
  }

  @PostMapping("/charge-presets")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile addPreset(@RequestBody TenantProfileService.PresetInput request) {
    return service.savePreset(null, request);
  }

  @PutMapping("/charge-presets/{id}")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile editPreset(@PathVariable UUID id,
      @RequestBody TenantProfileService.PresetInput request) {
    return service.savePreset(id, request);
  }

  @DeleteMapping("/charge-presets/{id}")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile deletePreset(@PathVariable UUID id) { return service.deletePreset(id); }

  @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile uploadLogo(@RequestPart("file") MultipartFile file) {
    return service.saveLogo(logos.normalize(file));
  }

  @DeleteMapping("/logo")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public TenantProfileService.Profile removeLogo() { return service.saveLogo(null); }

  @GetMapping(value = "/logo", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> logo() {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG).body(service.logo());
  }
}
