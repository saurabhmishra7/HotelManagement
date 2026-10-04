-- Existing manager roles gain access to the new service performance view.
UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["SERVICE_PERFORMANCE_READ"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER', 'RESTAURANT_MANAGER')
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["SERVICE_PERFORMANCE_READ"]'::jsonb;
