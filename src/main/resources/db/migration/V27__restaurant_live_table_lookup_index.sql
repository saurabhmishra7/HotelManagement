CREATE INDEX idx_restaurant_order_latest_billed_table
    ON restaurant.restaurant_order (tenant_id, table_id, created_at DESC)
    WHERE order_type = 'dine_in' AND status = 'billed';
