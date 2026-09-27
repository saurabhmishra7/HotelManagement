package com.InnovaServe.stay.repository;
import com.InnovaServe.stay.entity.Room;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.*;
public interface RoomRepository extends JpaRepository<Room,UUID>{List<Room> findAllByTenantIdOrderByRoomNumber(UUID tenantId);@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from Room r where r.tenantId=:tenantId and r.id=:id")Optional<Room> lockByTenantIdAndId(UUID tenantId,UUID id);Optional<Room> findByTenantIdAndId(UUID tenantId,UUID id);}
