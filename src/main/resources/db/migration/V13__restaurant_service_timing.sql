-- Track when kitchen items enter preparation and when they are served.
ALTER TABLE restaurant.order_item
    ADD COLUMN placed_at TIMESTAMP,
    ADD COLUMN preparing_at TIMESTAMP,
    ADD COLUMN served_at TIMESTAMP;

-- Existing items predate per-item timestamps; use their parent order time as
-- the best available placement time for historical reporting.
UPDATE restaurant.order_item item
SET placed_at = restaurant_order.created_at
FROM restaurant.restaurant_order restaurant_order
WHERE restaurant_order.id = item.order_id
  AND restaurant_order.tenant_id = item.tenant_id;

ALTER TABLE restaurant.order_item
    ALTER COLUMN placed_at SET DEFAULT timezone('UTC', now()),
    ALTER COLUMN placed_at SET NOT NULL;

CREATE INDEX idx_order_item_tenant_served
    ON restaurant.order_item (tenant_id, served_at DESC)
    WHERE served_at IS NOT NULL;
