-- Existing admin roles need the permission introduced for room creation.
UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["ROOM_MANAGE"]'::jsonb
WHERE name IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER')
  AND NOT COALESCE(permissions, '[]'::jsonb) ? 'ROOM_MANAGE';
