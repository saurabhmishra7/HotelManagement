ALTER TABLE core.payment DROP CONSTRAINT IF EXISTS payment_mode_check;
ALTER TABLE core.payment ADD CONSTRAINT payment_mode_check
    CHECK (mode IN ('cash', 'card', 'upi', 'account', 'advance'));

CREATE UNIQUE INDEX uq_stay_advance_payment_reference
    ON core.payment (tenant_id, reference)
    WHERE mode = 'advance';
