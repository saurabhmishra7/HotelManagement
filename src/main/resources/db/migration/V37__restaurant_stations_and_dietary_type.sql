CREATE TABLE restaurant.station (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uq_restaurant_station_tenant_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_restaurant_station_tenant ON restaurant.station (tenant_id);

INSERT INTO restaurant.station (tenant_id, code, name)
SELECT id, 'kitchen', 'Kitchen' FROM core.tenant
UNION ALL
SELECT id, 'bar', 'Bar' FROM core.tenant;

ALTER TABLE restaurant.menu_item DROP CONSTRAINT IF EXISTS menu_item_station_check;
ALTER TABLE restaurant.kot_batch DROP CONSTRAINT IF EXISTS kot_batch_station_check;
ALTER TABLE restaurant.menu_item ALTER COLUMN station TYPE VARCHAR(32);
ALTER TABLE restaurant.kot_batch ALTER COLUMN station TYPE VARCHAR(32);
ALTER TABLE restaurant.menu_item DROP CONSTRAINT menu_item_food_type_check;
ALTER TABLE restaurant.menu_item ADD CONSTRAINT menu_item_food_type_check
    CHECK (food_type IN ('VEG', 'NON_VEG', 'EGG', 'NONE'));
