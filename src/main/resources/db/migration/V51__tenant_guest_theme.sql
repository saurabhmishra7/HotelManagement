ALTER TABLE core.tenant ADD COLUMN guest_theme VARCHAR(20) NOT NULL DEFAULT 'inherit';
ALTER TABLE core.tenant ADD CONSTRAINT tenant_guest_theme_check CHECK (guest_theme IN ('inherit', 'earth', 'sage', 'ocean', 'lavender', 'mickey', 'doraemon', 'spongebob'));
