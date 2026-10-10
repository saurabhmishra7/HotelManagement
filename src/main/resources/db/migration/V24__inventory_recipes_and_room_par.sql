CREATE SCHEMA IF NOT EXISTS inventory;

ALTER TABLE core.tenant_module DROP CONSTRAINT tenant_module_module_check;
ALTER TABLE core.tenant_module ADD CONSTRAINT tenant_module_module_check
  CHECK (module IN ('stay', 'restaurant', 'expense', 'inventory'));
ALTER TABLE platform.plan_module DROP CONSTRAINT plan_module_module_check;
ALTER TABLE platform.plan_module ADD CONSTRAINT plan_module_module_check
  CHECK (module IN ('stay', 'restaurant', 'expense', 'inventory'));
ALTER TABLE platform.tenant_subscription_module DROP CONSTRAINT tenant_subscription_module_module_check;
ALTER TABLE platform.tenant_subscription_module ADD CONSTRAINT tenant_subscription_module_module_check
  CHECK (module IN ('stay', 'restaurant', 'expense', 'inventory'));

CREATE TABLE inventory.item (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  name VARCHAR(150) NOT NULL,
  unit VARCHAR(20) NOT NULL,
  usage_type VARCHAR(20) NOT NULL CHECK (usage_type IN ('restaurant', 'room', 'other')),
  current_stock NUMERIC(10,3) NOT NULL DEFAULT 0,
  average_unit_cost NUMERIC(10,2) NOT NULL DEFAULT 0,
  reorder_threshold NUMERIC(10,3) NOT NULL DEFAULT 0 CHECK (reorder_threshold >= 0),
  active BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT ck_inventory_item_name CHECK (length(trim(name)) > 0),
  CONSTRAINT ck_inventory_item_unit CHECK (length(trim(unit)) > 0)
);
CREATE INDEX idx_inventory_item_tenant_active ON inventory.item (tenant_id, active);

CREATE TABLE inventory.stock_purchase (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  item_id UUID NOT NULL REFERENCES inventory.item(id),
  quantity NUMERIC(10,3) NOT NULL CHECK (quantity > 0),
  unit_cost NUMERIC(10,2) NOT NULL CHECK (unit_cost >= 0),
  total_cost NUMERIC(10,2) NOT NULL CHECK (total_cost >= 0),
  vendor_name VARCHAR(150),
  expense_id UUID REFERENCES expense.expense(id),
  purchased_by UUID NOT NULL REFERENCES core.users(id),
  purchased_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_purchase_item_date ON inventory.stock_purchase (tenant_id, item_id, purchased_at DESC);
CREATE INDEX idx_inventory_purchase_expense ON inventory.stock_purchase (expense_id) WHERE expense_id IS NOT NULL;

CREATE TABLE inventory.stock_consumption (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  item_id UUID NOT NULL REFERENCES inventory.item(id),
  quantity NUMERIC(10,3) NOT NULL,
  consumption_type VARCHAR(30) NOT NULL CHECK (consumption_type IN ('recipe_deduction', 'room_par_deduction', 'manual_adjustment')),
  reference_type VARCHAR(20) CHECK (reference_type IN ('order_item', 'room')),
  reference_id UUID,
  note TEXT,
  created_by UUID REFERENCES core.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_inventory_consumption_quantity CHECK
    ((consumption_type = 'manual_adjustment' AND quantity <> 0) OR
     (consumption_type <> 'manual_adjustment' AND quantity > 0)),
  CONSTRAINT ck_inventory_adjustment_note CHECK
    (consumption_type <> 'manual_adjustment' OR length(trim(coalesce(note, ''))) > 0)
);
CREATE INDEX idx_inventory_consumption_item_date ON inventory.stock_consumption (tenant_id, item_id, created_at DESC);
CREATE INDEX idx_inventory_consumption_reference ON inventory.stock_consumption (tenant_id, reference_type, reference_id);

CREATE TABLE restaurant.recipe (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  menu_item_id UUID NOT NULL UNIQUE REFERENCES restaurant.menu_item(id)
);
CREATE INDEX idx_recipe_tenant ON restaurant.recipe (tenant_id);

CREATE TABLE restaurant.recipe_ingredient (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  recipe_id UUID NOT NULL REFERENCES restaurant.recipe(id) ON DELETE CASCADE,
  inventory_item_id UUID NOT NULL REFERENCES inventory.item(id),
  quantity_required NUMERIC(10,3) NOT NULL CHECK (quantity_required > 0),
  CONSTRAINT uq_recipe_ingredient_item UNIQUE (recipe_id, inventory_item_id)
);
CREATE INDEX idx_recipe_ingredient_inventory ON restaurant.recipe_ingredient (inventory_item_id);

CREATE TABLE stay.room_par_item (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES core.tenant(id),
  room_type VARCHAR(50) NOT NULL,
  inventory_item_id UUID NOT NULL REFERENCES inventory.item(id),
  quantity_per_clean NUMERIC(10,3) NOT NULL CHECK (quantity_per_clean > 0),
  CONSTRAINT uq_room_par_item UNIQUE (tenant_id, room_type, inventory_item_id)
);
CREATE INDEX idx_room_par_tenant_type ON stay.room_par_item (tenant_id, room_type);
