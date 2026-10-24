package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.StaffUserRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class EmailStaffApprovalDelivery implements StaffApprovalDelivery {
  private static final Logger log = LoggerFactory.getLogger(EmailStaffApprovalDelivery.class);
  private final EmailSender emailSender;
  private final StaffUserRepository users;
  private final String approvalBaseUrl;

  public EmailStaffApprovalDelivery(EmailSender emailSender, StaffUserRepository users,
      @Value("${app.staff-approval.base-url:${APP_STAFF_APPROVAL_BASE_URL:http://localhost:3000/app/team}}")
          String approvalBaseUrl) {
    this.emailSender = emailSender;
    this.users = users;
    this.approvalBaseUrl = approvalBaseUrl;
  }

  @Override
  public void requested(UUID tenantId, Collection<UUID> ownerIds, UUID requestId, String staffName) {
    if (!emailSender.isAvailable()) return;
    List<String> recipients = ownerIds.stream()
        .map(id -> users.findByTenantIdAndId(tenantId, id).orElse(null))
        .filter(user -> user != null && user.isActive())
        .map(StaffUser::getEmail)
        .filter(email -> email != null && !email.isBlank()).distinct().toList();
    String separator = approvalBaseUrl.contains("?") ? "&" : "?";
    String link = approvalBaseUrl + separator + "approval=" + requestId;
    String message = staffName + " was proposed for an elevated role. Review the request at:\n\n"
        + link + "\n\nSign in as an Owner and enter your PIN to approve or decline it.";
    afterCommit(() -> recipients.forEach(email -> deliver(email, "Approve elevated team access", message)));
  }

  @Override
  public void reviewed(UUID tenantId, UUID requesterId, UUID requestId, String staffName,
      boolean approved) {
    if (!emailSender.isAvailable()) return;
    String email = users.findByTenantIdAndId(tenantId, requesterId)
        .filter(StaffUser::isActive).map(StaffUser::getEmail).orElse(null);
    if (email == null || email.isBlank()) return;
    String subject = approved ? "Team access approved" : "Team access declined";
    String message = staffName + (approved ? " can now use the approved role."
        : " was not given the requested role.");
    afterCommit(() -> deliver(email, subject, message));
  }

  @Override public void resolved(UUID tenantId, UUID requestId) { }

  private void deliver(String email, String subject, String message) {
    try {
      emailSender.send(email, subject, message);
    } catch (EmailDeliveryException exception) {
      log.error("Could not deliver staff approval email", exception);
    }
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { action.run(); }
    });
  }
}
