CREATE TABLE restaurant.guest_qr_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    qr_code_id UUID NOT NULL REFERENCES core.qr_code(id),
    target_type VARCHAR(20) NOT NULL,
    target_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'closed')),
    closed_reason VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    closed_at TIMESTAMP,
    CONSTRAINT ck_guest_qr_session_target CHECK (target_type IN ('dining_table', 'room'))
);

CREATE INDEX idx_guest_qr_session_target
    ON restaurant.guest_qr_session (tenant_id, target_type, target_id, status);

ALTER TABLE restaurant.restaurant_order
    ADD COLUMN guest_session_id UUID REFERENCES restaurant.guest_qr_session(id);

CREATE INDEX idx_restaurant_order_guest_session
    ON restaurant.restaurant_order (tenant_id, guest_session_id)
    WHERE guest_session_id IS NOT NULL;
