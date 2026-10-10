package com.InnovaServe.platform.repository;

import com.InnovaServe.platform.entity.TenantSubscription;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
  List<TenantSubscription> findAllByTenantIdOrderByStartsOnDescCreatedAtDesc(UUID tenantId);

  Optional<TenantSubscription> findFirstByTenantIdAndStatusOrderByStartsOnDesc(
      UUID tenantId, String status);

  List<TenantSubscription> findAllByTenantIdIn(List<UUID> tenantIds);

  List<TenantSubscription> findAllByPlanIdInAndStatusIn(List<UUID> planIds, List<String> statuses);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from TenantSubscription s where s.id = :id")
  Optional<TenantSubscription> lockById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from TenantSubscription s where s.tenantId = :tenantId "
          + "and s.status in ('active', 'cancelling')")
  List<TenantSubscription> lockCurrentByTenant(@Param("tenantId") UUID tenantId);

  @Query(
      "select s from TenantSubscription s where s.status = 'scheduled' "
          + "and s.startsOn <= :today order by s.startsOn")
  List<TenantSubscription> findScheduledDue(@Param("today") LocalDate today);

  @Query(
      "select s from TenantSubscription s where s.status in ('active', 'cancelling') "
          + "and s.expiresOn < :today order by s.expiresOn")
  List<TenantSubscription> findExpired(@Param("today") LocalDate today);
}
