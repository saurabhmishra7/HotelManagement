package com.InnovaServe.inventory.event;
import java.util.UUID;
public record RoomMarkedCleanEvent(UUID tenantId, UUID roomId, String roomType) {}
