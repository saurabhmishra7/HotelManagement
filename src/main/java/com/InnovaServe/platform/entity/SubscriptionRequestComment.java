package com.InnovaServe.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_request_comment", schema = "platform")
public class SubscriptionRequestComment {
  @Id
  private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "author_type", nullable = false, length = 20)
  private String authorType;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(columnDefinition = "text")
  private String message;

  @Column(name = "proposal_id")
  private UUID proposalId;

  @Column(name = "created_at", insertable = false, updatable = false)
  private Instant createdAt;

  protected SubscriptionRequestComment() {
  }

  public SubscriptionRequestComment(
    UUID requestId, String authorType, UUID authorId, String message, UUID proposalId) {
    this.id = UUID.randomUUID();
    this.requestId = requestId;
    this.authorType = authorType;
    this.authorId = authorId;
    this.message = message;
    this.proposalId = proposalId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public String getAuthorType() {
    return authorType;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getMessage() {
    return message;
  }

  public UUID getProposalId() {
    return proposalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
