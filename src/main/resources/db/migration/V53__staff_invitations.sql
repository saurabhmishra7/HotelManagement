CREATE TABLE core.staff_invitation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    requested_by UUID NOT NULL REFERENCES core.users(id),
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(15) NOT NULL,
    email VARCHAR(150) NOT NULL,
    role_ids JSONB NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT uq_staff_invitation_token UNIQUE (token_hash)
);
CREATE INDEX idx_staff_invitation_tenant ON core.staff_invitation (tenant_id, created_at DESC);
CREATE UNIQUE INDEX uq_staff_invitation_pending_phone ON core.staff_invitation (tenant_id, phone)
    WHERE accepted_at IS NULL;
CREATE UNIQUE INDEX uq_staff_invitation_pending_email ON core.staff_invitation (tenant_id, lower(email))
    WHERE accepted_at IS NULL;
