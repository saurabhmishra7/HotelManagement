package com.InnovaServe.restaurant.repository;

import com.InnovaServe.restaurant.entity.RestaurantOrderFeedback;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantOrderFeedbackRepository extends JpaRepository<RestaurantOrderFeedback, UUID> {
  List<RestaurantOrderFeedback> findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
      UUID tenantId, LocalDateTime since);

  @Modifying
  @Query("update RestaurantOrderFeedback f set f.feedback = null "
      + "where f.createdAt < :cutoff and f.feedback is not null")
  int clearExpiredComments(@Param("cutoff") LocalDateTime cutoff);
}
