package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.core.service.AccountValidation;
import com.InnovaServe.core.service.EmailSender;
import com.InnovaServe.core.service.EmailDeliveryException;
import com.InnovaServe.core.service.DefaultStaffRoleSeeder;
import com.InnovaServe.platform.entity.PlatformSettings;
import com.InnovaServe.platform.entity.PublicSignupIntent;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.PlatformSettingsRepository;
import com.InnovaServe.platform.repository.PublicSignupIntentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PublicSignupService {
  private static final Logger log = LoggerFactory.getLogger(PublicSignupService.class);
  private static final String GLOBAL_SETTINGS_ID = "GLOBAL";
  private static final Duration DRAFT_LIFETIME = Duration.ofHours(24);
  private static final Duration EMAIL_LINK_LIFETIME = Duration.ofMinutes(30);
  private static final List<String> OPEN_STATUSES = List.of(
      "pending", "email_pending", "email_verified", "payment_pending");
  private static final SecureRandom RANDOM = new SecureRandom();
  private final PlatformSettingsRepository settings;
  private final PlanRepository plans;
  private final PublicSignupIntentRepository intents;
  private final TenantRepository tenants;
  private final StaffRoleRepository roles;
  private final DefaultStaffRoleSeeder defaultRoles;
  private final StaffUserRepository users;
  private final PasswordEncoder passwords;
  private final PlatformSubscriptionService subscriptions;
  private final RazorpayPaymentService payments;
  private final PublicSignupRateLimiter rateLimiter;
  private final EmailSender emailSender;
  private final SignupPhoneVerifier phoneVerifier;
  private final String verificationBaseUrl;
  private final Clock clock;
  private final boolean demoPaymentsEnabled;
  private final TransactionTemplate transactions;

  public PublicSignupService(
      PlatformSettingsRepository settings,
      PlanRepository plans,
      PublicSignupIntentRepository intents,
      TenantRepository tenants,
      StaffRoleRepository roles,
      DefaultStaffRoleSeeder defaultRoles,
      StaffUserRepository users,
      PasswordEncoder passwords,
      PlatformSubscriptionService subscriptions,
      RazorpayPaymentService payments,
      PublicSignupRateLimiter rateLimiter,
      EmailSender emailSender,
      ObjectProvider<SignupPhoneVerifier> phoneVerifiers,
      Clock clock,
      @Value("${app.payments.demo-mode:false}") boolean demoPaymentsEnabled,
      @Value("${app.signup.verification-base-url:${APP_SIGNUP_VERIFICATION_BASE_URL:http://localhost:3000/}}")
          String verificationBaseUrl,
      PlatformTransactionManager transactionManager) {
    this.settings = settings;
    this.plans = plans;
    this.intents = intents;
    this.tenants = tenants;
    this.roles = roles;
    this.defaultRoles = defaultRoles;
    this.users = users;
    this.passwords = passwords;
    this.subscriptions = subscriptions;
    this.payments = payments;
    this.rateLimiter = rateLimiter;
    this.emailSender = emailSender;
    this.phoneVerifier = phoneVerifiers.getIfAvailable();
    this.verificationBaseUrl = verificationBaseUrl;
    this.clock = clock;
    this.demoPaymentsEnabled = demoPaymentsEnabled;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> signupMode() {
    PlatformSettings current = settings.findById(GLOBAL_SETTINGS_ID)
        .orElseThrow(() -> new IllegalStateException("Global platform settings are missing"));
    List<String> trialModules = "trial".equals(current.getSignupMode()) && current.getTrialPlanId() != null
        ? plans.findById(current.getTrialPlanId()).filter(Plan::isActive)
            .map(plan -> plan.getModules().stream().sorted().toList()).orElse(List.of())
        : List.of();
    return Map.of("mode", current.getSignupMode(), "trial_modules", trialModules);
  }

  @Transactional
  public Map<String, Object> signup(SignupRequest request, String ipAddress) {
    rateLimiter.requireAllowed(ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
    validate(request);
    PlatformSettings current = settings.findById(GLOBAL_SETTINGS_ID)
        .orElseThrow(() -> new IllegalStateException("Global platform settings are missing"));
    String email = request.ownerEmail().trim().toLowerCase(Locale.ROOT);
    requireAvailableOwnerEmail(email);
    boolean trial = "trial".equals(current.getSignupMode());
    if (trial && request.planId() != null)
      throw new IllegalArgumentException("plan_id must not be sent in trial signup mode");
    if (trial && current.getTrialPlanId() == null)
      throw new IllegalStateException("Trial signup is not configured with a trial plan");
    if (!trial && request.planId() == null)
      throw new IllegalArgumentException("plan_id is required in standard signup mode");
    Plan plan = plans.findById(trial ? current.getTrialPlanId() : request.planId())
        .filter(Plan::isActive)
        .orElseThrow(() -> new NoSuchElementException("Active plan not found"));
    if (!emailSender.isAvailable())
      throw new EmailDeliveryException("Signup email verification is not configured");
    Instant now = clock.instant();
    PublicSignupIntent intent = PublicSignupIntent.pendingVerification(
        request.name().trim(), blankToNull(request.gstin()), blankToNull(request.address()),
        request.ownerName().trim(), request.ownerPhone().trim(), email,
        passwords.encode(request.ownerPassword()), passwords.encode(request.ownerPin()),
        plan.getId(), trial ? BigDecimal.ZERO : plan.getPrice(), plan.getCurrency(),
        trial ? "trial" : "standard", now.plus(DRAFT_LIFETIME));
    intents.saveAndFlush(intent);
    sendVerification(intent, now);
    return Map.of("signup_status", "email_pending", "signup_intent_id", intent.getId(),
        "owner_email", email, "expires_at", intent.getExpiresAt());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> signupProgress(UUID id) {
    if (id == null) throw new IllegalArgumentException("Signup reference is required");
    var intent = intents.findById(id).orElse(null);
    // Only coarse progress: never expose tokens, account details or payment credentials.
    if (intent == null) return Map.of("signup_status", "expired");
    if (intent.getTenantId() != null) return Map.of("signup_status", "completed");
    if (!intent.getExpiresAt().isAfter(clock.instant())) return Map.of("signup_status", "expired");
    return Map.of("signup_status", intent.getEmailVerifiedAt() == null ? "email_pending"
        : "payment_pending".equals(intent.getStatus()) ? "payment_pending" : "email_verified");
  }

  @Transactional
  public Map<String, Object> resendVerification(UUID id, String ipAddress) {
    rateLimiter.requireAllowed(ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
    PublicSignupIntent intent = intents.lockById(id)
        .orElseThrow(() -> new NoSuchElementException("Pending signup not found"));
    sendVerification(intent, clock.instant());
    return Map.of("sent", true);
  }

  @Transactional
  public Map<String, Object> confirmSignupEmail(String token) {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Verification link is invalid or expired");
    String tokenHash = hash(token);
    PublicSignupIntent intent = intents.lockByEmailVerificationTokenHash(tokenHash)
        .orElseThrow(() -> new IllegalArgumentException("Verification link is invalid or expired"));
    String continuation = newToken();
    if (!intent.verifyEmail(tokenHash, hash(continuation), clock.instant()))
      throw new IllegalArgumentException("Verification link is invalid or expired");
    return Map.of("signup_status", "email_verified", "signup_intent_id", intent.getId(),
        "continuation_token", continuation, "phone_verification_required", phoneVerifier != null,
        "owner_phone", intent.getOwnerPhone());
  }

  @Transactional
  public Map<String, Object> verifySignupPhone(UUID id, String continuation, String providerProof) {
    PublicSignupIntent intent = verifiedDraft(id, continuation);
    if (phoneVerifier == null) throw new IllegalStateException("Phone OTP is not configured yet");
    phoneVerifier.verify(providerProof, intent.getOwnerPhone());
    intent.verifyPhone(clock.instant());
    return Map.of("phone_verified", true);
  }

  @Transactional
  public Map<String, Object> continueSignup(UUID id, String continuation) {
    PublicSignupIntent intent = verifiedDraft(id, continuation);
    if (!"pending".equals(intent.getStatus()) && phoneVerifier != null && intent.getPhoneVerifiedAt() == null)
      return Map.of("signup_status", "phone_verification_required", "owner_phone", intent.getOwnerPhone());
    if ("payment_pending".equals(intent.getStatus())) return paymentResult(intent, null);
    Plan plan = plans.findById(intent.getPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new IllegalStateException("Selected plan is no longer active"));
    if ("trial".equals(intent.getSignupMode()) || intent.getAmount().signum() == 0)
      return finishSignup(intent.getId());
    if (demoPaymentsEnabled) {
      intent.beginPayment("demo-signup-" + UUID.randomUUID(), clock.instant());
      return paymentResult(intent, null);
    }
    if (!payments.isConfigured()) throw new RazorpayPaymentService.PaymentProviderUnavailableException();
    String receipt = "signup-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    RazorpayPaymentService.Order order = payments.createOrder(intent.getAmount(), intent.getCurrency(), receipt);
    intent.beginPayment(order.orderId(), clock.instant());
    return paymentResult(intent, order.keyId());
  }

  public Map<String, Object> completePayment(PaymentCompletion request) {
    PublicSignupIntent intent = intents.findById(request.signupIntentId())
        .orElseThrow(() -> new NoSuchElementException("Signup checkout not found"));
    if (intent.getPaymentOrderId() == null || !intent.getPaymentOrderId().equals(request.orderId()))
      throw new IllegalArgumentException("Payment order does not match this signup");
    if ("completed".equals(intent.getStatus()))
      return completedResult(intent.getTenantId());
    if (!("pending".equals(intent.getStatus()) || "payment_pending".equals(intent.getStatus()))
        || !intent.getExpiresAt().isAfter(clock.instant()))
      throw new IllegalArgumentException("Signup checkout has expired");
    if (users.existsOwnerEmailIgnoreCase(intent.getOwnerEmail()))
      throw new IllegalStateException("This email already owns a property");
    payments.requireCapturedPayment(intent.getPaymentOrderId(), request.paymentId(),
        request.signature(), intent.getAmount(), intent.getCurrency());
    return transactions.execute(status -> finishSignup(request.signupIntentId()));
  }

  public Map<String, Object> completeDemoPayment(UUID intentId) {
    if (!demoPaymentsEnabled)
      throw new IllegalStateException("Demo payment confirmation is disabled");
    PublicSignupIntent intent = intents.findById(intentId)
        .orElseThrow(() -> new NoSuchElementException("Signup checkout not found"));
    if (intent.getPaymentOrderId() == null || !intent.getPaymentOrderId().startsWith("demo-signup-"))
      throw new IllegalArgumentException("This signup is not using demo payment");
    return transactions.execute(status -> finishSignup(intentId));
  }

  private Map<String, Object> finishSignup(UUID intentId) {
    PublicSignupIntent intent = intents.lockById(intentId)
        .orElseThrow(() -> new NoSuchElementException("Signup checkout not found"));
    if ("completed".equals(intent.getStatus())) return completedResult(intent.getTenantId());
    if (!("pending".equals(intent.getStatus()) || "email_verified".equals(intent.getStatus())
        || "payment_pending".equals(intent.getStatus())) || !intent.getExpiresAt().isAfter(clock.instant()))
      throw new IllegalArgumentException("Signup checkout has expired");
    if (!"pending".equals(intent.getStatus()) && intent.getEmailVerifiedAt() == null)
      throw new IllegalStateException("Owner email must be verified before signup can finish");
    if (!"pending".equals(intent.getStatus()) && phoneVerifier != null && intent.getPhoneVerifiedAt() == null)
      throw new IllegalStateException("Owner phone must be verified before signup can finish");
    if (intent.getAmount().signum() > 0 && !"pending".equals(intent.getStatus())
        && !"payment_pending".equals(intent.getStatus()))
      throw new IllegalStateException("Payment must be completed before signup can finish");
    requireAvailableOwnerEmail(intent.getOwnerEmail(), intent.getId());
    Plan plan = plans.findById(intent.getPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new IllegalStateException("Selected plan is no longer active"));
    Tenant tenant = createTenant(intent.getPropertyName(), intent.getGstin(), intent.getAddress(),
        intent.getOwnerName(), intent.getOwnerPhone(), intent.getOwnerEmail(),
        intent.getOwnerPasswordHash(), intent.getOwnerPinHash());
    StaffUser owner = users.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(
        tenant.getId(), intent.getOwnerEmail())
        .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
    if (intent.getEmailVerifiedAt() != null) owner.markEmailVerified(intent.getEmailVerifiedAt());
    if (intent.getPhoneVerifiedAt() != null) owner.markPhoneVerified(intent.getPhoneVerifiedAt());
    boolean trial = "trial".equals(intent.getSignupMode());
    int trialDays = trial ? settings.findById(GLOBAL_SETTINGS_ID)
        .orElseThrow(() -> new IllegalStateException("Global platform settings are missing"))
        .getTrialDurationDays() : 0;
    subscriptions.activateSignup(tenant.getId(), plan, intent.getAmount(), trial, trialDays);
    intent.complete(tenant.getId());
    intents.save(intent);
    sendPropertyCodeAfterCommit(intent.getOwnerEmail(), tenant.getTenantCode());
    return signupResult(tenant, owner, intent.getSignupMode(), "active", plan);
  }

  private void sendPropertyCodeAfterCommit(String email, String tenantCode) {
    Runnable deliver = () -> {
      try {
        emailSender.send(email, "Your InnovaServe property code",
            "Your property is ready. Sign in to the InnovaServe tenant portal using this property code:\n\n"
                + tenantCode + "\n\nTenant portal: " + verificationBaseUrl
                + "\n\nUse the owner email and password you chose during signup.");
      } catch (RuntimeException exception) {
        log.error("Could not email the property code after signup activation", exception);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { deliver.run(); }
      });
    } else {
      deliver.run();
    }
  }

  private Tenant createTenant(String propertyName, String gstin, String address,
      String ownerName, String ownerPhone, String email, String passwordHash, String pinHash) {
    Tenant entity = new Tenant(propertyName.trim(), blankToNull(gstin), blankToNull(address));
    entity.setPrimaryOwnerEmail(email);
    Tenant tenant = tenants.save(entity);
    List<String> permissions = Arrays.stream(Permission.values()).map(Enum::name).sorted().toList();
    StaffRole ownerRole = roles.save(new StaffRole(tenant.getId(), "OWNER", permissions));
    defaultRoles.ensureForTenant(tenant.getId());
    users.save(new StaffUser(tenant.getId(), ownerName.trim(), ownerPhone.trim(),
        email, passwordHash, pinHash, ownerRole.getId()));
    return tenant;
  }

  private StaffUser findOwner(UUID tenantId) {
    return users.findAllByTenantIdOrderByName(tenantId).stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
  }

  private void requireAvailableOwnerEmail(String email) {
    requireAvailableOwnerEmail(email, null);
  }

  private void requireAvailableOwnerEmail(String email, UUID currentIntentId) {
    Instant now = clock.instant();
    intents.findAllByOwnerEmailIgnoreCaseAndStatusInAndExpiresAtBefore(email, OPEN_STATUSES, now)
        .forEach(PublicSignupIntent::expire);
    intents.flush();
    if (users.existsOwnerEmailIgnoreCase(email)
        || (currentIntentId == null
            ? intents.existsByOwnerEmailIgnoreCaseAndStatusInAndExpiresAtAfter(email, OPEN_STATUSES, now)
            : intents.existsByOwnerEmailIgnoreCaseAndStatusInAndIdNotAndExpiresAtAfter(
                email, OPEN_STATUSES, currentIntentId, now)))
      throw new IllegalStateException("This email already owns a property or has a signup in progress");
  }

  private PublicSignupIntent verifiedDraft(UUID id, String continuation) {
    if (id == null || continuation == null || continuation.isBlank())
      throw new IllegalArgumentException("Signup verification has expired. Start again.");
    PublicSignupIntent intent = intents.lockById(id)
        .orElseThrow(() -> new NoSuchElementException("Signup not found"));
    if (!intent.canContinue(hash(continuation), clock.instant()))
      throw new IllegalArgumentException("Signup verification has expired. Start again.");
    return intent;
  }

  private void sendVerification(PublicSignupIntent intent, Instant now) {
    if (!emailSender.isAvailable())
      throw new EmailDeliveryException("Signup email verification is not configured");
    String token = newToken();
    if (!intent.issueEmailToken(hash(token), now, now.plus(EMAIL_LINK_LIFETIME)))
      throw new IllegalStateException("Wait one minute before requesting another link, or start signup again after it expires");
    String separator = verificationBaseUrl.contains("?") ? "&" : "?";
    String link = verificationBaseUrl + separator + "signup_verify_token=" + token
        + "&signup_intent_id=" + intent.getId();
    emailSender.send(intent.getOwnerEmail(), "Verify your InnovaServe tenant signup",
        "Open this link in the InnovaServe tenant portal to verify your email and continue setting up "
        + intent.getPropertyName() + ".\n\n"
        + link + "\n\nThis link is valid for 30 minutes and can be used once. "
        + "No payment or property activation happens until you confirm it.");
  }

  private Map<String, Object> paymentResult(PublicSignupIntent intent, String keyId) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", intent.getSignupMode());
    result.put("signup_status", "payment_required");
    result.put("demo_payment", intent.getPaymentOrderId().startsWith("demo-signup-"));
    result.put("signup_intent_id", intent.getId());
    result.put("order_id", intent.getPaymentOrderId());
    result.put("key_id", keyId == null ? payments.keyId() : keyId);
    result.put("amount", intent.getPaymentOrderId().startsWith("demo-signup-")
        ? intent.getAmount() : intent.getAmount().movePointRight(2).longValueExact());
    result.put("currency", intent.getCurrency());
    result.put("owner_name", intent.getOwnerName());
    result.put("owner_email", intent.getOwnerEmail());
    result.put("property_name", intent.getPropertyName());
    return result;
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not hash signup token", exception);
    }
  }

  private Map<String, Object> completedResult(UUID tenantId) {
    Tenant tenant = tenants.findById(tenantId)
        .orElseThrow(() -> new NoSuchElementException("Signup tenant not found"));
    StaffUser owner = findOwner(tenantId);
    Plan plan = plans.findById(subscriptions.history(tenantId).get(0).getPlanId()).orElse(null);
    return signupResult(tenant, owner, "standard", "active", plan);
  }

  private static Map<String, Object> signupResult(
      Tenant tenant, StaffUser owner, String mode, String status, Plan plan) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", mode);
    result.put("signup_status", status);
    result.put("tenant_id", tenant.getId());
    result.put("tenant_name", tenant.getName());
    result.put("tenant_code", tenant.getTenantCode());
    result.put("modules", plan == null ? List.of() : plan.getModules().stream().sorted().toList());
    result.put("owner", Map.of("id", owner.getId(), "name", owner.getName(),
        "phone", owner.getPhone(), "email", owner.getEmail()));
    result.put("email_verification_blocking", true);
    return result;
  }

  private static void validate(SignupRequest request) {
    if (request == null || request.name() == null || request.name().isBlank()
        || request.name().trim().length() > 200)
      throw new IllegalArgumentException("Property name is required and must be at most 200 characters");
    if (request.ownerName() == null || request.ownerName().isBlank()
        || request.ownerName().trim().length() > 100)
      throw new IllegalArgumentException("Owner name is required and must be at most 100 characters");
    AccountValidation.requireMobile(request.ownerPhone());
    if (request.ownerEmail() == null || request.ownerEmail().trim().length() > 150)
      throw new IllegalArgumentException("A valid owner email is required");
    AccountValidation.requireEmail(request.ownerEmail());
    AccountValidation.requirePassword(request.ownerPassword(), 12);
    if (request.ownerPin() == null || !request.ownerPin().matches("[0-9]{4,8}"))
      throw new IllegalArgumentException("Owner PIN must contain 4 to 8 digits");
    if (request.gstin() != null && request.gstin().length() > 15)
      throw new IllegalArgumentException("GSTIN must be at most 15 characters");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record SignupRequest(
      String name, String gstin, String address,
      @JsonProperty("owner_name") String ownerName,
      @JsonProperty("owner_phone") String ownerPhone,
      @JsonProperty("owner_email") String ownerEmail,
      @JsonProperty("owner_password") String ownerPassword,
      @JsonProperty("owner_pin") String ownerPin,
      @JsonProperty("plan_id") UUID planId) {}

  public record PaymentCompletion(
      @JsonProperty("signup_intent_id") UUID signupIntentId,
      @JsonProperty("order_id") String orderId,
      @JsonProperty("payment_id") String paymentId,
      String signature) {}
}
