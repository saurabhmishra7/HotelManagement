package com.InnovaServe.platform.controller;

import com.InnovaServe.platform.service.SubscriptionRequestConversationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/subscription-requests/{requestId}")
public class TenantSubscriptionRequestConversationController {
  private final SubscriptionRequestConversationService conversations;

  public TenantSubscriptionRequestConversationController(
      SubscriptionRequestConversationService conversations) {
    this.conversations = conversations;
  }

  @GetMapping("/comments")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public List<Map<String, Object>> comments(@PathVariable UUID requestId) {
    return conversations.tenantComments(requestId);
  }

  @PostMapping("/comments")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_REQUEST')")
  public Map<String, Object> addComment(
      @PathVariable UUID requestId, @RequestBody CommentRequest body) {
    return conversations.addTenantComment(requestId, body.message());
  }

  @GetMapping("/proposals")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_READ')")
  public List<Map<String, Object>> proposals(@PathVariable UUID requestId) {
    return conversations.tenantProposals(requestId);
  }

  @PostMapping("/proposals/{proposalId}/agree")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_REQUEST')")
  public Map<String, Object> agree(
      @PathVariable UUID requestId, @PathVariable UUID proposalId) {
    return conversations.agree(requestId, proposalId);
  }

  @PostMapping("/revoke")
  @PreAuthorize("hasAuthority('PERM_TENANT_SUBSCRIPTION_REQUEST')")
  public Map<String, Object> revoke(@PathVariable UUID requestId) {
    return conversations.revoke(requestId);
  }

  public record CommentRequest(@JsonProperty("message") String message) {}
}
