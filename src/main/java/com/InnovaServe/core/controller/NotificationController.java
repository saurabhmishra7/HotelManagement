package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.Notification;
import com.InnovaServe.core.service.NotificationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService service;
  public NotificationController(NotificationService service) { this.service = service; }

  @GetMapping public Inbox inbox() { return new Inbox(service.inbox().stream().map(NotificationView::from).toList(), service.unreadCount()); }
  @PostMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable UUID id) { service.markRead(id); }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void dismiss(@PathVariable UUID id) { service.dismiss(id); }
  @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void clear() { service.clearAll(); }

  public record Inbox(List<NotificationView> notifications, @JsonProperty("unread_count") long unreadCount) {}
  public record NotificationView(UUID id, String title, String message, String category, String source,
      @JsonProperty("created_at") Instant createdAt, @JsonProperty("read_at") Instant readAt) {
    static NotificationView from(Notification notification) {
      return new NotificationView(notification.getId(), notification.getTitle(), notification.getMessage(),
          notification.getCategory(), notification.getSource(), notification.getCreatedAt(), notification.getReadAt());
    }
  }
}
