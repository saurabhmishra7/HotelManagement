package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "staff_approval_request", schema = "core")
public class StaffApprovalRequest {
  @Id private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(name = "target_user_id") private UUID targetUserId;
  @Column(name = "request_type", nullable = false, length = 12) private String requestType;
  @Column(length = 100) private String name;
  @Column(length = 15) private String phone;
  @Column(length = 150) private String email;
  @Column(name = "password_hash", columnDefinition = "text") private String passwordHash;
  @Column(name = "pin_hash", columnDefinition = "text") private String pinHash;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name = "role_ids", nullable = false, columnDefinition = "jsonb") private List<UUID> roleIds;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name = "previous_role_ids", columnDefinition = "jsonb") private List<UUID> previousRoleIds;
  @Column(nullable = false, length = 12) private String status = "pending";
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
  @Column(name = "reviewed_at") private Instant reviewedAt;
  @Column(name = "reviewed_by") private UUID reviewedBy;
  @Version private long version;

  protected StaffApprovalRequest() {}

  public static StaffApprovalRequest create(UUID tenantId, UUID requester, String name, String phone,
      String email, String passwordHash, String pinHash, Collection<UUID> roleIds) {
    var request = new StaffApprovalRequest();
    request.id = UUID.randomUUID(); request.tenantId = tenantId; request.requestedBy = requester;
    request.requestType = "create"; request.name = name; request.phone = phone; request.email = email;
    request.passwordHash = passwordHash; request.pinHash = pinHash;
    request.roleIds = List.copyOf(roleIds); request.createdAt = Instant.now();
    return request;
  }

  public static StaffApprovalRequest roles(UUID tenantId, UUID requester, UUID targetUserId,
      Collection<UUID> roleIds, Collection<UUID> previousRoleIds) {
    var request = new StaffApprovalRequest();
    request.id = UUID.randomUUID(); request.tenantId = tenantId; request.requestedBy = requester;
    request.requestType = "roles"; request.targetUserId = targetUserId;
    request.roleIds = List.copyOf(roleIds); request.previousRoleIds = List.copyOf(previousRoleIds);
    request.createdAt = Instant.now();
    return request;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getRequestedBy() { return requestedBy; }
  public UUID getTargetUserId() { return targetUserId; }
  public String getRequestType() { return requestType; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getPinHash() { return pinHash; }
  public List<UUID> getRoleIds() { return roleIds; }
  public List<UUID> getPreviousRoleIds() { return previousRoleIds; }
  public String getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getReviewedAt() { return reviewedAt; }
  public UUID getReviewedBy() { return reviewedBy; }
  public void review(UUID reviewer, boolean approved) {
    if (!"pending".equals(status)) throw new IllegalStateException("This request has already been reviewed");
    status = approved ? "approved" : "rejected";
    reviewedAt = Instant.now(); reviewedBy = reviewer;
  }
}
