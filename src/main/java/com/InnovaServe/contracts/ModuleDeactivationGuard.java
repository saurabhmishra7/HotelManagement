package com.InnovaServe.contracts;

import com.InnovaServe.core.security.ModuleType;
import java.util.Optional;
import java.util.UUID;

/** Prevents disabling a module while it has operational records that need completion. */
public interface ModuleDeactivationGuard {
  ModuleType module();

  Optional<String> blockingReason(UUID tenantId);
}
