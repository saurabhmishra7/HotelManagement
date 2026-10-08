package com.InnovaServe.core.event;

import java.util.UUID;

public record InvoiceFullyPaidEvent(UUID tenantId, UUID invoiceId) {}
