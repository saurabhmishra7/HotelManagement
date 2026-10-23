ALTER TABLE restaurant.order_item
    DROP CONSTRAINT order_item_status_check;

ALTER TABLE restaurant.order_item
    ADD CONSTRAINT order_item_status_check CHECK (status IN
        ('pending_confirmation', 'sent', 'preparing', 'ready', 'served', 'cancelled'));

ALTER TABLE restaurant.order_item
    ADD COLUMN ready_at TIMESTAMP;
