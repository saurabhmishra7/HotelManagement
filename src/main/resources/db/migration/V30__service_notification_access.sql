UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["ORDER_CONFIRM"]'::jsonb
WHERE upper(name) = 'WAITER'
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["ORDER_CONFIRM"]'::jsonb;

UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["ROOM_SERVICE_REQUEST_HANDLE"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER', 'HOUSEKEEPING')
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["ROOM_SERVICE_REQUEST_HANDLE"]'::jsonb;
