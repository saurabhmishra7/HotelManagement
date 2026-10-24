ALTER TABLE platform.plan ADD COLUMN bar_enabled BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE platform.tenant_subscription ADD COLUMN bar_enabled BOOLEAN NOT NULL DEFAULT true;

-- Existing plans and paid-through subscriptions retain their current Bar access.
UPDATE platform.plan p SET bar_enabled = false
WHERE NOT EXISTS (SELECT 1 FROM platform.plan_module pm
                  WHERE pm.plan_id = p.id AND pm.module = 'restaurant');

UPDATE platform.tenant_subscription s SET bar_enabled = false
WHERE NOT EXISTS (SELECT 1 FROM platform.tenant_subscription_module sm
                  WHERE sm.subscription_id = s.id AND sm.module = 'restaurant');
