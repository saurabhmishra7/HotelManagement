ALTER TABLE restaurant.menu_item
    ADD COLUMN food_type VARCHAR(10) NOT NULL DEFAULT 'VEG';

UPDATE restaurant.menu_item
SET food_type = CASE WHEN veg_flag THEN 'VEG' ELSE 'NON_VEG' END;

ALTER TABLE restaurant.menu_item
    ADD CONSTRAINT menu_item_food_type_check
    CHECK (food_type IN ('VEG', 'NON_VEG', 'EGG'));
