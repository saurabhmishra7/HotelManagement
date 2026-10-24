CREATE TABLE core.user_role (
    user_id UUID NOT NULL REFERENCES core.users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES core.role(id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_role_role ON core.user_role(role_id);
-- Retain users.role_id as the primary role for older clients and tokens.
INSERT INTO core.user_role(user_id, role_id)
SELECT id, role_id FROM core.users WHERE role_id IS NOT NULL;
