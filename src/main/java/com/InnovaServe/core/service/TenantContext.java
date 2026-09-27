package com.InnovaServe.core.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {
  public UUID tenantId() {
    return claim("tenant_id");
  }

  public UUID userId() {
    return claim("user_id");
  }

  private UUID claim(String name) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
        || !(auth.getPrincipal() instanceof Map<?, ?> claims)
        || claims.get(name) == null)
      throw new IllegalStateException("Authenticated token is missing claim: " + name);
    return UUID.fromString(claims.get(name).toString());
  }
}
