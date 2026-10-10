package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", schema = "core")
public class Notification {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(name = "recipient_user_id", nullable = false) private UUID recipientUserId;
  @Column(nullable = false, length = 160) private String title;
  @Column(nullable = false, columnDefinition = "text") private String message;
  @Column(nullable = false, length = 20) private String category;
  @Column(nullable = false, length = 40) private String source;
  @Column(name = "source_key", length = 200) private String sourceKey;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(name = "read_at") private Instant readAt;
  @Column(name = "dismissed_at") private Instant dismissedAt;

  protected Notification() {}

  public Notification(UUID tenantId, UUID recipientUserId, String title, String message,
      String category, String source, String sourceKey) {
    this.tenantId = tenantId;
    this.recipientUserId = recipientUserId;
    this.title = title;
    this.message = message;
    this.category = category;
    this.source = source;
    this.sourceKey = sourceKey;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getRecipientUserId() { return recipientUserId; }
  public String getTitle() { return title; }
  public String getMessage() { return message; }
  public String getCategory() { return category; }
  public String getSource() { return source; }
  public String getSourceKey() { return sourceKey; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getReadAt() { return readAt; }
  public Instant getDismissedAt() { return dismissedAt; }
  public void markRead() { if (readAt == null) readAt = Instant.now(); }
  public void dismiss() { dismissedAt = Instant.now(); markRead(); }
}
