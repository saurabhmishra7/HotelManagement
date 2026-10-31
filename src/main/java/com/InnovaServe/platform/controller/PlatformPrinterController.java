package com.InnovaServe.platform.controller;

import com.InnovaServe.restaurant.service.PrinterService;
import com.InnovaServe.platform.entity.PlatformAdmin;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformPrinterController {
  private final PrinterService printers;
  public PlatformPrinterController(PrinterService printers) { this.printers=printers; }
  @GetMapping("/printer-compatibility")
  @PreAuthorize("hasAuthority('PLATFORM_TENANTS_READ')")
  public Object list(@RequestParam(defaultValue="") String brand,@RequestParam(defaultValue="") String model) { return printers.compatibility(brand,model); }
  @PostMapping("/printer-compatibility")
  @PreAuthorize("hasAuthority('PLATFORM_SETTINGS_MANAGE')")
  public Object create(@jakarta.validation.Valid @RequestBody Map<String,Object> body) { printers.saveCompatibility(null,adminId(),body); return Map.of("saved",true); }
  @PatchMapping("/printer-compatibility/{id}")
  @PreAuthorize("hasAuthority('PLATFORM_SETTINGS_MANAGE')")
  public Object update(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody Map<String,Object> body) { printers.saveCompatibility(id,adminId(),body); return Map.of("saved",true); }
  @GetMapping("/tenants/{id}/printers")
  @PreAuthorize("hasAuthority('PLATFORM_TENANTS_READ')")
  public Object tenant(@PathVariable UUID id) { return printers.overview(id); }
  private UUID adminId() { return ((PlatformAdmin)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId(); }
}
