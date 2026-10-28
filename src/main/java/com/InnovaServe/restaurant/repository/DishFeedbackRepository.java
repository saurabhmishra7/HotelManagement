package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.DishFeedback;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DishFeedbackRepository extends JpaRepository<DishFeedback, UUID> {
  interface DishSummary {
    UUID getMenuItemId();
    Double getAverageStars();
    Long getReviewCount();
  }

  @Query("select f.menuItemId as menuItemId, avg(f.stars) as averageStars, count(f) as reviewCount "
      + "from DishFeedback f where f.tenantId = :tenantId "
      + "group by f.menuItemId")
  List<DishSummary> summarizeAll(@Param("tenantId") UUID tenantId);

  List<DishFeedback> findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
      UUID tenantId, LocalDateTime since);

}
