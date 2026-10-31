package com.InnovaServe.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.service.EmailVerificationService;
import com.InnovaServe.platform.entity.PublicSignupIntent;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.PlatformSettingsRepository;
import com.InnovaServe.platform.repository.PublicSignupIntentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicSignupServiceTest {
  private final PublicSignupIntentRepository intents = mock(PublicSignupIntentRepository.class);
  private final StaffUserRepository users = mock(StaffUserRepository.class);
  private final PlanRepository plans = mock(PlanRepository.class);
  private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
  private final UUID intentId = UUID.randomUUID();
  private final String email = "new.owner@example.com";
  private PublicSignupService service;

  @BeforeEach
  void setup() {
    PublicSignupIntent intent = new PublicSignupIntent("Property", null, null,
        "Owner", "1234567890", email, "password-hash", "pin-hash",
        UUID.randomUUID(), BigDecimal.TEN, "INR", "demo-signup-123",
        Instant.now().plus(Duration.ofMinutes(20)));
    ReflectionTestUtils.setField(intent, "id", intentId);
    when(intents.findById(intentId)).thenReturn(Optional.of(intent));
    when(intents.lockById(intentId)).thenReturn(Optional.of(intent));
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    service = new PublicSignupService(mock(PlatformSettingsRepository.class), plans, intents,
        mock(TenantRepository.class), mock(StaffRoleRepository.class), users,
        mock(PasswordEncoder.class), mock(PlatformSubscriptionService.class),
        mock(RazorpayPaymentService.class), mock(PublicSignupRateLimiter.class),
        mock(com.InnovaServe.core.service.EmailSender.class), mock(org.springframework.beans.factory.ObjectProvider.class), Clock.systemUTC(), true, "http://localhost:3000/", transactionManager);
  }

  @Test
  void paymentCompletionDoesNotTreatItsOwnPendingIntentAsDuplicate() {
    // Reaching the absent-plan check proves the pending intent did not block itself.
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> service.completeDemoPayment(intentId));
    assertEquals("Selected plan is no longer active", error.getMessage());
    verify(intents).existsByOwnerEmailIgnoreCaseAndStatusInAndIdNotAndExpiresAtAfter(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(intentId), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void paymentCompletionStillRejectsAnExistingOwner() {
    when(users.existsOwnerEmailIgnoreCase(email)).thenReturn(true);
    IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> service.completeDemoPayment(intentId));
    assertEquals("This email already owns a property or has a signup in progress", error.getMessage());
  }
}
