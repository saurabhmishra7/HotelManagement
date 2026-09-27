-- V1: Core module
-- Tenant, Role, User, Customer, Account, TaxRule, QRCode,
-- Invoice, InvoiceLineItem, Payment, CreditNote, AuditLog

-- ===================== TENANT =====================
CREATE TABLE core.tenant (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL,
    gstin       VARCHAR(15),
    address     TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== ROLE =====================
CREATE TABLE core.role (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES core.tenant(id),
    name         VARCHAR(50) NOT NULL,
    permissions  JSONB
);

-- ===================== USERS =====================
-- Note: named "users" instead of "user" — "user" is a reserved word in SQL.
CREATE TABLE core.users (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES core.tenant(id),
    name           VARCHAR(100) NOT NULL,
    phone          VARCHAR(15) NOT NULL,
    email          VARCHAR(150),
    password_hash  TEXT NOT NULL,
    pin_hash       TEXT,
    role_id        UUID REFERENCES core.role(id),
    active         BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_phone UNIQUE (tenant_id, phone)
);

-- ===================== CUSTOMER =====================
CREATE TABLE core.customer (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES core.tenant(id),
    name             VARCHAR(150) NOT NULL,
    phone            VARCHAR(15) NOT NULL,
    id_proof_type    VARCHAR(20) CHECK (id_proof_type IN
                        ('aadhaar','passport','voter_id','driving_license','other')),
    id_proof_number  VARCHAR(50),
    address          TEXT,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_tenant_phone UNIQUE (tenant_id, phone)
);

-- ===================== ACCOUNT =====================
-- linked_entity_type/id are deliberately NOT a real FK (polymorphic, see design notes).
CREATE TABLE core.account (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES core.tenant(id),
    opened_by_module    VARCHAR(10) NOT NULL CHECK (opened_by_module IN ('stay','pos','other')),
    linked_entity_type  VARCHAR(30),
    linked_entity_id    UUID,
    status              VARCHAR(10) NOT NULL CHECK (status IN ('open','closed')),
    opened_at           TIMESTAMP NOT NULL DEFAULT now(),
    closed_at           TIMESTAMP
);

-- ===================== TAX RULE =====================
CREATE TABLE core.tax_rule (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES core.tenant(id),
    name             VARCHAR(100) NOT NULL,
    applies_to       VARCHAR(20) NOT NULL CHECK (applies_to IN ('room','restaurant','other')),
    rate_percent     NUMERIC(5,2) NOT NULL,
    itc_eligible     BOOLEAN NOT NULL DEFAULT false,
    effective_from   DATE NOT NULL,
    effective_to     DATE
);

-- ===================== QR CODE =====================
-- target_id is polymorphic (Room or DiningTable) — no hard FK, same pattern as Account.
CREATE TABLE core.qr_code (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES core.tenant(id),
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('dining_table','room')),
    target_id   UUID NOT NULL,
    token       VARCHAR(64) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    rotated_at  TIMESTAMP,
    CONSTRAINT uq_qrcode_tenant_token UNIQUE (tenant_id, token)
);

-- ===================== INVOICE =====================
CREATE TABLE core.invoice (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core.tenant(id),
    invoice_number  VARCHAR(30) NOT NULL,
    financial_year  VARCHAR(9) NOT NULL,
    account_id      UUID REFERENCES core.account(id),
    customer_id     UUID REFERENCES core.customer(id),
    source_module   VARCHAR(20) NOT NULL CHECK (source_module IN
                        ('stay','restaurant','expense','other')),
    subtotal        NUMERIC(10,2) NOT NULL,
    tax_amount      NUMERIC(10,2) NOT NULL,
    total_amount    NUMERIC(10,2) NOT NULL,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('draft','final','credited')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    locked_at       TIMESTAMP,
    CONSTRAINT uq_invoice_tenant_fy_number UNIQUE (tenant_id, financial_year, invoice_number)
);

-- ===================== INVOICE LINE ITEM =====================
CREATE TABLE core.invoice_line_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES core.tenant(id),
    invoice_id   UUID NOT NULL REFERENCES core.invoice(id),
    description  VARCHAR(200) NOT NULL,
    quantity     NUMERIC(8,2) NOT NULL,
    unit_price   NUMERIC(10,2) NOT NULL,
    tax_rule_id  UUID REFERENCES core.tax_rule(id),
    tax_amount   NUMERIC(10,2) NOT NULL,
    line_total   NUMERIC(10,2) NOT NULL
);

-- ===================== PAYMENT =====================
CREATE TABLE core.payment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES core.tenant(id),
    invoice_id  UUID NOT NULL REFERENCES core.invoice(id),
    mode        VARCHAR(10) NOT NULL CHECK (mode IN ('cash','card','upi','account')),
    amount      NUMERIC(10,2) NOT NULL,
    reference   VARCHAR(100),
    paid_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== CREDIT NOTE =====================
CREATE TABLE core.credit_note (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES core.tenant(id),
    original_invoice_id  UUID NOT NULL REFERENCES core.invoice(id),
    reason               TEXT NOT NULL,
    amount               NUMERIC(10,2) NOT NULL,
    approved_by          UUID NOT NULL REFERENCES core.users(id),
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== AUDIT LOG =====================
CREATE TABLE core.audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES core.tenant(id),
    user_id       UUID NOT NULL REFERENCES core.users(id),
    action        VARCHAR(50) NOT NULL,
    entity_type   VARCHAR(50) NOT NULL,
    entity_id     UUID NOT NULL,
    before_value  JSONB,
    after_value   JSONB,
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== INDEXES (tenant-scoped lookups) =====================
CREATE INDEX idx_role_tenant                  ON core.role (tenant_id);
CREATE INDEX idx_users_tenant                 ON core.users (tenant_id);
CREATE INDEX idx_customer_tenant_phone        ON core.customer (tenant_id, phone);
CREATE INDEX idx_account_tenant               ON core.account (tenant_id);
CREATE INDEX idx_account_linked_entity        ON core.account (linked_entity_type, linked_entity_id);
CREATE INDEX idx_taxrule_tenant               ON core.tax_rule (tenant_id);
CREATE INDEX idx_qrcode_target                ON core.qr_code (target_type, target_id);
CREATE INDEX idx_invoice_tenant_fy            ON core.invoice (tenant_id, financial_year);
CREATE INDEX idx_invoicelineitem_invoice      ON core.invoice_line_item (invoice_id);
CREATE INDEX idx_payment_invoice              ON core.payment (invoice_id);
CREATE INDEX idx_creditnote_invoice           ON core.credit_note (original_invoice_id);
CREATE INDEX idx_auditlog_tenant_entity       ON core.audit_log (tenant_id, entity_type, entity_id);