-- Existing management roles need Bar display access even though permissions are stored as JSON.
UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["BAR_READ"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER', 'RESTAURANT_MANAGER')
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["BAR_READ"]'::jsonb;

UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["BAR_MANAGE"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER', 'RESTAURANT_MANAGER')
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["BAR_MANAGE"]'::jsonb;
