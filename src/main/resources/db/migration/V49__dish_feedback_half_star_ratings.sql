ALTER TABLE restaurant.dish_feedback
    ALTER COLUMN stars TYPE NUMERIC(2, 1)
    USING stars::NUMERIC(2, 1);

ALTER TABLE restaurant.dish_feedback
    DROP CONSTRAINT IF EXISTS dish_feedback_stars_check;

ALTER TABLE restaurant.dish_feedback
    ADD CONSTRAINT ck_dish_feedback_stars_half_step
    CHECK (stars BETWEEN 1 AND 5 AND stars * 2 = trunc(stars * 2));
