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
    return notifications.findTop20ByTenantIdAndRecipientUserIdAndDismissedAtIsNullOrderByUpdatedAtDesc(context.tenantId(), context.userId());
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
  public void roomBecameDirty(UUID tenantId, UUID roomId, String roomNumber) {
    String key = "room-dirty:" + roomId + ":" + UUID.randomUUID();
    sendToRole(tenantId, "HOUSEKEEPING_UPDATE", "HOUSEKEEPING", "Room " + roomNumber + " needs cleaning",
        "Room " + roomNumber + " is dirty. Clean it, then mark it clean in Rooms.", "housekeeping", key);
  }

  @Transactional
  public void roomWasCleaned(UUID tenantId, UUID roomId) {
    notifications.findAllByTenantIdAndSourceAndSourceKeyStartingWithAndDismissedAtIsNull(
        tenantId, "housekeeping", "room-dirty:" + roomId + ":").forEach(Notification::dismiss);
  }

  @Transactional
  public void qrOrderNeedsConfirmation(UUID tenantId, UUID orderId, String location, boolean roomService) {
    sendToRole(tenantId, "ORDER_CONFIRM", "WAITER",
        "Confirm QR order for " + location,
        "A guest placed a " + (roomService ? "room restaurant" : "table") + " order for " + location
            + ". Confirm it in Restaurant → Orders before it reaches the kitchen.",
        "restaurant_order", "order-confirm:" + orderId + ":" + UUID.randomUUID());
  }

  @Transactional
  public void qrOrderWasConfirmed(UUID tenantId, UUID orderId) {
    notifications.findAllByTenantIdAndSourceAndSourceKeyStartingWithAndDismissedAtIsNull(
        tenantId, "restaurant_order", "order-confirm:" + orderId).forEach(Notification::dismiss);
  }

  @Transactional
  public void syncReadyPickup(UUID tenantId, List<String> readyItems, boolean newlyReady) {
    String source = "restaurant_order";
    String key = "order-ready";
    // Retire item-level alerts from before grouping so each waiter sees one pickup alert.
    notifications.findAllByTenantIdAndSourceAndSourceKeyStartingWithAndDismissedAtIsNull(
        tenantId, source, "order-ready:").forEach(Notification::dismiss);
    List<Notification> current = notifications.findAllByTenantIdAndSourceAndSourceKey(
        tenantId, source, key);
    if (readyItems.isEmpty()) {
      current.stream().filter(notification -> notification.getDismissedAt() == null).forEach(Notification::dismiss);
      return;
    }
    String title = readyItems.size() == 1 ? "1 item ready for pickup"
        : readyItems.size() + " items ready for pickup";
    String message = "Ready for pickup: " + String.join("; ", readyItems)
        + ". Deliver each item, then mark it served in Restaurant → Orders or Tables & service.";
    Map<UUID, Notification> byRecipient = new HashMap<>();
    current.forEach(notification -> byRecipient.put(notification.getRecipientUserId(), notification));
    if (newlyReady) {
      for (StaffUser user : recipients(tenantId, "ORDER_CREATE", "WAITER")) {
        Notification notification = byRecipient.remove(user.getId());
        if (notification == null) {
          notifications.save(new Notification(tenantId, user.getId(), title, message, "IMPORTANT", source, key));
        } else {
          notification.reopenReadyItems(title, message);
        }
      }
      byRecipient.values().stream().filter(notification -> notification.getDismissedAt() == null)
          .forEach(Notification::dismiss);
    } else {
      current.stream().filter(notification -> notification.getDismissedAt() == null)
          .forEach(notification -> notification.updateReadyItems(title, message, false));
    }
  }

  @Transactional
  public void restaurantBillRequested(UUID tenantId, UUID orderId, String location) {
    int recipients = sendToRole(tenantId, "BILL_CREATE", "WAITER",
        "Bill requested for " + location,
        "A guest at " + location + " requested the bill for order "
            + orderId.toString().substring(0, 8).toUpperCase(Locale.ROOT)
            + ". Open Restaurant → Orders to prepare and settle it.",
        "restaurant_order", "bill-request:" + orderId);
    if (recipients == 0)
      throw new IllegalStateException("No staff with billing access are assigned. Please contact the front desk.");
  }

  @Transactional
  public void restaurantBillRequestHandled(UUID tenantId, UUID orderId) {
    notifications.findAllByTenantIdAndSourceAndSourceKeyAndDismissedAtIsNull(
        tenantId, "restaurant_order", "bill-request:" + orderId).forEach(Notification::dismiss);
  }

  @Transactional
  public void roomServiceRequested(UUID tenantId, UUID requestId, String roomNumber, String serviceName, String notes) {
    String message = "Room " + roomNumber + " requested " + serviceName + "."
        + (notes == null || notes.isBlank() ? "" : " Guest note: " + notes.trim())
        + " Open Room service to mark your work done. The guest will confirm receipt separately.";
    int recipients = sendToRole(tenantId, "ROOM_SERVICE_REQUEST_HANDLE", "ROOM_SERVICE_ATTENDANT",
        "Room service: " + roomNumber, message, "room_service",
        "room-service:" + requestId);
    if (recipients == 0) throw new IllegalStateException("Room service staff are not assigned yet. Please contact the front desk.");
  }

  @Transactional
  public void roomServiceAttendantConfirmed(UUID tenantId, UUID requestId) {
    notifications.findAllByTenantIdAndSourceAndSourceKeyAndDismissedAtIsNull(
        tenantId, "room_service", "room-service:" + requestId).forEach(Notification::dismiss);
  }

  @Transactional
  public void roomServiceCompleted(UUID tenantId, UUID requestId, String roomNumber, String serviceType) {
    String serviceName = switch (serviceType) {
      case "cleaning" -> "Room cleaning";
      case "towels" -> "Fresh towels";
      case "water" -> "Drinking water";
      default -> "Room assistance";
    };
    String title = "Room service completed: " + roomNumber;
    String message = serviceName + " for room " + roomNumber + " is complete.";
    for (StaffUser user : recipients(tenantId, "STAY_CHECKIN", "FRONT_DESK")) {
      notifications.save(new Notification(tenantId, user.getId(), title, message,
          "NORMAL", "room_service_completed", "room-service-completed:" + requestId));
    }
  }

  private int sendToRole(UUID tenantId, String permission, String preferredRole, String title,
      String message, String source, String key) {
    List<StaffUser> recipients = recipients(tenantId, permission, preferredRole);
    for (StaffUser user : recipients) {
      notifications.save(new Notification(tenantId, user.getId(), title, message, "IMPORTANT", source, key));
    }
    return recipients.size();
  }

  private List<StaffUser> recipients(UUID tenantId, String permission, String preferredRole) {
    Map<UUID, StaffRole> roleById = new HashMap<>();
    roles.findAllByTenantIdOrderByName(tenantId).forEach(role -> roleById.put(role.getId(), role));
    List<StaffUser> eligible = users.findAllByTenantIdAndActiveTrue(tenantId).stream()
        .filter(user -> {
          StaffRole role = roleById.get(user.getRoleId());
          return role != null && role.getPermissions() != null && role.getPermissions().contains(permission);
        }).toList();
    List<StaffUser> preferred = eligible.stream()
        .filter(user -> preferredRole.equalsIgnoreCase(roleById.get(user.getRoleId()).getName())).toList();
    if (!preferred.isEmpty()) return preferred;
    List<StaffUser> specialists = eligible.stream().filter(user -> {
      String role = roleById.get(user.getRoleId()).getName().toUpperCase(Locale.ROOT);
      return !Set.of("OWNER", "HOTEL_ADMIN", "GENERAL_MANAGER", "TENANT_MANAGER").contains(role);
    }).toList();
    return specialists.isEmpty() ? eligible : specialists;
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
