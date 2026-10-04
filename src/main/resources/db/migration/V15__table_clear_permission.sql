-- Waiters and restaurant managers can release a billed table after turnover.
UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["TABLE_CLEAR"]'::jsonb
WHERE upper(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER', 'RESTAURANT_MANAGER', 'WAITER')
  AND NOT COALESCE(permissions, '[]'::jsonb) @> '["TABLE_CLEAR"]'::jsonb;
