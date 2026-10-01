package com.InnovaServe.platform;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class PlatformTimeConfiguration {
  public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Kolkata");

  @Bean
  Clock platformClock() {
    return Clock.system(BILLING_ZONE);
  }
}
