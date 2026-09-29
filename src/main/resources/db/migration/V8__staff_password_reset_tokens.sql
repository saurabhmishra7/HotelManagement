ALTER TABLE core.users
    ADD COLUMN password_reset_token_hash VARCHAR(64),
    ADD COLUMN password_reset_expires_at TIMESTAMPTZ,
    ADD COLUMN password_reset_requested_at TIMESTAMPTZ;

CREATE INDEX idx_users_password_reset
    ON core.users (tenant_id, password_reset_token_hash)
    WHERE password_reset_token_hash IS NOT NULL;
