ALTER TABLE restaurant.dining_table
    ADD COLUMN station VARCHAR(32) NOT NULL DEFAULT 'kitchen';

ALTER TABLE restaurant.restaurant_order
    ADD COLUMN service_station VARCHAR(32) NOT NULL DEFAULT 'kitchen';

CREATE INDEX idx_diningtable_tenant_station
    ON restaurant.dining_table (tenant_id, station);
CREATE INDEX idx_order_tenant_service_station
    ON restaurant.restaurant_order (tenant_id, service_station, created_at DESC);

-- Existing tables and orders remain in Restaurant. Station codes are validated
-- by the application because Kitchen and Bar can be implicit tenant defaults.
