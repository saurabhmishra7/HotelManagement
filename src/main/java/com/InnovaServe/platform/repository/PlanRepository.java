package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
  List<Plan> findAllByOrderByNameAscVersionDesc();

  List<Plan> findAllByActiveTrueOrderByNameAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Plan p where p.id = :id")
  Optional<Plan> lockById(@Param("id") UUID id);
}
