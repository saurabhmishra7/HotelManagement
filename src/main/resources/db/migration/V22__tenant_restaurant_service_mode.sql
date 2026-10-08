-- Select whether restaurant tickets are handled on screen, printed, or both.
ALTER TABLE core.tenant
  ADD COLUMN restaurant_service_mode VARCHAR(20) NOT NULL DEFAULT 'kitchen_display'
  CHECK (restaurant_service_mode IN ('kitchen_display', 'thermal_printer', 'both'));
