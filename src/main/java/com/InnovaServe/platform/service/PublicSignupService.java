package com.InnovaServe.platform.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.core.service.EmailVerificationService;
import com.InnovaServe.platform.entity.PlatformSettings;
import com.InnovaServe.platform.entity.PublicSignupIntent;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.TenantSubscription;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.PlatformSettingsRepository;
import com.InnovaServe.platform.repository.PublicSignupIntentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PublicSignupService {
  private static final String GLOBAL_SETTINGS_ID = "GLOBAL";
  private final PlatformSettingsRepository settings;
  private final PlanRepository plans;
  private final PublicSignupIntentRepository intents;
  private final TenantRepository tenants;
  private final StaffRoleRepository roles;
  private final StaffUserRepository users;
  private final PasswordEncoder passwords;
  private final PlatformSubscriptionService subscriptions;
  private final RazorpayPaymentService payments;
  private final PublicSignupRateLimiter rateLimiter;
  private final EmailVerificationService verification;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public PublicSignupService(
      PlatformSettingsRepository settings,
      PlanRepository plans,
      PublicSignupIntentRepository intents,
      TenantRepository tenants,
      StaffRoleRepository roles,
      StaffUserRepository users,
      PasswordEncoder passwords,
      PlatformSubscriptionService subscriptions,
      RazorpayPaymentService payments,
      PublicSignupRateLimiter rateLimiter,
      EmailVerificationService verification,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.settings = settings;
    this.plans = plans;
    this.intents = intents;
    this.tenants = tenants;
    this.roles = roles;
    this.users = users;
    this.passwords = passwords;
    this.subscriptions = subscriptions;
    this.payments = payments;
    this.rateLimiter = rateLimiter;
    this.verification = verification;
    this.clock = clock;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> signupMode() {
    PlatformSettings current = settings.findById(GLOBAL_SETTINGS_ID)
        .orElseThrow(() -> new IllegalStateException("Global platform settings are missing"));
    return Map.of("mode", current.getSignupMode());
  }

  @Transactional
  public Map<String, Object> signup(SignupRequest request, String ipAddress) {
    rateLimiter.requireAllowed(ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress);
    validate(request);
    PlatformSettings current = settings.findById(GLOBAL_SETTINGS_ID)
        .orElseThrow(() -> new IllegalStateException("Global platform settings are missing"));
    String email = request.ownerEmail().trim().toLowerCase(Locale.ROOT);
    requireAvailableOwnerEmail(email);

    if ("trial".equals(current.getSignupMode())) {
      if (request.planId() != null)
        throw new IllegalArgumentException("plan_id must not be sent in trial signup mode");
      if (current.getTrialPlanId() == null)
        throw new IllegalStateException("Trial signup is not configured with a trial plan");
      Plan trialPlan = plans.findById(current.getTrialPlanId()).filter(Plan::isActive)
          .orElseThrow(() -> new IllegalStateException("Configured trial plan is not active"));
      Tenant tenant = createTenant(request.name(), request.gstin(), request.address(),
          request.ownerName(), request.ownerPhone(), email, passwords.encode(request.ownerPassword()),
          passwords.encode(request.ownerPin()));
      StaffUser owner = users.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(tenant.getId(), email)
          .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
      subscriptions.activateSignup(tenant.getId(), trialPlan, BigDecimal.ZERO, true,
          current.getTrialDurationDays());
      verification.send(owner, tenant.getTenantCode());
      return signupResult(tenant, owner, "trial", "active", trialPlan);
    }

    if (request.planId() == null)
      throw new IllegalArgumentException("plan_id is required in standard signup mode");
    Plan plan = plans.findById(request.planId()).filter(Plan::isActive)
        .orElseThrow(() -> new NoSuchElementException("Active plan not found"));
    if (plan.getPrice().signum() == 0) {
      Tenant tenant = createTenant(request.name(), request.gstin(), request.address(),
          request.ownerName(), request.ownerPhone(), email, passwords.encode(request.ownerPassword()),
          passwords.encode(request.ownerPin()));
      StaffUser owner = users.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(tenant.getId(), email)
          .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
      subscriptions.activateSignup(tenant.getId(), plan, BigDecimal.ZERO, false, 0);
      verification.send(owner, tenant.getTenantCode());
      return signupResult(tenant, owner, "standard", "active", plan);
    }
    if (!payments.isConfigured())
      throw new RazorpayPaymentService.PaymentProviderUnavailableException();

    String receipt = "signup-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    RazorpayPaymentService.Order order = payments.createOrder(plan.getPrice(), plan.getCurrency(), receipt);
    PublicSignupIntent intent = intents.save(new PublicSignupIntent(
        request.name().trim(), blankToNull(request.gstin()), blankToNull(request.address()),
        request.ownerName().trim(), request.ownerPhone().trim(), email,
        passwords.encode(request.ownerPassword()), passwords.encode(request.ownerPin()),
        plan.getId(), plan.getPrice(), plan.getCurrency(), order.orderId(),
        Instant.now(clock).plus(Duration.ofMinutes(20))));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("mode", "standard");
    result.put("signup_status", "payment_required");
    result.put("signup_intent_id", intent.getId());
    result.put("key_id", order.keyId());
    result.put("order_id", order.orderId());
    result.put("amount", order.amountMinor());
    result.put("currency", order.currency());
    result.put("owner_name", request.ownerName().trim());
    result.put("owner_email", email);
    result.put("property_name", request.name().trim());
    return result;
  }

  public Map<String, Object> completePayment(PaymentCompletion request) {
    PublicSignupIntent intent = intents.findById(request.signupIntentId())
        .orElseThrow(() -> new NoSuchElementException("Signup checkout not found"));
    if (!intent.getPaymentOrderId().equals(request.orderId()))
      throw new IllegalArgumentException("Payment order does not match this signup");
    if ("completed".equals(intent.getStatus()))
      return completedResult(intent.getTenantId());
    if (!"pending".equals(intent.getStatus()) || !intent.getExpiresAt().isAfter(Instant.now(clock)))
      throw new IllegalArgumentException("Signup checkout has expired");
    if (users.existsOwnerEmailIgnoreCase(intent.getOwnerEmail()))
      throw new IllegalStateException("This email already owns a property");
    payments.requireCapturedPayment(intent.getPaymentOrderId(), request.paymentId(),
        request.signature(), intent.getAmount(), intent.getCurrency());
    return transactions.execute(status -> completePaidSignup(request.signupIntentId()));
  }

  @Transactional
  protected Map<String, Object> completePaidSignup(UUID intentId) {
    PublicSignupIntent intent = intents.lockById(intentId)
        .orElseThrow(() -> new NoSuchElementException("Signup checkout not found"));
    if ("completed".equals(intent.getStatus())) return completedResult(intent.getTenantId());
    if (!"pending".equals(intent.getStatus()) || !intent.getExpiresAt().isAfter(Instant.now(clock)))
      throw new IllegalArgumentException("Signup checkout has expired");
    requireAvailableOwnerEmail(intent.getOwnerEmail());
    Plan plan = plans.findById(intent.getPlanId()).filter(Plan::isActive)
        .orElseThrow(() -> new IllegalStateException("Selected plan is no longer active"));
    Tenant tenant = createTenant(intent.getPropertyName(), intent.getGstin(), intent.getAddress(),
        intent.getOwnerName(), intent.getOwnerPhone(), intent.getOwnerEmail(),
        intent.getOwnerPasswordHash(), intent.getOwnerPinHash());
    StaffUser owner = users.findByTenantIdAndEmailIgnoreCaseAndActiveTrue(
        tenant.getId(), intent.getOwnerEmail())
        .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
    subscriptions.activateSignup(tenant.getId(), plan, intent.getAmount(), false, 0);
    intent.complete(tenant.getId());
    intents.save(intent);
    verification.send(owner, tenant.getTenantCode());
    return signupResult(tenant, owner, "standard", "active", plan);
  }

  private Tenant createTenant(String propertyName, String gstin, String address,
      String ownerName, String ownerPhone, String email, String passwordHash, String pinHash) {
    Tenant entity = new Tenant(propertyName.trim(), blankToNull(gstin), blankToNull(address));
    entity.setPrimaryOwnerEmail(email);
    Tenant tenant = tenants.save(entity);
    List<String> permissions = Arrays.stream(Permission.values()).map(Enum::name).sorted().toList();
    StaffRole ownerRole = roles.save(new StaffRole(tenant.getId(), "OWNER", permissions));
    users.save(new StaffUser(tenant.getId(), ownerName.trim(), ownerPhone.trim(),
        email, passwordHash, pinHash, ownerRole.getId()));
    return tenant;
  }

  private StaffUser findOwner(UUID tenantId) {
    return users.findAllByTenantIdOrderByName(tenantId).stream().findFirst()
        .orElseThrow(() -> new IllegalStateException("New owner user was not created"));
  }

  private void requireAvailableOwnerEmail(String email) {
    if (users.existsOwnerEmailIgnoreCase(email)
        || intents.existsByOwnerEmailIgnoreCaseAndStatus(email, "pending"))
      throw new IllegalStateException("This email already owns a property or has a signup in progress");
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
    result.put("email_verification_blocking", false);
    return result;
  }

  private static void validate(SignupRequest request) {
    if (request == null || request.name() == null || request.name().isBlank()
        || request.name().trim().length() > 200)
      throw new IllegalArgumentException("Property name is required and must be at most 200 characters");
    if (request.ownerName() == null || request.ownerName().isBlank()
        || request.ownerName().trim().length() > 100)
      throw new IllegalArgumentException("Owner name is required and must be at most 100 characters");
    if (request.ownerPhone() == null || !request.ownerPhone().trim().matches("[0-9+() -]{7,15}"))
      throw new IllegalArgumentException("A valid owner phone is required");
    if (request.ownerEmail() == null || request.ownerEmail().trim().length() > 150
        || !request.ownerEmail().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
      throw new IllegalArgumentException("A valid owner email is required");
    if (request.ownerPassword() == null || request.ownerPassword().length() < 12)
      throw new IllegalArgumentException("Password must be at least 12 characters");
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
