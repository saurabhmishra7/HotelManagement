ALTER TABLE restaurant.menu_item
    ADD COLUMN serving_size_ml INTEGER;

ALTER TABLE restaurant.menu_item
    ADD CONSTRAINT menu_item_serving_size_ml_check
    CHECK (serving_size_ml IS NULL OR serving_size_ml BETWEEN 1 AND 10000);
