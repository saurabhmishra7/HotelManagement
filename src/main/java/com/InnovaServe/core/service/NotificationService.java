package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.Notification;
import com.InnovaServe.core.entity.StaffRole;
import com.InnovaServe.core.entity.StaffUser;
import com.InnovaServe.core.repository.NotificationRepository;
import com.InnovaServe.core.repository.StaffRoleRepository;
import com.InnovaServe.core.repository.StaffUserRepository;
import com.InnovaServe.core.repository.TenantRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
  private final NotificationRepository notifications;
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;
  private final TenantRepository tenants;
  private final TenantContext context;

  public NotificationService(NotificationRepository notifications, StaffUserRepository users,
      StaffRoleRepository roles, TenantRepository tenants, TenantContext context) {
    this.notifications = notifications; this.users = users; this.roles = roles;
    this.tenants = tenants; this.context = context;
  }

  @Transactional(readOnly = true)
  public List<Notification> inbox() {
    return notifications.findTop20ByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(context.tenantId(), context.userId());
  }

  @Transactional(readOnly = true)
  public long unreadCount() {
    return notifications.countByTenantIdAndRecipientUserIdAndReadAtIsNullAndDismissedAtIsNull(context.tenantId(), context.userId());
  }

  @Transactional
  public void markRead(UUID id) { owned(id).markRead(); }

  @Transactional
  public void dismiss(UUID id) { owned(id).dismiss(); }

  @Transactional
  public void clearAll() { notifications.deleteAllByTenantIdAndRecipientUserIdAndDismissedAtIsNull(context.tenantId(), context.userId()); }

  @Transactional
  public void lowStock(UUID tenantId, UUID itemId, String itemName, String quantity, String threshold) {
    String key = "low-stock:" + itemId + ":" + UUID.randomUUID();
    for (StaffUser user : responsibleUsers(tenantId, true)) {
      notifications.save(new Notification(tenantId, user.getId(), "Low stock: " + itemName,
          itemName + " is at " + quantity + "; reorder threshold is " + threshold + ".",
          "IMPORTANT", "inventory", key));
    }
  }

  @Transactional
  public void sendPlatformNotice(Collection<UUID> tenantIds, boolean allTenants, String title, String message) {
    if (allTenants) tenantIds = tenants.findAllTenantIds();
    if (tenantIds == null || tenantIds.isEmpty()) throw new IllegalArgumentException("Choose at least one tenant");
    if (title == null || title.isBlank() || title.trim().length() > 160) throw new IllegalArgumentException("Title is required and must be at most 160 characters");
    if (message == null || message.isBlank() || message.trim().length() > 4000) throw new IllegalArgumentException("Message is required and must be at most 4000 characters");
    String key = UUID.randomUUID().toString();
    for (UUID tenantId : tenantIds) for (StaffUser user : responsibleUsers(tenantId, false)) {
      notifications.save(new Notification(tenantId, user.getId(), title.trim(), message.trim(), "NORMAL", "platform", key));
    }
  }

  private List<StaffUser> responsibleUsers(UUID tenantId, boolean includeInventoryManagers) {
    Map<UUID, StaffRole> roleById = new HashMap<>();
    roles.findAllByTenantIdOrderByName(tenantId).forEach(role -> roleById.put(role.getId(), role));
    return users.findAllByTenantIdAndActiveTrue(tenantId).stream().filter(user -> {
      StaffRole role = roleById.get(user.getRoleId());
      if (role == null) return false;
      String roleName = role.getName().toUpperCase(Locale.ROOT);
      return roleName.equals("OWNER") || roleName.equals("HOTEL_ADMIN") || roleName.equals("GENERAL_MANAGER") || roleName.equals("TENANT_MANAGER")
          || (includeInventoryManagers && role.getPermissions() != null && role.getPermissions().contains("INVENTORY_MANAGE"));
    }).toList();
  }

  private Notification owned(UUID id) {
    return notifications.findByTenantIdAndRecipientUserIdAndId(context.tenantId(), context.userId(), id)
        .orElseThrow(() -> new NoSuchElementException("Notification not found"));
  }
}
