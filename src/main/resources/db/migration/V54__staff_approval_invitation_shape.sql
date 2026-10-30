ALTER TABLE core.staff_approval_request
    DROP CONSTRAINT ck_staff_approval_shape;

ALTER TABLE core.staff_approval_request
    ADD CONSTRAINT ck_staff_approval_shape CHECK (
        (request_type = 'create' AND target_user_id IS NULL AND name IS NOT NULL AND phone IS NOT NULL
            AND (email IS NOT NULL OR password_hash IS NOT NULL))
        OR (request_type = 'roles' AND target_user_id IS NOT NULL AND previous_role_ids IS NOT NULL)
    );
