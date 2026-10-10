package com.InnovaServe.platform.controller;

import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.NotificationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/notifications")
@PreAuthorize("hasAuthority('PLATFORM_NOTIFICATIONS_MANAGE')")
public class PlatformNotificationController {
  private final NotificationService notifications;
  private final TenantRepository tenants;
  public PlatformNotificationController(NotificationService notifications, TenantRepository tenants) {
    this.notifications = notifications; this.tenants = tenants;
  }
  @GetMapping("/tenants")
  public List<TenantOption> tenants(@RequestParam(required = false) String search) {
    String normalizedSearch = search == null ? "" : search.trim();
    return tenants.findTenantSummaries(normalizedSearch, PageRequest.of(0, 100)).getContent().stream()
        .map(t -> new TenantOption(t.getId(), t.getName(), t.getTenantCode())).toList();
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public void send(@RequestBody NoticeRequest request) {
    notifications.sendPlatformNotice(request.tenantIds(), Boolean.TRUE.equals(request.allTenants()), request.title(), request.message());
  }
  public record TenantOption(UUID id, String name, @JsonProperty("tenant_code") String tenantCode) {}
  public record NoticeRequest(@JsonProperty("tenant_ids") List<UUID> tenantIds,
      @JsonProperty("all_tenants") Boolean allTenants, String title, String message) {}
}
