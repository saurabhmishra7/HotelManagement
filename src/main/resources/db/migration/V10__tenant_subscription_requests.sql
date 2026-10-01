CREATE TABLE platform.subscription_request (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES core.tenant(id),
    requested_by             UUID NOT NULL REFERENCES core.users(id),
    current_subscription_id  UUID REFERENCES platform.tenant_subscription(id),
    requested_plan_id        UUID NOT NULL REFERENCES platform.plan(id),
    request_type             VARCHAR(24) NOT NULL CHECK
                                 (request_type IN ('renewal', 'upgrade', 'new_subscription')),
    message                  VARCHAR(1000),
    status                   VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK
                                 (status IN ('pending', 'in_review', 'completed', 'declined')),
    response_note            VARCHAR(1000),
    submitted_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_by              UUID REFERENCES platform.platform_admin(id),
    resolved_at              TIMESTAMPTZ,
    CONSTRAINT ck_subscription_request_message_length
        CHECK (message IS NULL OR length(message) <= 1000),
    CONSTRAINT ck_subscription_request_response_length
        CHECK (response_note IS NULL OR length(response_note) <= 1000)
);

CREATE UNIQUE INDEX uq_subscription_request_open_tenant
    ON platform.subscription_request (tenant_id)
    WHERE status IN ('pending', 'in_review');

CREATE INDEX idx_subscription_request_status_date
    ON platform.subscription_request (status, submitted_at DESC);

CREATE INDEX idx_subscription_request_tenant_date
    ON platform.subscription_request (tenant_id, submitted_at DESC);

UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb)
    || '["TENANT_SUBSCRIPTION_READ", "TENANT_SUBSCRIPTION_REQUEST"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN');
