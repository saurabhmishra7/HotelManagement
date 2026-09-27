-- V2: Stay module
-- Room, Stay, StayCharge, FormCSubmission
-- Allowed to have real FKs INTO core (Stay depends on Core, never the reverse).

-- ===================== ROOM =====================
CREATE TABLE stay.room (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES core.tenant(id),
    room_number   VARCHAR(10) NOT NULL,
    room_type     VARCHAR(50) NOT NULL,
    floor         VARCHAR(10),
    base_tariff   NUMERIC(10,2) NOT NULL,
    status        VARCHAR(10) NOT NULL CHECK (status IN ('vacant','occupied','dirty','clean')),
    CONSTRAINT uq_room_tenant_number UNIQUE (tenant_id, room_number)
);

-- ===================== STAY =====================
CREATE TABLE stay.stay (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES core.tenant(id),
    room_id                 UUID NOT NULL REFERENCES stay.room(id),
    customer_id             UUID NOT NULL REFERENCES core.customer(id),
    account_id              UUID NOT NULL REFERENCES core.account(id),
    guest_count             SMALLINT NOT NULL,
    is_foreign_guest        BOOLEAN NOT NULL DEFAULT false,
    plan                    VARCHAR(5) NOT NULL DEFAULT 'EP' CHECK (plan IN ('EP','CP','MAP','AP')),
    tariff                  NUMERIC(10,2) NOT NULL,
    check_in_at             TIMESTAMP NOT NULL,
    expected_check_out_at   TIMESTAMP NOT NULL,
    actual_check_out_at     TIMESTAMP,
    advance_paid            NUMERIC(10,2) NOT NULL DEFAULT 0,
    status                  VARCHAR(15) NOT NULL CHECK (status IN ('active','checked_out')),
    CONSTRAINT uq_stay_account UNIQUE (account_id)
);

-- ===================== STAY CHARGE =====================
CREATE TABLE stay.stay_charge (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES core.tenant(id),
    stay_id      UUID NOT NULL REFERENCES stay.stay(id),
    description  VARCHAR(200) NOT NULL,
    amount       NUMERIC(10,2) NOT NULL,
    added_by     UUID NOT NULL REFERENCES core.users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== FORM C SUBMISSION =====================
CREATE TABLE stay.form_c_submission (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES core.tenant(id),
    stay_id            UUID NOT NULL REFERENCES stay.stay(id),
    submitted_at       TIMESTAMP,
    status             VARCHAR(10) NOT NULL CHECK (status IN ('pending','submitted')),
    reference_number   VARCHAR(50),
    CONSTRAINT uq_formc_stay UNIQUE (stay_id)
);

-- ===================== INDEXES =====================
CREATE INDEX idx_room_tenant_number     ON stay.room (tenant_id, room_number);
CREATE INDEX idx_stay_tenant            ON stay.stay (tenant_id);
CREATE INDEX idx_stay_room              ON stay.stay (room_id);
CREATE INDEX idx_stay_customer          ON stay.stay (customer_id);
CREATE INDEX idx_staycharge_stay        ON stay.stay_charge (stay_id);
CREATE INDEX idx_formc_stay             ON stay.form_c_submission (stay_id);