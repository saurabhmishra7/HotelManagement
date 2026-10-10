package com.InnovaServe.inventory.event;
import java.util.UUID;
public record OrderItemServedEvent(UUID tenantId, UUID menuItemId, UUID orderItemId, short quantity) {}
