-- V3: Restaurant module
-- MenuCategory, MenuItem, DiningTable, RestaurantOrder, OrderItem, KOTBatch, RestaurantBill
-- Allowed to reference core (tax_rule, invoice) and stay (stay) — never the reverse.

-- ===================== MENU CATEGORY =====================
CREATE TABLE restaurant.menu_category (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES core.tenant(id),
    name        VARCHAR(100) NOT NULL,
    sort_order  SMALLINT NOT NULL DEFAULT 0
);

-- ===================== MENU ITEM =====================
CREATE TABLE restaurant.menu_item (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES core.tenant(id),
    category_id   UUID NOT NULL REFERENCES restaurant.menu_category(id),
    name          VARCHAR(150) NOT NULL,
    price         NUMERIC(10,2) NOT NULL,
    tax_rule_id   UUID REFERENCES core.tax_rule(id),
    station       VARCHAR(10) NOT NULL CHECK (station IN ('kitchen','bar')),
    veg_flag      BOOLEAN NOT NULL DEFAULT true,
    active        BOOLEAN NOT NULL DEFAULT true
);

-- ===================== DINING TABLE =====================
CREATE TABLE restaurant.dining_table (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES core.tenant(id),
    table_number  VARCHAR(10) NOT NULL,
    section       VARCHAR(50),
    status        VARCHAR(10) NOT NULL CHECK (status IN ('free','occupied','billed'))
);

-- ===================== RESTAURANT ORDER =====================
CREATE TABLE restaurant.restaurant_order (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES core.tenant(id),
    order_type            VARCHAR(15) NOT NULL CHECK (order_type IN ('dine_in','room_service','takeaway')),
    table_id              UUID REFERENCES restaurant.dining_table(id),
    stay_id               UUID REFERENCES stay.stay(id),
    status                VARCHAR(10) NOT NULL CHECK (status IN ('open','billed','cancelled')),
    order_source          VARCHAR(10) NOT NULL CHECK (order_source IN ('staff','qr_guest')),
    confirmation_status   VARCHAR(15) NOT NULL CHECK (confirmation_status IN
                              ('not_required','pending','confirmed')),
    confirmed_by          UUID REFERENCES core.users(id),
    confirmed_at          TIMESTAMP,
    bill_requested_at     TIMESTAMP,
    opened_by             UUID REFERENCES core.users(id),
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- ===================== KOT BATCH =====================
-- Created before order_item because order_item.kot_batch_id references it.
CREATE TABLE restaurant.kot_batch (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES core.tenant(id),
    order_id      UUID NOT NULL REFERENCES restaurant.restaurant_order(id),
    station       VARCHAR(10) NOT NULL CHECK (station IN ('kitchen','bar')),
    batch_number  SMALLINT NOT NULL,
    printed_at    TIMESTAMP
);

-- ===================== ORDER ITEM =====================
CREATE TABLE restaurant.order_item (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES core.tenant(id),
    order_id       UUID NOT NULL REFERENCES restaurant.restaurant_order(id),
    menu_item_id   UUID NOT NULL REFERENCES restaurant.menu_item(id),
    quantity       SMALLINT NOT NULL,
    notes          VARCHAR(200),
    status         VARCHAR(20) NOT NULL CHECK (status IN
                       ('pending_confirmation','sent','preparing','served','cancelled')),
    kot_batch_id   UUID REFERENCES restaurant.kot_batch(id)
);

-- ===================== RESTAURANT BILL =====================
CREATE TABLE restaurant.restaurant_bill (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES core.tenant(id),
    order_id               UUID NOT NULL REFERENCES restaurant.restaurant_order(id),
    invoice_id             UUID NOT NULL REFERENCES core.invoice(id),
    discount_amount        NUMERIC(10,2) NOT NULL DEFAULT 0,
    discount_approved_by   UUID REFERENCES core.users(id),
    settlement_mode        VARCHAR(10) NOT NULL CHECK (settlement_mode IN ('cash','card','upi','account')),
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_bill_order UNIQUE (order_id),
    CONSTRAINT uq_bill_invoice UNIQUE (invoice_id)
);

-- ===================== INDEXES =====================
CREATE INDEX idx_menucategory_tenant   ON restaurant.menu_category (tenant_id);
CREATE INDEX idx_menuitem_tenant       ON restaurant.menu_item (tenant_id);
CREATE INDEX idx_menuitem_category     ON restaurant.menu_item (category_id);
CREATE INDEX idx_diningtable_tenant    ON restaurant.dining_table (tenant_id, table_number);
CREATE INDEX idx_order_tenant          ON restaurant.restaurant_order (tenant_id);
CREATE INDEX idx_order_table           ON restaurant.restaurant_order (table_id);
CREATE INDEX idx_order_stay            ON restaurant.restaurant_order (stay_id);
CREATE INDEX idx_kotbatch_order        ON restaurant.kot_batch (order_id);
CREATE INDEX idx_orderitem_order       ON restaurant.order_item (order_id);
CREATE INDEX idx_orderitem_kotbatch    ON restaurant.order_item (kot_batch_id);
CREATE INDEX idx_bill_order            ON restaurant.restaurant_bill (order_id);