ALTER TABLE stay.room_service_request
    ADD COLUMN attendant_confirmed_at TIMESTAMPTZ,
    ADD COLUMN attendant_confirmed_by UUID REFERENCES core.users(id),
    ADD COLUMN guest_confirmed_at TIMESTAMPTZ;

-- Preserve the staff confirmation recorded for requests completed before this workflow.
UPDATE stay.room_service_request
SET attendant_confirmed_at = completed_at,
    attendant_confirmed_by = completed_by
WHERE status = 'completed';
