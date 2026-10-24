ALTER TABLE core.notification
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE core.notification SET updated_at = created_at;

ALTER TABLE core.notification
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET NOT NULL;

DROP INDEX IF EXISTS core.ix_notification_inbox;
CREATE INDEX ix_notification_inbox ON core.notification(tenant_id, recipient_user_id, updated_at DESC)
    WHERE dismissed_at IS NULL;
