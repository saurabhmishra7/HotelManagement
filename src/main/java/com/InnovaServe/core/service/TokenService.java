package com.InnovaServe.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final String secret;
  private final ObjectMapper mapper = new ObjectMapper();

  public TokenService(@Value("${app.jwt.secret}") String secret) {
    if (secret == null || secret.length() < 32)
      throw new IllegalArgumentException("app.jwt.secret must contain at least 32 characters");
    this.secret = secret;
  }

  public String issue(UUID tenant, UUID user, UUID role) {
    try {
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("tenant_id", tenant.toString());
      claims.put("user_id", user.toString());
      claims.put("role_id", role == null ? null : role.toString());
      claims.put("exp", System.currentTimeMillis() / 1000 + 900);
      String payload =
          Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(claims));
      return payload + "." + signature(payload);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public Map<?, ?> verify(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 2
          || !java.security.MessageDigest.isEqual(
              signature(parts[0]).getBytes(StandardCharsets.UTF_8),
              parts[1].getBytes(StandardCharsets.UTF_8))) return null;
      Map<?, ?> claims = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
      Object expiry = claims.get("exp");
      if (!(expiry instanceof Number n) || n.longValue() <= System.currentTimeMillis() / 1000)
        return null;
      return claims;
    } catch (Exception e) {
      return null;
    }
  }

  private String signature(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }
}
