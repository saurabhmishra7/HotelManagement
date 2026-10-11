ALTER TABLE stay.stay_charge
    ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN unit_price NUMERIC(10,2);

UPDATE stay.stay_charge SET unit_price = amount;

ALTER TABLE stay.stay_charge
    ALTER COLUMN unit_price SET NOT NULL,
    ADD CONSTRAINT stay_charge_quantity_positive CHECK (quantity BETWEEN 1 AND 999);
