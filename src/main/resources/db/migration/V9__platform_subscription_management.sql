CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.platform_admin (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    password_hash  TEXT NOT NULL,
    role           VARCHAR(30) NOT NULL CHECK (role IN ('OWNER', 'TENANT_MANAGER', 'PLAN_MANAGER')),
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_platform_admin_email
    ON platform.platform_admin (lower(email));

CREATE TABLE platform.plan (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id   UUID NOT NULL,
    version     INTEGER NOT NULL CHECK (version > 0),
    name        VARCHAR(80) NOT NULL,
    price       NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    currency    VARCHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency ~ '^[A-Z]{3}$'),
    duration    VARCHAR(20) NOT NULL CHECK (duration IN ('monthly', 'annual')),
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plan_family_version UNIQUE (family_id, version)
);

CREATE UNIQUE INDEX uq_plan_one_active_version_per_family
    ON platform.plan (family_id)
    WHERE active = true;

CREATE INDEX idx_plan_active_name ON platform.plan (active, name);

CREATE TABLE platform.plan_module (
    plan_id UUID NOT NULL REFERENCES platform.plan(id),
    module  VARCHAR(20) NOT NULL CHECK (module IN ('stay', 'restaurant', 'expense')),
    PRIMARY KEY (plan_id, module)
);

CREATE TABLE platform.tenant_subscription (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(id),
    plan_id             UUID NOT NULL REFERENCES platform.plan(id),
    plan_price_at_time  NUMERIC(10,2) NOT NULL CHECK (plan_price_at_time >= 0),
    price_paid          NUMERIC(10,2) NOT NULL CHECK (price_paid >= 0),
    negotiation_note    TEXT,
    starts_on           DATE NOT NULL,
    expires_on          DATE NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN
                            ('scheduled', 'active', 'cancelling', 'expired', 'cancelled')),
    cancelled_at        TIMESTAMPTZ,
    created_by          UUID NOT NULL REFERENCES platform.platform_admin(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_dates CHECK (expires_on >= starts_on)
);

CREATE UNIQUE INDEX uq_tenant_current_subscription
    ON platform.tenant_subscription (tenant_id)
    WHERE status IN ('active', 'cancelling');

CREATE UNIQUE INDEX uq_tenant_scheduled_subscription
    ON platform.tenant_subscription (tenant_id)
    WHERE status = 'scheduled';

CREATE INDEX idx_tenant_subscription_history
    ON platform.tenant_subscription (tenant_id, starts_on DESC, created_at DESC);

CREATE INDEX idx_subscription_due
    ON platform.tenant_subscription (starts_on, expires_on, status);

-- A subscription retains the precise module set that was purchased even after
-- a plan receives a new version.
CREATE TABLE platform.tenant_subscription_module (
    subscription_id UUID NOT NULL REFERENCES platform.tenant_subscription(id),
    module          VARCHAR(20) NOT NULL CHECK (module IN ('stay', 'restaurant', 'expense')),
    PRIMARY KEY (subscription_id, module)
);

CREATE TABLE platform.audit_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID REFERENCES platform.platform_admin(id),
    actor_type   VARCHAR(20) NOT NULL CHECK (actor_type IN ('platform_admin', 'system')),
    action       VARCHAR(60) NOT NULL,
    entity_type  VARCHAR(40) NOT NULL,
    entity_id    UUID NOT NULL,
    before_value JSONB,
    after_value  JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_platform_audit_entity
    ON platform.audit_log (entity_type, entity_id, created_at DESC);

CREATE INDEX idx_platform_audit_admin
    ON platform.audit_log (admin_id, created_at DESC);
