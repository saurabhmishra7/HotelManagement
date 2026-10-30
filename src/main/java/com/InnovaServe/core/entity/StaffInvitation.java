package com.InnovaServe.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "staff_invitation", schema = "core")
public class StaffInvitation {
  @Id private UUID id;
  @Column(name = "tenant_id", nullable = false) private UUID tenantId;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(nullable = false, length = 100) private String name;
  @Column(nullable = false, length = 15) private String phone;
  @Column(nullable = false, length = 150) private String email;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name = "role_ids", nullable = false, columnDefinition = "jsonb") private List<UUID> roleIds;
  @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "accepted_at") private Instant acceptedAt;

  protected StaffInvitation() {}

  public StaffInvitation(UUID tenantId, UUID requestedBy, String name, String phone, String email,
      Collection<UUID> roleIds, String tokenHash, Instant expiresAt) {
    id = UUID.randomUUID(); this.tenantId = tenantId; this.requestedBy = requestedBy;
    this.name = name; this.phone = phone; this.email = email;
    this.roleIds = List.copyOf(roleIds); this.tokenHash = tokenHash;
    this.expiresAt = expiresAt; createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getRequestedBy() { return requestedBy; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getEmail() { return email; }
  public List<UUID> getRoleIds() { return roleIds; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getAcceptedAt() { return acceptedAt; }
  public boolean isPending() { return acceptedAt == null; }
  public boolean matches(String hash, Instant now) {
    return isPending() && expiresAt.isAfter(now) && tokenHash.equals(hash);
  }
  public void renew(String hash, Instant expiry) { tokenHash = hash; expiresAt = expiry; }
  public void accept(Instant now) { acceptedAt = now; }
}
