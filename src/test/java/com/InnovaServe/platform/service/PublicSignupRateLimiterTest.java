package com.InnovaServe.platform.service;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
class PublicSignupRateLimiterTest {
  @Test void boundaryAndWindowReset() {
    Clock clock=mock(Clock.class); when(clock.instant()).thenReturn(Instant.EPOCH);
    var limiter=new PublicSignupRateLimiter(clock);
    for(int i=0;i<5;i++) limiter.requireAllowed("client");
    assertThrows(PublicSignupRateLimiter.SignupRateLimitException.class,()->limiter.requireAllowed("client"));
    limiter.requireAllowed("other");
    when(clock.instant()).thenReturn(Instant.EPOCH.plusSeconds(901)); limiter.requireAllowed("client");
  }
}
