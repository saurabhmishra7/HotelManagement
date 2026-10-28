-- Record when each item is sent to its preparation station. QR items can be
-- placed well before staff confirm them, so placed_at is not a reliable timer.
ALTER TABLE restaurant.order_item ADD COLUMN sent_at TIMESTAMP;

-- Historical rows did not record the send transition; placement is the closest
-- available approximation. New sends are timestamped by the application.
UPDATE restaurant.order_item
SET sent_at = placed_at
WHERE status IN ('sent', 'preparing', 'ready', 'served');
