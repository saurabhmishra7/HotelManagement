CREATE TABLE stay.room_service_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    room_id UUID NOT NULL REFERENCES stay.room(id),
    stay_id UUID NOT NULL REFERENCES stay.stay(id),
    guest_session_id UUID NOT NULL REFERENCES restaurant.guest_qr_session(id),
    room_number VARCHAR(10) NOT NULL,
    service_type VARCHAR(20) NOT NULL CHECK (service_type IN ('cleaning', 'towels', 'water', 'other')),
    notes VARCHAR(200),
    status VARCHAR(12) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'completed')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    completed_by UUID REFERENCES core.users(id),
    CONSTRAINT ck_room_service_completion CHECK (
        (status = 'pending' AND completed_at IS NULL AND completed_by IS NULL)
        OR (status = 'completed' AND completed_at IS NOT NULL AND completed_by IS NOT NULL)
    )
);

CREATE INDEX idx_room_service_request_staff
    ON stay.room_service_request (tenant_id, status, requested_at DESC);
CREATE INDEX idx_room_service_request_guest
    ON stay.room_service_request (tenant_id, guest_session_id, requested_at DESC);
