package com.InnovaServe.core.service;

import com.InnovaServe.contracts.ModuleDeactivationGuard;
import com.InnovaServe.core.entity.TenantModule;
import com.InnovaServe.core.repository.TenantModuleRepository;
import com.InnovaServe.core.repository.TenantRepository;
import com.InnovaServe.core.security.ModuleType;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModuleEntitlementService {
  private final TenantModuleRepository modules;
  private final TenantRepository tenants;
  private final List<ModuleDeactivationGuard> deactivationGuards;

  public ModuleEntitlementService(
      TenantModuleRepository modules,
      TenantRepository tenants,
      List<ModuleDeactivationGuard> deactivationGuards) {
    this.modules = modules;
    this.tenants = tenants;
    this.deactivationGuards = deactivationGuards;
  }

  @Transactional(readOnly = true)
  public boolean isActive(UUID tenantId, ModuleType module) {
    return modules
        .findByTenantIdAndModule(tenantId, module.key())
        .filter(
            entitlement ->
                "active".equals(entitlement.getStatus())
                    && (entitlement.getExpiresAt() == null
                        || entitlement.getExpiresAt().isAfter(Instant.now())))
        .isPresent();
  }

  public void requireActive(UUID tenantId, ModuleType module) {
    if (!isActive(tenantId, module)) throw new ModuleNotEntitledException(module);
  }

  @Transactional(readOnly = true)
  public List<String> activeModules(UUID tenantId) {
    Instant now = Instant.now();
    return modules.findAllByTenantId(tenantId).stream()
        .filter(
            entitlement ->
                "active".equals(entitlement.getStatus())
                    && (entitlement.getExpiresAt() == null
                        || entitlement.getExpiresAt().isAfter(now)))
        .map(TenantModule::getModule)
        .sorted()
        .toList();
  }

  @Transactional
  public List<String> replaceModules(UUID tenantId, Collection<ModuleType> selectedModules) {
    if (!tenants.existsById(tenantId)) throw new NoSuchElementException("Tenant not found");
    Set<ModuleType> selected = Set.copyOf(selectedModules);
    Map<String, TenantModule> existing = new HashMap<>();
    for (TenantModule entitlement : modules.findAllByTenantId(tenantId)) {
      existing.put(entitlement.getModule(), entitlement);
      ModuleType module = ModuleType.from(entitlement.getModule());
      boolean isSelected = selected.contains(module);
      if ("active".equals(entitlement.getStatus()) && !isSelected) {
        ModuleDeactivationGuard guard =
            deactivationGuards.stream()
            .filter(candidate -> candidate.module() == module)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No deactivation guard is configured for " + module.key()));
        Optional<String> blockingReason = guard.blockingReason(tenantId);
        if (blockingReason.isPresent()) throw new IllegalStateException(blockingReason.get());
      }
      if (!isSelected)
        entitlement.cancel();
    }
    for (ModuleType module : ModuleType.values()) {
      TenantModule entitlement = existing.get(module.key());
      if (entitlement == null) {
        entitlement = new TenantModule(tenantId, module.key(), selected.contains(module));
      } else if (selected.contains(module)) {
        entitlement.activate();
      }
      modules.save(entitlement);
    }
    return activeModules(tenantId);
  }

  public static class ModuleNotEntitledException extends RuntimeException {
    private final ModuleType module;

    public ModuleNotEntitledException(ModuleType module) {
      super("Tenant does not have access to the " + module.key() + " module");
      this.module = module;
    }

    public ModuleType module() {
      return module;
    }
  }
}
