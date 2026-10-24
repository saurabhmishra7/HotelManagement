ALTER TABLE core.tax_rule
    ADD COLUMN tax_category VARCHAR(60);

UPDATE core.tax_rule
SET tax_category = CASE applies_to
    WHEN 'room' THEN 'Room stay'
    WHEN 'restaurant' THEN 'Restaurant food and drinks'
    ELSE 'Other'
END;

ALTER TABLE core.tax_rule
    ALTER COLUMN tax_category SET NOT NULL,
    ADD CONSTRAINT tax_rule_category_not_blank CHECK (length(btrim(tax_category)) > 0);
