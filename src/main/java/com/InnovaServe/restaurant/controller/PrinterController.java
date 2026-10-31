package com.InnovaServe.restaurant.controller;

import com.InnovaServe.restaurant.service.PrinterService;
import com.InnovaServe.core.service.TenantProfileService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PrinterController {
  private final PrinterService printers;
  private final TenantProfileService profiles;
  public PrinterController(PrinterService printers,TenantProfileService profiles) { this.printers=printers; this.profiles=profiles; }
  @GetMapping("/printer-devices")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> list() { return printers.list(); }
  @PostMapping("/printer-devices")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> register(@jakarta.validation.Valid @RequestBody Map<String,Object> input) { return printers.register(input); }
  @PatchMapping("/printer-devices/{id}")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> update(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody Map<String,Object> input) { printers.update(id,input); return Map.of("saved",true); }
  @PatchMapping("/printer-devices/routes/{purpose}")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> route(@PathVariable String purpose,@jakarta.validation.Valid @RequestBody Map<String,UUID> input) { printers.route(purpose,input.get("device_id")); return Map.of("saved",true); }
  @PostMapping("/printer-devices/{id}/tested")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> tested(@PathVariable UUID id) { printers.tested(id); return Map.of("saved",true); }
  @PostMapping("/printer-devices/{id}/agent-tested")
  public Map<String,Object> agentTested(@PathVariable UUID id,@RequestHeader("X-Print-Device-Key") String key) { printers.agentTested(id,key); return Map.of("saved",true); }
  @GetMapping("/printer-devices/pending-jobs")
  public ResponseEntity<Map<String,Object>> pending(@RequestParam("device_id") UUID id,
      @RequestHeader(value="X-Print-Device-Key",required=false) String key) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(printers.pending(id,key));
  }
  @PostMapping("/printer-devices/{id}/jobs/{jobId}/status")
  public Map<String,Object> status(@PathVariable UUID id,@PathVariable UUID jobId,
      @RequestHeader(value="X-Print-Device-Key",required=false) String key,@jakarta.validation.Valid @RequestBody Map<String,Object> body) {
    printers.acknowledge(id,jobId,key,body); return Map.of("saved",true);
  }
  @PostMapping("/printer-devices/jobs/{jobId}/review")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public Map<String,Object> review(@PathVariable UUID jobId,@jakarta.validation.Valid @RequestBody Map<String,Boolean> input) {
    printers.review(jobId,Boolean.TRUE.equals(input.get("retry"))); return Map.of("saved",true);
  }
  @GetMapping("/printer-compatibility")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public List<Map<String,Object>> compatibility(@RequestParam(defaultValue="") String brand,@RequestParam(defaultValue="") String model) {
    return printers.compatibility(brand,model);
  }
  @GetMapping("/settings/restaurant-workflow")
  public Map<String,Object> workflow() { return Map.of("serviceMode",profiles.profile().restaurantServiceMode()); }
  @PostMapping("/printer-devices/invoices/{id}/print")
  @PreAuthorize("hasAnyAuthority('PERM_BILLING_READ','PERM_BILL_CREATE')")
  public Object receipt(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody Map<String,UUID> body) {
    UUID request=body.get("request_id");
    if(request==null) throw new IllegalArgumentException("A print request ID is required");
    return printers.receipt(id,request);
  }
  @PatchMapping("/settings/restaurant-workflow")
  @PreAuthorize("hasAuthority('PERM_TENANT_PROFILE_MANAGE')")
  public Object workflow(@jakarta.validation.Valid @RequestBody TenantProfileService.RestaurantSettings settings) {
    String mode="thermal_only".equals(settings.serviceMode())?"thermal_printer":settings.serviceMode();
    return profiles.updateRestaurantSettings(new TenantProfileService.RestaurantSettings(mode));
  }
}
