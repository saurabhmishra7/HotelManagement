ALTER TABLE platform.subscription_request
    DROP CONSTRAINT IF EXISTS subscription_request_status_check,
    ADD CONSTRAINT subscription_request_status_check
        CHECK (status IN (
            'pending', 'in_review', 'completed', 'declined',
            'awaiting_tenant', 'accepted', 'revoked'
        ));

CREATE TABLE platform.subscription_request_proposal (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id          UUID NOT NULL REFERENCES platform.subscription_request(id),
    calculation_type    VARCHAR(20) NOT NULL
                            CHECK (calculation_type IN ('adjust_days', 'pay_difference')),
    new_plan_id         UUID NOT NULL REFERENCES platform.plan(id),
    new_price_paid      NUMERIC(10,2) NOT NULL CHECK (new_price_paid >= 0),
    new_expires_on      DATE NOT NULL,
    extra_charge_amount NUMERIC(10,2) NOT NULL DEFAULT 0
                            CHECK (extra_charge_amount >= 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'offered'
                            CHECK (status IN ('offered', 'accepted', 'superseded')),
    created_by          UUID NOT NULL REFERENCES platform.platform_admin(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_request_proposal_request
    ON platform.subscription_request_proposal (request_id, created_at);

CREATE UNIQUE INDEX uq_subscription_request_proposal_offered_type
    ON platform.subscription_request_proposal (request_id, calculation_type)
    WHERE status = 'offered';

CREATE TABLE platform.subscription_request_comment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id  UUID NOT NULL REFERENCES platform.subscription_request(id),
    author_type VARCHAR(20) NOT NULL
                    CHECK (author_type IN ('platform_admin', 'tenant_user')),
    author_id   UUID NOT NULL,
    message     TEXT,
    proposal_id UUID REFERENCES platform.subscription_request_proposal(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_request_comment_content
        CHECK (message IS NOT NULL OR proposal_id IS NOT NULL)
);

CREATE INDEX idx_subscription_request_comment_thread
    ON platform.subscription_request_comment (request_id, created_at, id);
