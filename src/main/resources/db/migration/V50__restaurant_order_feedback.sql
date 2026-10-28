CREATE TABLE restaurant.restaurant_order_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    order_id UUID NOT NULL REFERENCES restaurant.restaurant_order(id),
    guest_session_id UUID NOT NULL REFERENCES restaurant.guest_qr_session(id),
    feedback VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT timezone('UTC', now()),
    CONSTRAINT uq_restaurant_order_feedback_order UNIQUE (tenant_id, order_id)
);

INSERT INTO restaurant.restaurant_order_feedback
    (tenant_id, order_id, guest_session_id, feedback, created_at)
SELECT tenant_id, order_id, guest_session_id,
       left(string_agg(DISTINCT trim(feedback), E'\n'), 1000),
       max(created_at)
FROM restaurant.dish_feedback
WHERE feedback IS NOT NULL AND trim(feedback) <> ''
  AND created_at >= timezone('UTC', now()) - INTERVAL '1 month'
GROUP BY tenant_id, order_id, guest_session_id;

CREATE INDEX idx_restaurant_order_feedback_recent
    ON restaurant.restaurant_order_feedback (tenant_id, created_at DESC);

ALTER TABLE restaurant.dish_feedback DROP COLUMN feedback;
