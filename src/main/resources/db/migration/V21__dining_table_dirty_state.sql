-- Track restaurant table turnover after guests leave and before cleaning is complete.
ALTER TABLE restaurant.dining_table
    DROP CONSTRAINT IF EXISTS dining_table_status_check;

ALTER TABLE restaurant.dining_table
    ADD CONSTRAINT chk_dining_table_status
    CHECK (status IN ('free', 'occupied', 'billed', 'dirty'));
