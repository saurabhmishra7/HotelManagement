package com.InnovaServe.stay.repository;

import com.InnovaServe.stay.entity.Room;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {
  List<Room> findAllByTenantIdOrderByRoomNumber(UUID tenantId);

  boolean existsByTenantIdAndRoomNumber(UUID tenantId, String roomNumber);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.tenantId=:tenantId and r.id=:id")
  Optional<Room> lockByTenantIdAndId(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  Optional<Room> findByTenantIdAndId(UUID tenantId, UUID id);
}
