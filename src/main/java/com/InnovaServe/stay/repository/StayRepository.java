package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.Stay;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StayRepository extends JpaRepository<Stay, UUID> {
  Optional<Stay> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Stay s where s.tenantId = :tenantId and s.id = :id")
  Optional<Stay> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  Optional<Stay> findFirstByTenantIdAndRoomIdAndStatusOrderByCheckInAtDesc(
      UUID tenantId, UUID roomId, String status);

  List<Stay> findAllByTenantIdAndStatusOrderByCheckInAtDesc(UUID tenantId, String status);

  List<Stay> findAllByTenantIdAndAccountId(UUID tenantId, UUID accountId);

  List<Stay> findAllByTenantIdAndCustomerIdOrderByCheckInAtDesc(
      UUID tenantId, UUID customerId);

  List<Stay> findAllByTenantIdAndCustomerIdInOrderByCheckInAtDesc(
      UUID tenantId, Collection<UUID> customerIds);

  @Query("""
      select s.customerId as customerId, count(s) as stayCount
      from Stay s
      where s.tenantId = :tenantId and s.customerId in :customerIds
      group by s.customerId
      """)
  List<CustomerStayStatsProjection> findCustomerStayStats(
      @Param("tenantId") UUID tenantId, @Param("customerIds") Collection<UUID> customerIds);

  @Query(value = """
      select c.id from Customer c
      where c.tenantId = :tenantId
        and exists (
          select s.id from Stay s
          where s.tenantId = :tenantId and s.customerId = c.id
        )
      order by c.name asc
      """,
      countQuery = """
      select count(c.id) from Customer c
      where c.tenantId = :tenantId
        and exists (
          select s.id from Stay s
          where s.tenantId = :tenantId and s.customerId = c.id
        )
      """)
  Page<UUID> findCustomerIdsWithHotelStays(
      @Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(value = """
      select c.id from Customer c
      where c.tenantId = :tenantId
        and not exists (
          select s.id from Stay s
          where s.tenantId = :tenantId and s.customerId = c.id
        )
      order by c.name asc
      """,
      countQuery = """
      select count(c.id) from Customer c
      where c.tenantId = :tenantId
        and not exists (
          select s.id from Stay s
          where s.tenantId = :tenantId and s.customerId = c.id
        )
      """)
  Page<UUID> findCustomerIdsWithoutHotelStays(
      @Param("tenantId") UUID tenantId, Pageable pageable);

  boolean existsByTenantIdAndStatus(UUID tenantId, String status);

  interface CustomerStayStatsProjection {
    UUID getCustomerId();

    long getStayCount();
  }
}
