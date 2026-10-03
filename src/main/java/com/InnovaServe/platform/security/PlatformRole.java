package com.InnovaServe.platform.security;

import java.util.Set;

public enum PlatformRole {
  OWNER(
      Set.of(
          PlatformPermission.TENANTS_READ,
          PlatformPermission.PLANS_READ,
          PlatformPermission.PLANS_MANAGE,
          PlatformPermission.SUBSCRIPTIONS_MANAGE,
          PlatformPermission.ADMINS_MANAGE,
          PlatformPermission.AUDIT_READ,
          PlatformPermission.SETTINGS_MANAGE)),
  TENANT_MANAGER(
      Set.of(
          PlatformPermission.TENANTS_READ,
          PlatformPermission.PLANS_READ,
          PlatformPermission.SUBSCRIPTIONS_MANAGE)),
  PLAN_MANAGER(
      Set.of(
          PlatformPermission.TENANTS_READ,
          PlatformPermission.PLANS_READ,
          PlatformPermission.PLANS_MANAGE,
          PlatformPermission.SUBSCRIPTIONS_MANAGE));

  private final Set<PlatformPermission> permissions;

  PlatformRole(Set<PlatformPermission> permissions) {
    this.permissions = permissions;
  }

  public Set<PlatformPermission> permissions() {
    return permissions;
  }
}
