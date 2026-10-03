package com.InnovaServe.platform.service;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class PublicSignupRateLimiter {
  private static final int LIMIT = 5;
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private final Clock clock;
  private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

  public PublicSignupRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void requireAllowed(String address) {
    long window = Math.floorDiv(clock.instant().getEpochSecond(), WINDOW.toSeconds());
    WindowCounter counter = counters.compute(address, (key, existing) ->
        existing == null || existing.window() != window
            ? new WindowCounter(window, new AtomicInteger())
            : existing);
    if (counter.count().incrementAndGet() > LIMIT)
      throw new SignupRateLimitException();
    if (counters.size() > 10_000) counters.entrySet().removeIf(entry -> entry.getValue().window() < window - 1);
  }

  private record WindowCounter(long window, AtomicInteger count) {}

  public static class SignupRateLimitException extends RuntimeException {
    public SignupRateLimitException() {
      super("Too many signup attempts. Please try again in a few minutes.");
    }
  }
}
