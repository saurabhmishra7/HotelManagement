package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.entity.PlatformAdmin;
import com.InnovaServe.platform.service.SubscriptionRequestConversationService;
import com.InnovaServe.platform.service.PlatformSubscriptionRequestService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/subscription-requests")
public class PlatformSubscriptionRequestController {
  private final PlatformSubscriptionRequestService service;
  private final SubscriptionRequestConversationService conversations;

  public PlatformSubscriptionRequestController(
      PlatformSubscriptionRequestService service,
      SubscriptionRequestConversationService conversations) {
    this.service = service;
    this.conversations = conversations;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public List<Map<String, Object>> list(@RequestParam(required = false) String status) {
    return service.list(status);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> update(@PathVariable UUID id, @RequestBody UpdateRequest body) {
    return service.update(id, body.status(), body.responseNote(), currentAdminId());
  }

  @GetMapping("/{id}/comments")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public List<Map<String, Object>> comments(@PathVariable UUID id) {
    return conversations.platformComments(id);
  }

  @PostMapping("/{id}/comments")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public Map<String, Object> addComment(
      @PathVariable UUID id, @RequestBody CommentRequest body) {
    return conversations.addPlatformComment(id, body.message(), currentAdminId());
  }

  @PostMapping("/{id}/proposals/preview")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public List<Map<String, Object>> previewProposals(
      @PathVariable UUID id, @RequestBody ProposalRequest body) {
    return conversations.preview(id, body.calculationTypes());
  }

  @PostMapping("/{id}/proposals")
  @PreAuthorize("hasAuthority('PLATFORM_SUBSCRIPTIONS_MANAGE')")
  public List<Map<String, Object>> createProposals(
      @PathVariable UUID id, @RequestBody ProposalRequest body) {
    return conversations.createProposals(id, body.calculationTypes(), currentAdminId());
  }

  private UUID currentAdminId() {
    PlatformAdmin admin = (PlatformAdmin) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
    return admin.getId();
  }

  public record UpdateRequest(
      String status, @JsonProperty("response_note") String responseNote) {}

  public record CommentRequest(String message) {}

  public record ProposalRequest(
      @JsonProperty("calculation_types") List<String> calculationTypes) {}

}
