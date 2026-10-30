-- Historical advances have no known method; do not invent one.
ALTER TABLE stay.stay ADD COLUMN advance_payment_mode VARCHAR(10);
ALTER TABLE stay.stay ADD CONSTRAINT ck_stay_advance_payment_mode
  CHECK (advance_payment_mode IS NULL OR advance_payment_mode IN ('cash', 'card', 'upi'));
