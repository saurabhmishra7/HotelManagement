package com.InnovaServe.platform.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlatformSubscriptionScheduler {
  private final PlatformSubscriptionService subscriptions;

  public PlatformSubscriptionScheduler(PlatformSubscriptionService subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
  public void applySubscriptionDateTransitions() {
    subscriptions.processDue();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void catchUpTransitionsAfterRestart() {
    subscriptions.processDue();
  }
}
