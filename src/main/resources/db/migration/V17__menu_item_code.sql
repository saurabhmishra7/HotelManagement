ALTER TABLE restaurant.menu_item
    ADD COLUMN item_code VARCHAR(40);

CREATE UNIQUE INDEX uq_menu_item_tenant_code
    ON restaurant.menu_item (tenant_id, upper(item_code))
    WHERE item_code IS NOT NULL;
