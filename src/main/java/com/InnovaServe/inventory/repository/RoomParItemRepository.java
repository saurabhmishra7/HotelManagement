package com.InnovaServe.inventory.repository;
import com.InnovaServe.inventory.entity.RoomParItem;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoomParItemRepository extends JpaRepository<RoomParItem, UUID> {
  List<RoomParItem> findAllByTenantIdAndRoomTypeOrderById(UUID tenantId, String roomType);
  void deleteAllByTenantIdAndRoomType(UUID tenantId, String roomType);
  void deleteAllByTenantIdAndInventoryItemId(UUID tenantId, UUID inventoryItemId);
}
