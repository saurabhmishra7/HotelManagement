CREATE TABLE core.staff_approval_request (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    requested_by UUID NOT NULL REFERENCES core.users(id),
    target_user_id UUID REFERENCES core.users(id),
    request_type VARCHAR(12) NOT NULL CHECK (request_type IN ('create', 'roles')),
    name VARCHAR(100),
    phone VARCHAR(15),
    email VARCHAR(150),
    password_hash TEXT,
    pin_hash TEXT,
    role_ids JSONB NOT NULL,
    previous_role_ids JSONB,
    status VARCHAR(12) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES core.users(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_staff_approval_shape CHECK (
        (request_type = 'create' AND target_user_id IS NULL AND name IS NOT NULL AND phone IS NOT NULL AND password_hash IS NOT NULL)
        OR (request_type = 'roles' AND target_user_id IS NOT NULL AND previous_role_ids IS NOT NULL)
    )
);
CREATE INDEX idx_staff_approval_tenant_status ON core.staff_approval_request(tenant_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_staff_approval_pending_phone ON core.staff_approval_request(tenant_id, phone)
    WHERE request_type = 'create' AND status = 'pending';
CREATE UNIQUE INDEX uq_staff_approval_pending_target ON core.staff_approval_request(tenant_id, target_user_id)
    WHERE request_type = 'roles' AND status = 'pending';
