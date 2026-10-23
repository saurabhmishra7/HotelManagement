CREATE INDEX idx_kot_batch_tenant_station_number
    ON restaurant.kot_batch (tenant_id, station, batch_number);

CREATE INDEX idx_order_item_pending_batch
    ON restaurant.order_item (tenant_id, kot_batch_id)
    WHERE kot_batch_id IS NOT NULL
      AND status NOT IN ('ready', 'served', 'cancelled');
