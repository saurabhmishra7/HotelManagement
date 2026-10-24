package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.Notification;
import com.InnovaServe.core.repository.NotificationRepository;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InAppStaffApprovalDelivery implements StaffApprovalDelivery {
  private final NotificationRepository notifications;
  public InAppStaffApprovalDelivery(NotificationRepository notifications) { this.notifications = notifications; }

  @Override public void requested(UUID tenantId, Collection<UUID> ownerIds, UUID requestId, String staffName) {
    for (UUID ownerId : ownerIds) notifications.save(new Notification(tenantId, ownerId,
        "Approve elevated team access", staffName + " was proposed for an elevated role. Open the request and approve with your PIN.",
        "IMPORTANT", "staff_approval", "staff-approval:" + requestId));
  }

  @Override public void reviewed(UUID tenantId, UUID requesterId, UUID requestId, String staffName, boolean approved) {
    notifications.save(new Notification(tenantId, requesterId,
        approved ? "Team access approved" : "Team access declined",
        staffName + (approved ? " can now use the approved role." : " was not given the requested role."),
        "NORMAL", "staff_approval_result", "staff-approval-result:" + requestId));
  }

  @Override public void resolved(UUID tenantId, UUID requestId) {
    notifications.findAllByTenantIdAndSourceAndSourceKey(tenantId, "staff_approval", "staff-approval:" + requestId)
        .forEach(Notification::dismiss);
  }
}
