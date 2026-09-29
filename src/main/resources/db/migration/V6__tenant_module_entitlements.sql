-- Tenant-level product access. Existing tenants retain the modules available before this change.
CREATE TABLE core.tenant_module (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES core.tenant(id),
    module       VARCHAR(20) NOT NULL CHECK (module IN ('stay', 'restaurant', 'expense')),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('active', 'suspended', 'cancelled')),
    activated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ,
    CONSTRAINT uq_tenant_module UNIQUE (tenant_id, module)
);

CREATE INDEX idx_tenant_module_tenant_status
    ON core.tenant_module (tenant_id, status);

INSERT INTO core.tenant_module (tenant_id, module, status)
SELECT tenant.id, modules.module, 'active'
FROM core.tenant
CROSS JOIN (VALUES ('stay'), ('restaurant'), ('expense')) AS modules(module);
