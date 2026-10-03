package com.InnovaServe.platform.service;

import com.InnovaServe.platform.repository.PublicSignupIntentRepository;
import com.InnovaServe.platform.repository.TenantSubscriptionCheckoutRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PublicSignupIntentCleanup {
  private final PublicSignupIntentRepository intents;
  private final TenantSubscriptionCheckoutRepository checkouts;
  private final Clock clock;

  public PublicSignupIntentCleanup(PublicSignupIntentRepository intents,
      TenantSubscriptionCheckoutRepository checkouts, Clock clock) {
    this.intents = intents;
    this.checkouts = checkouts;
    this.clock = clock;
  }

  @Scheduled(cron = "0 20 * * * *", zone = "Asia/Kolkata")
  @Transactional
  public void expireAbandonedCheckouts() {
    Instant now = clock.instant();
    intents.findAllByStatusAndExpiresAtBefore("pending", now).forEach(intent -> {
      intent.expire();
      intents.save(intent);
    });
    checkouts.findAllByStatusAndExpiresAtBefore("pending", now).forEach(checkout -> {
      checkout.expire();
      checkouts.save(checkout);
    });
  }
}
