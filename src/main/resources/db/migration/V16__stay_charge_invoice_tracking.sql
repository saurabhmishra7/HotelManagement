-- Track which invoice contains each stay charge and which invoice contains the final room tariff.
ALTER TABLE stay.stay
    ADD COLUMN checkout_invoice_id UUID UNIQUE REFERENCES core.invoice(id);

ALTER TABLE stay.stay_charge
    ADD COLUMN invoice_id UUID REFERENCES core.invoice(id);

-- Preserve links for historical checkout invoices where a room tariff line identifies them.
UPDATE stay.stay s
SET checkout_invoice_id = (
    SELECT i.id
    FROM core.invoice i
    JOIN core.invoice_line_item li
      ON li.tenant_id = i.tenant_id AND li.invoice_id = i.id
    WHERE i.tenant_id = s.tenant_id
      AND i.account_id = s.account_id
      AND i.source_module = 'stay'
      AND li.description LIKE 'Room tariff (%'
    ORDER BY i.created_at DESC
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1 FROM core.invoice i
    JOIN core.invoice_line_item li
      ON li.tenant_id = i.tenant_id AND li.invoice_id = i.id
    WHERE i.tenant_id = s.tenant_id
      AND i.account_id = s.account_id
      AND i.source_module = 'stay'
      AND li.description LIKE 'Room tariff (%'
);

-- Associate existing stay-charge lines with the invoice that already billed them.
UPDATE stay.stay_charge c
SET invoice_id = (
    SELECT i.id
    FROM stay.stay s
    JOIN core.invoice i ON i.tenant_id = s.tenant_id AND i.account_id = s.account_id
    JOIN core.invoice_line_item li ON li.tenant_id = i.tenant_id AND li.invoice_id = i.id
    WHERE s.tenant_id = c.tenant_id AND s.id = c.stay_id
      AND i.source_module = 'stay'
      AND li.description = c.description
      AND li.quantity = 1
      AND li.unit_price = c.amount
    ORDER BY i.created_at DESC
    LIMIT 1
)
WHERE EXISTS (
    SELECT 1
    FROM stay.stay s
    JOIN core.invoice i ON i.tenant_id = s.tenant_id AND i.account_id = s.account_id
    JOIN core.invoice_line_item li ON li.tenant_id = i.tenant_id AND li.invoice_id = i.id
    WHERE s.tenant_id = c.tenant_id AND s.id = c.stay_id
      AND i.source_module = 'stay'
      AND li.description = c.description
      AND li.quantity = 1
      AND li.unit_price = c.amount
);
