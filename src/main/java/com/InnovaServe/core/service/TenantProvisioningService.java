package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.entity.Tenant;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.Permission;
import com.InnovaServe.platform.entity.Plan;
import com.InnovaServe.platform.entity.SubscriptionRequest;
import com.InnovaServe.platform.repository.PlanRepository;
import com.InnovaServe.platform.repository.SubscriptionRequestRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {
  private final TenantRepository tenants;
  private final StaffRoleRepository roles;
  private final StaffUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final ModuleEntitlementService modules;
  private final PlanRepository plans;
  private final SubscriptionRequestRepository subscriptionRequests;

  public TenantProvisioningService(
      TenantRepository tenants,
      StaffRoleRepository roles,
      StaffUserRepository users,
      PasswordEncoder passwordEncoder,
      ModuleEntitlementService modules,
      PlanRepository plans,
      SubscriptionRequestRepository subscriptionRequests) {
    this.tenants = tenants;
    this.roles = roles;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.modules = modules;
    this.plans = plans;
    this.subscriptionRequests = subscriptionRequests;
  }

  @Transactional
  public ProvisionedTenant create(ProvisionTenant request) {
    Plan selectedPlan = null;
    if (request.selectedPlanId() != null) {
      if (request.subscriptionRequestMessage() != null
          && request.subscriptionRequestMessage().length() > 1000) {
        throw new IllegalArgumentException(
            "Subscription request message must be at most 1000 characters");
      }
      selectedPlan =
          plans.findById(request.selectedPlanId())
              .filter(Plan::isActive)
              .orElseThrow(
                  () -> new NoSuchElementException("Selected active plan not found"));
      Set<String> selectedModules =
          request.modules().stream().map(ModuleType::key).collect(Collectors.toSet());
      if (!selectedModules.equals(selectedPlan.getModules())) {
        throw new IllegalArgumentException("Selected modules must match the chosen plan");
      }
    }
    Tenant tenant =
        tenants.save(new Tenant(request.name().trim(), request.gstin(), request.address()));
    // Signup always selects a plan; the no-plan branch remains for existing
    // provisioning-key operator integrations. Paid access waits for confirmation.
    modules.replaceModules(tenant.getId(), selectedPlan == null ? request.modules() : Set.of());
    List<String> permissions =
        java.util.Arrays.stream(Permission.values()).map(Enum::name).sorted().toList();
    StaffRole ownerRole = roles.save(new StaffRole(tenant.getId(), "OWNER", permissions));
    StaffUser owner =
        users.save(
            new StaffUser(
                tenant.getId(),
                request.ownerName().trim(),
                request.ownerPhone().trim(),
                request.ownerEmail(),
                passwordEncoder.encode(request.ownerPassword()),
                passwordEncoder.encode(request.ownerPin()),
                ownerRole.getId()));
    SubscriptionRequest subscriptionRequest = null;
    if (selectedPlan != null) {
      subscriptionRequest =
          subscriptionRequests.save(
              new SubscriptionRequest(
                  tenant.getId(),
                  owner.getId(),
                  null,
                  selectedPlan.getId(),
                  "new_subscription",
                  request.subscriptionRequestMessage()));
    }
    return new ProvisionedTenant(tenant, owner, ownerRole, subscriptionRequest);
  }

  public record ProvisionTenant(
      String name,
      String gstin,
      String address,
      String ownerName,
      String ownerPhone,
      String ownerEmail,
      String ownerPassword,
      String ownerPin,
      Set<ModuleType> modules,
      UUID selectedPlanId,
      String subscriptionRequestMessage) {}

  public record ProvisionedTenant(
      Tenant tenant,
      StaffUser owner,
      StaffRole ownerRole,
      SubscriptionRequest subscriptionRequest) {}
}
