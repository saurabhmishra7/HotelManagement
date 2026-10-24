package com.InnovaServe.core.service;

import java.util.*;

/** Delivery seam: another implementation can send email after it is configured. */
public interface StaffApprovalDelivery {
  void requested(UUID tenantId, Collection<UUID> ownerIds, UUID requestId, String staffName);
  void reviewed(UUID tenantId, UUID requesterId, UUID requestId, String staffName, boolean approved);
  void resolved(UUID tenantId, UUID requestId);
}
