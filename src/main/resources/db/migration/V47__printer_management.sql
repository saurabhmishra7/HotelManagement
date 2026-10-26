CREATE TABLE restaurant.printer_device (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  label VARCHAR(100) NOT NULL,
  connection_type VARCHAR(20) NOT NULL CHECK (connection_type IN ('webusb','network_http','agent_bridge')),
  usb_vendor_id INTEGER CHECK (usb_vendor_id BETWEEN 0 AND 65535),
  usb_product_id INTEGER CHECK (usb_product_id BETWEEN 0 AND 65535),
  usb_serial VARCHAR(200),
  network_ip_address VARCHAR(255),
  printer_brand VARCHAR(50),
  printer_model VARCHAR(100),
  paper_width INTEGER NOT NULL DEFAULT 80 CHECK (paper_width IN (58,80)),
  active BOOLEAN NOT NULL DEFAULT true,
  tested_at TIMESTAMPTZ,
  paired_by UUID NOT NULL REFERENCES core.users(id),
  paired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ,
  key_hash VARCHAR(64),
  UNIQUE(tenant_id,id)
);
-- Separate routes allow one physical printer to serve several purposes.
CREATE TABLE restaurant.printer_route (
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('kitchen_kot','bar_kot','restaurant_bill','hotel_reception')),
  device_id UUID NOT NULL,
  PRIMARY KEY(tenant_id,purpose),
  FOREIGN KEY(tenant_id,device_id) REFERENCES restaurant.printer_device(tenant_id,id)
);
CREATE TABLE restaurant.printer_job (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('kitchen_kot','bar_kot','restaurant_bill','hotel_reception')),
  batch_id UUID REFERENCES restaurant.kot_batch(id),
  invoice_id UUID REFERENCES core.invoice(id),
  device_id UUID,
  snapshot JSONB NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','dispatching','printed','uncertain','resolved')),
  claim_id UUID,
  claimed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  detail VARCHAR(500),
  UNIQUE(batch_id),
  CHECK ((batch_id IS NOT NULL) <> (invoice_id IS NOT NULL)),
  FOREIGN KEY(tenant_id,device_id) REFERENCES restaurant.printer_device(tenant_id,id)
);
CREATE INDEX printer_job_pending ON restaurant.printer_job(tenant_id,purpose,created_at) WHERE status = 'pending';
CREATE INDEX printer_job_device ON restaurant.printer_job(tenant_id,device_id,status);
CREATE INDEX printer_job_history ON restaurant.printer_job(tenant_id,created_at DESC);
CREATE TABLE platform.printer_compatibility (
  id UUID PRIMARY KEY,
  brand VARCHAR(50) NOT NULL,
  model VARCHAR(100) NOT NULL,
  connection_type VARCHAR(30) NOT NULL CHECK (connection_type IN ('usb_webusb','network_http_eposprint','network_http_other','unsupported')),
  verified_status VARCHAR(20) NOT NULL CHECK (verified_status IN ('verified','community_reported','untested','known_incompatible')),
  notes TEXT,
  added_by UUID NOT NULL REFERENCES platform.platform_admin(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX printer_compatibility_model ON platform.printer_compatibility(lower(brand),lower(model));
UPDATE core.role SET permissions = COALESCE(permissions,'[]'::jsonb) || '["PRINTER_OPERATOR"]'::jsonb
WHERE upper(name) IN ('OWNER','HOTEL_ADMIN','GENERAL_MANAGER','RESTAURANT_MANAGER')
AND NOT COALESCE(permissions,'[]'::jsonb) @> '["PRINTER_OPERATOR"]'::jsonb;
INSERT INTO core.role(id,tenant_id,name,permissions)
SELECT gen_random_uuid(),t.id,'PRINTER_OPERATOR','["PRINTER_OPERATOR"]'::jsonb FROM core.tenant t
WHERE NOT EXISTS(SELECT 1 FROM core.role r WHERE r.tenant_id=t.id AND upper(r.name)='PRINTER_OPERATOR');
