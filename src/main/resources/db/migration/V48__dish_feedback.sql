CREATE TABLE restaurant.dish_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    order_id UUID NOT NULL REFERENCES restaurant.restaurant_order(id),
    menu_item_id UUID NOT NULL REFERENCES restaurant.menu_item(id),
    guest_session_id UUID NOT NULL REFERENCES restaurant.guest_qr_session(id),
    stars SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    feedback VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT timezone('UTC', now()),
    CONSTRAINT uq_dish_feedback_order_item UNIQUE (tenant_id, order_id, menu_item_id)
);

CREATE INDEX idx_dish_feedback_menu_recent
    ON restaurant.dish_feedback (tenant_id, menu_item_id, created_at DESC);
CREATE INDEX idx_dish_feedback_recent
    ON restaurant.dish_feedback (tenant_id, created_at DESC);
