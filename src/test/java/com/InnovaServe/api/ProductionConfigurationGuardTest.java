package com.InnovaServe.api;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
class ProductionConfigurationGuardTest {
  private MockEnvironment valid() {
    var e=new MockEnvironment().withProperty("app.jwt.secret","test-only-tenant-secret-at-least-32-chars")
      .withProperty("app.platform.jwt-secret","test-only-platform-secret-at-least-32-chars")
      .withProperty("spring.datasource.password","test-only-db-value");
    for(String key:new String[]{"app.cors.allowed-origins","app.platform.cors.allowed-origins",
      "app.signup.verification-base-url","app.password-reset.base-url","app.staff-invitation.base-url",
      "app.staff-approval.base-url","app.email-verification.base-url"}) e.setProperty(key,"https://staff.example.com");
    return e;
  }
  @Test void acceptsConfiguredProduction() { assertDoesNotThrow(()->new ProductionConfigurationGuard(valid())); }
  @Test void rejectsMissingSecrets() { assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(new MockEnvironment())); }
  @Test void rejectsSharedSigningKeys() {
    var e=valid();e.setProperty("app.platform.jwt-secret",e.getProperty("app.jwt.secret"));
    assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(e));
  }
  @Test void rejectsDemoAndBootstrap() {
    for(String key:new String[]{"app.payments.demo-mode","app.platform.bootstrap.enabled"}) {
      var e=valid().withProperty(key,"true"); assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(e));
    }
  }
  @Test void rejectsUnsafeOriginsAndLinks() {
    for(String value:new String[]{"", "http://staff.example.com", "https://*.example.com", "invalid", "https://user:pass@example.com"}) {
      var e=valid().withProperty("app.cors.allowed-origins",value);
      assertThrows(IllegalStateException.class,()->new ProductionConfigurationGuard(e));
    }
  }
}
