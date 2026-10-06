UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["STAY_MANAGE"]'::jsonb
WHERE UPPER(name) = 'FRONT_DESK'
  AND NOT COALESCE(permissions, '[]'::jsonb) ? 'STAY_MANAGE';
