package com.InnovaServe.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlatformTokenService {
  private final byte[] secret;
  private final ObjectMapper mapper;

  public PlatformTokenService(
      @Value("${app.jwt.secret}") String tenantSecret,
      @Value("${app.platform.jwt-secret:}") String configuredSecret,
      ObjectMapper mapper) {
    if (tenantSecret == null || tenantSecret.length() < 32) {
      throw new IllegalArgumentException("app.jwt.secret must contain at least 32 characters");
    }
    if (configuredSecret != null && !configuredSecret.isBlank()) {
      if (configuredSecret.length() < 32) {
        throw new IllegalArgumentException(
            "app.platform.jwt-secret must contain at least 32 characters");
      }
      secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    } else {
      // Domain-separate platform credentials for development. Production should set a dedicated key.
      secret = derivePlatformSecret(tenantSecret);
    }
    this.mapper = mapper;
  }

  public String issue(UUID adminId) {
    try {
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("scope", "platform");
      claims.put("admin_id", adminId.toString());
      claims.put("exp", System.currentTimeMillis() / 1000 + 28800);
      String payload =
          Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(claims));
      return payload + "." + signature(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Could not issue platform token", e);
    }
  }

  public Map<?, ?> verify(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 2
          || !MessageDigest.isEqual(
              signature(parts[0]).getBytes(StandardCharsets.UTF_8),
              parts[1].getBytes(StandardCharsets.UTF_8))) return null;
      Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
      Object expiry = claims.get("exp");
      if (!"platform".equals(claims.get("scope"))
          || !(expiry instanceof Number n)
          || n.longValue() <= System.currentTimeMillis() / 1000
          || claims.get("admin_id") == null) return null;
      return claims;
    } catch (Exception e) {
      return null;
    }
  }

  private String signature(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] derivePlatformSecret(String tenantSecret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(tenantSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal("InnovaServe:platform-token:v1".getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("Could not derive platform token key", e);
    }
  }
}
