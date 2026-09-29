ALTER TABLE core.tenant ADD COLUMN tenant_code VARCHAR(12);

UPDATE core.tenant
SET tenant_code = upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12))
WHERE tenant_code IS NULL;

ALTER TABLE core.tenant ALTER COLUMN tenant_code SET NOT NULL;
ALTER TABLE core.tenant ADD CONSTRAINT uq_tenant_tenant_code UNIQUE (tenant_code);
