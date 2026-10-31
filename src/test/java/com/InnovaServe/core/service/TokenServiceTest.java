package com.InnovaServe.core.service;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class TokenServiceTest {
  private final TokenService tokens = new TokenService("test-only-signing-key-for-isolated-unit-tests");
  @Test void roundTripIsTenantBound() {
    UUID tenant=UUID.randomUUID(), user=UUID.randomUUID();
    var claims=tokens.verify(tokens.issue(tenant,user,null));
    assertEquals(tenant.toString(),claims.get("tenant_id")); assertEquals(user.toString(),claims.get("user_id"));
  }
  @Test void rejectsInvalidTokens() {
    for(String token:new String[]{"", "garbage", "a.b", "a.b.c"}) assertNull(tokens.verify(token));
    assertNull(tokens.verify(null));
    String token=tokens.issue(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    assertNull(tokens.verify("x"+token));
    assertNull(new TokenService("different-test-only-signing-key-for-tests").verify(token));
  }
  @Test void rejectsShortSecret() { assertThrows(IllegalArgumentException.class,()->new TokenService("short")); assertThrows(IllegalArgumentException.class,()->new TokenService(null)); }
}
