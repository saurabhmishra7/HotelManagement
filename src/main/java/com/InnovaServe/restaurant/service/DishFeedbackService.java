package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.service.TenantContext;
import com.InnovaServe.restaurant.entity.DishFeedback;
import com.InnovaServe.restaurant.entity.MenuItem;
import com.InnovaServe.restaurant.entity.OrderItem;
import com.InnovaServe.restaurant.repository.DishFeedbackRepository;
import com.InnovaServe.restaurant.repository.MenuItemRepository;
import com.InnovaServe.restaurant.repository.RestaurantOrderFeedbackRepository;
import com.InnovaServe.restaurant.entity.RestaurantOrderFeedback;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DishFeedbackService {
  private final DishFeedbackRepository feedback;
  private final MenuItemRepository menuItems;
  private final RestaurantOrderFeedbackRepository orderFeedback;
  private final TenantContext tenant;

  public DishFeedbackService(DishFeedbackRepository feedback, MenuItemRepository menuItems,
      RestaurantOrderFeedbackRepository orderFeedback, TenantContext tenant) {
    this.feedback = feedback;
    this.menuItems = menuItems;
    this.orderFeedback = orderFeedback;
    this.tenant = tenant;
  }

  public record Rating(UUID menuItemId, BigDecimal stars) {}
  public record Summary(double averageStars, long reviewCount) {}

  @Transactional
  public void saveForBillRequest(UUID tenantId, UUID orderId, UUID guestSessionId,
      List<OrderItem> billableItems, List<Rating> ratings, String writtenFeedback) {
    Set<UUID> dishes = billableItems.stream().map(OrderItem::getMenuItemId)
        .collect(Collectors.toSet());
    if (ratings == null || ratings.size() != dishes.size())
      throw new IllegalArgumentException("Rate every dish before requesting the bill");
    Set<UUID> rated = new HashSet<>();
    List<DishFeedback> rows = new ArrayList<>();
    for (Rating rating : ratings) {
      if (rating == null || rating.menuItemId() == null
          || !dishes.contains(rating.menuItemId()) || !rated.add(rating.menuItemId())
          || !isValidRating(rating.stars()))
        throw new IllegalArgumentException("Give each ordered dish one rating from 1 to 5 stars in half-star steps");
      rows.add(new DishFeedback(tenantId, orderId, rating.menuItemId(), guestSessionId,
          rating.stars()));
    }
    feedback.saveAllAndFlush(rows);
    String comment = writtenFeedback == null ? null : writtenFeedback.trim();
    if (comment != null && comment.length() > 1000)
      throw new IllegalArgumentException("Feedback must be at most 1000 characters");
    if (comment != null && !comment.isEmpty())
      orderFeedback.save(new RestaurantOrderFeedback(tenantId, orderId, guestSessionId, comment));
  }

  private boolean isValidRating(BigDecimal stars) {
    return stars != null
        && stars.compareTo(BigDecimal.ONE) >= 0
        && stars.compareTo(BigDecimal.valueOf(5)) <= 0
        && stars.remainder(BigDecimal.valueOf(0.5)).compareTo(BigDecimal.ZERO) == 0;
  }

  @Transactional(readOnly = true)
  public Map<UUID, Summary> summaries(UUID tenantId) {
    Map<UUID, Summary> result = new HashMap<>();
    for (DishFeedbackRepository.DishSummary row : feedback.summarizeAll(tenantId))
      result.put(row.getMenuItemId(), new Summary(row.getAverageStars(), row.getReviewCount()));
    return result;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> recent() {
    UUID tenantId = tenant.tenantId();
    List<DishFeedback> rows = feedback.findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
        tenantId, cutoff());
    List<RestaurantOrderFeedback> comments = orderFeedback
        .findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(tenantId, cutoff());
    Set<UUID> ids = rows.stream().map(DishFeedback::getMenuItemId).collect(Collectors.toSet());
    Map<UUID, String> names = ids.isEmpty() ? Map.of() : menuItems.findAllByTenantIdAndIdIn(tenantId, ids).stream()
        .collect(Collectors.toMap(MenuItem::getId, MenuItem::getDisplayName));
    List<Map<String, Object>> result = rows.stream().map(row -> {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", row.getId());
      view.put("type", "rating");
      view.put("menu_item_id", row.getMenuItemId());
      view.put("dish_name", names.getOrDefault(row.getMenuItemId(), "Unavailable dish"));
      view.put("stars", row.getStars());
      view.put("created_at", row.getCreatedAt().atOffset(ZoneOffset.UTC));
      return view;
    }).collect(Collectors.toCollection(ArrayList::new));
    comments.stream().map(row -> {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("id", row.getId());
      view.put("type", "comment");
      view.put("dish_name", "Guest feedback");
      view.put("feedback", row.getFeedback());
      view.put("created_at", row.getCreatedAt().atOffset(ZoneOffset.UTC));
      return view;
    }).forEach(result::add);
    result.sort((left, right) -> ((java.time.OffsetDateTime) right.get("created_at"))
        .compareTo((java.time.OffsetDateTime) left.get("created_at")));
    return result;
  }

  @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
  @Transactional
  public void deleteExpired() {
    orderFeedback.clearExpiredComments(cutoff());
  }

  private LocalDateTime cutoff() {
    return LocalDateTime.now(ZoneOffset.UTC).minusMonths(1);
  }
}
