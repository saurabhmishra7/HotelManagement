ALTER TABLE core.tenant
  ADD COLUMN checkout_time TIME NOT NULL DEFAULT '11:00:00',
  ADD COLUMN logo BYTEA,
  ADD COLUMN logo_version UUID;

CREATE TABLE core.tenant_meal_plan (
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  plan VARCHAR(5) NOT NULL CHECK (plan IN ('EP', 'CP', 'MAP', 'AP')),
  PRIMARY KEY (tenant_id, plan)
);
-- Preserve the previously available plans for existing properties.
INSERT INTO core.tenant_meal_plan (tenant_id, plan)
SELECT t.id, p.plan FROM core.tenant t
CROSS JOIN (VALUES ('EP'), ('CP'), ('MAP'), ('AP')) p(plan);

CREATE TABLE core.stay_charge_preset (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  description VARCHAR(200) NOT NULL,
  amount NUMERIC(10,2) NOT NULL CHECK (amount > 0)
);
CREATE UNIQUE INDEX uq_stay_charge_preset_name
  ON core.stay_charge_preset (tenant_id, lower(description));

UPDATE core.role
SET permissions = COALESCE(permissions, '[]'::jsonb) || '["TENANT_PROFILE_MANAGE"]'::jsonb
WHERE UPPER(name) IN ('OWNER', 'HOTEL_ADMIN', 'GENERAL_MANAGER')
  AND NOT COALESCE(permissions, '[]'::jsonb) ? 'TENANT_PROFILE_MANAGE';
