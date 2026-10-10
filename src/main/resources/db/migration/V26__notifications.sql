CREATE TABLE core.notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES core.users(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('IMPORTANT', 'NORMAL')),
    source VARCHAR(40) NOT NULL,
    source_key VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

CREATE INDEX ix_notification_inbox ON core.notification(tenant_id, recipient_user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
CREATE INDEX ix_notification_unread ON core.notification(tenant_id, recipient_user_id)
    WHERE read_at IS NULL AND dismissed_at IS NULL;
CREATE UNIQUE INDEX uq_notification_source_key ON core.notification(tenant_id, recipient_user_id, source, source_key)
    WHERE source_key IS NOT NULL;
