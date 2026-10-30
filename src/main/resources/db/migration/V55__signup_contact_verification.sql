ALTER TABLE platform.public_signup_intent
    ALTER COLUMN payment_order_id DROP NOT NULL,
    ADD COLUMN signup_mode VARCHAR(10) NOT NULL DEFAULT 'standard'
        CHECK (signup_mode IN ('standard', 'trial')),
    ADD COLUMN email_verification_token_hash VARCHAR(64),
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD COLUMN email_verification_sent_at TIMESTAMPTZ,
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN phone_verified_at TIMESTAMPTZ,
    ADD COLUMN continuation_token_hash VARCHAR(64);

ALTER TABLE platform.public_signup_intent DROP CONSTRAINT public_signup_intent_status_check;
ALTER TABLE platform.public_signup_intent ADD CONSTRAINT public_signup_intent_status_check
    CHECK (status IN ('pending', 'email_pending', 'email_verified', 'payment_pending', 'completed', 'expired'));

DROP INDEX platform.uq_public_signup_pending_email;
CREATE UNIQUE INDEX uq_public_signup_pending_email
    ON platform.public_signup_intent (lower(owner_email))
    WHERE status IN ('pending', 'email_pending', 'email_verified', 'payment_pending');

CREATE UNIQUE INDEX uq_public_signup_email_token
    ON platform.public_signup_intent (email_verification_token_hash)
    WHERE email_verification_token_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_public_signup_continuation_token
    ON platform.public_signup_intent (continuation_token_hash)
    WHERE continuation_token_hash IS NOT NULL;

ALTER TABLE core.users ADD COLUMN phone_verified_at TIMESTAMPTZ;
