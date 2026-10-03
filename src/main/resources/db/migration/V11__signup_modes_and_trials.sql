CREATE TABLE platform.platform_settings (
    id                  VARCHAR(10) PRIMARY KEY DEFAULT 'GLOBAL'
                            CHECK (id = 'GLOBAL'),
    signup_mode         VARCHAR(10) NOT NULL DEFAULT 'standard'
                            CHECK (signup_mode IN ('trial', 'standard')),
    trial_plan_id       UUID REFERENCES platform.plan(id),
    trial_duration_days INTEGER NOT NULL DEFAULT 14
                            CHECK (trial_duration_days BETWEEN 1 AND 365),
    updated_by          UUID REFERENCES platform.platform_admin(id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform.platform_settings (id) VALUES ('GLOBAL');

-- Keep a database-enforced owner email reservation to close concurrent-signup races.
ALTER TABLE core.tenant ADD COLUMN primary_owner_email VARCHAR(150);
WITH owner_emails AS (
    SELECT u.tenant_id, lower(trim(u.email)) AS email,
           row_number() OVER (PARTITION BY lower(trim(u.email)) ORDER BY u.created_at, u.id) AS rank
    FROM core.users u
    JOIN core.role r ON r.id = u.role_id
    WHERE upper(r.name) IN ('OWNER', 'HOTEL_ADMIN') AND u.email IS NOT NULL
)
UPDATE core.tenant t SET primary_owner_email = owner_emails.email
FROM owner_emails WHERE owner_emails.tenant_id = t.id AND owner_emails.rank = 1;
CREATE UNIQUE INDEX uq_tenant_primary_owner_email
    ON core.tenant (lower(primary_owner_email)) WHERE primary_owner_email IS NOT NULL;

ALTER TABLE platform.tenant_subscription
    ADD COLUMN is_trial BOOLEAN NOT NULL DEFAULT false;

-- Public signup and tenant self-service subscriptions are system-authored.
ALTER TABLE platform.tenant_subscription
    ALTER COLUMN created_by DROP NOT NULL;

CREATE TABLE platform.public_signup_intent (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_name       VARCHAR(200) NOT NULL,
    gstin               VARCHAR(15),
    address             TEXT,
    owner_name          VARCHAR(100) NOT NULL,
    owner_phone         VARCHAR(15) NOT NULL,
    owner_email         VARCHAR(150) NOT NULL,
    owner_password_hash TEXT NOT NULL,
    owner_pin_hash      TEXT NOT NULL,
    plan_id             UUID NOT NULL REFERENCES platform.plan(id),
    amount              NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    currency            VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    payment_order_id    VARCHAR(80) NOT NULL UNIQUE,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending', 'completed', 'expired')),
    expires_at          TIMESTAMPTZ NOT NULL,
    tenant_id           UUID REFERENCES core.tenant(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_signup_intent_expiry
    ON platform.public_signup_intent (expires_at, status);

CREATE TABLE platform.tenant_subscription_checkout (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES core.tenant(id),
    initiated_by     UUID NOT NULL REFERENCES core.users(id),
    plan_id          UUID NOT NULL REFERENCES platform.plan(id),
    subscription_request_id UUID REFERENCES platform.subscription_request(id),
    action           VARCHAR(10) NOT NULL CHECK (action IN ('activate', 'schedule')),
    amount           NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    payment_order_id VARCHAR(80) NOT NULL UNIQUE,
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                         CHECK (status IN ('pending', 'completed', 'expired')),
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tenant_checkout_expiry
    ON platform.tenant_subscription_checkout (expires_at, status);
CREATE UNIQUE INDEX uq_tenant_proposal_pending_checkout
    ON platform.tenant_subscription_checkout (subscription_request_id)
    WHERE subscription_request_id IS NOT NULL AND status = 'pending';

CREATE UNIQUE INDEX uq_public_signup_pending_email
    ON platform.public_signup_intent (lower(owner_email))
    WHERE status = 'pending';

ALTER TABLE core.users
    ADD COLUMN email_verification_token_hash VARCHAR(64),
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD COLUMN email_verified_at TIMESTAMPTZ;

ALTER TABLE platform.public_signup_intent
    ALTER COLUMN owner_password_hash DROP NOT NULL,
    ALTER COLUMN owner_pin_hash DROP NOT NULL;

CREATE INDEX idx_users_email_verification_token
    ON core.users (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;

ALTER TABLE platform.subscription_request
    ADD COLUMN trigger_type VARCHAR(40) NOT NULL DEFAULT 'tenant_request',
    ADD COLUMN proposed_price NUMERIC(10,2),
    ADD COLUMN proposed_by UUID REFERENCES platform.platform_admin(id);

ALTER TABLE platform.subscription_request
    DROP CONSTRAINT IF EXISTS subscription_request_request_type_check,
    ADD CONSTRAINT subscription_request_request_type_check
        CHECK (request_type IN
            ('renewal', 'upgrade', 'new_subscription', 'tenant_instant_change', 'negotiated_price'));

ALTER TABLE platform.subscription_request
    DROP CONSTRAINT IF EXISTS subscription_request_status_check,
    ADD CONSTRAINT subscription_request_status_check
        CHECK (status IN
            ('pending', 'in_review', 'completed', 'declined', 'awaiting_tenant', 'accepted'));

ALTER TABLE platform.subscription_request
    ADD CONSTRAINT ck_subscription_request_proposed_price
        CHECK (proposed_price IS NULL OR proposed_price >= 0);

DROP INDEX platform.uq_subscription_request_open_tenant;
CREATE UNIQUE INDEX uq_subscription_request_open_tenant
    ON platform.subscription_request (tenant_id)
    WHERE status IN ('pending', 'in_review', 'awaiting_tenant');

UPDATE platform.subscription_request
SET status = 'completed',
    trigger_type = 'legacy_signup',
    response_note = COALESCE(response_note,
        'Historical signup request closed: new signup access is activated by the signup flow.')
WHERE request_type = 'new_subscription'
  AND status IN ('pending', 'in_review');
