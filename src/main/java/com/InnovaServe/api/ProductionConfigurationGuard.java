package com.InnovaServe.api;
import java.net.URI;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
/** Fail closed on unsafe production flags; values are never written to logs. */
@Configuration
@Profile("prod")
public class ProductionConfigurationGuard {
  public ProductionConfigurationGuard(Environment env) {
    for (String key : new String[]{"app.jwt.secret", "app.platform.jwt-secret", "spring.datasource.password"}) {
      String value = env.getProperty(key, "");
      if (value.isBlank() || (key.contains("secret") && value.length() < 32))
        throw new IllegalStateException("Missing or invalid production setting: " + key);
    }
    if (env.getProperty("app.jwt.secret").equals(env.getProperty("app.platform.jwt-secret")))
      throw new IllegalStateException("Production tenant and platform signing keys must differ");
    for (String key : new String[]{"app.payments.demo-mode", "app.platform.bootstrap.enabled"})
      if (env.getProperty(key, Boolean.class, false)) throw new IllegalStateException("Unsafe production setting: " + key);
    for (String key : new String[]{"app.cors.allowed-origins", "app.platform.cors.allowed-origins"}) {
      String value = env.getProperty(key, "");
      if (value.isBlank()) throw new IllegalStateException("Production origins required: " + key);
      for (String origin : value.split(",")) requireHttps(origin.trim(), key);
    }
    for (String key : new String[]{"app.signup.verification-base-url", "app.password-reset.base-url",
        "app.staff-invitation.base-url", "app.staff-approval.base-url", "app.email-verification.base-url"})
      requireHttps(env.getProperty(key, ""), key);
  }
  private static void requireHttps(String value, String key) {
    try {
      URI uri = URI.create(value);
      if (!"https".equals(uri.getScheme()) || uri.getHost() == null || value.contains("*") || uri.getUserInfo() != null)
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException ex) { throw new IllegalStateException("HTTPS URL required: " + key); }
  }
}
