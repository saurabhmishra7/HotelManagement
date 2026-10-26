package com.InnovaServe.restaurant.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Published only for new, staff-confirmed KOT batches, inside the order transaction. */
public record PrintTicketsCreated(UUID tenantId, UUID orderId, List<Map<String, Object>> tickets) {}
