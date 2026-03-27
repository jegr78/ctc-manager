-- Drop the UNIQUE constraint on teams.short_name
-- H2 auto-names inline UNIQUE constraints, so we query and drop dynamically
-- Simplest approach: recreate column without unique constraint via ALTER TABLE
ALTER TABLE teams ADD COLUMN short_name_new VARCHAR(50);
UPDATE teams SET short_name_new = short_name;
ALTER TABLE teams DROP COLUMN short_name;
ALTER TABLE teams ALTER COLUMN short_name_new RENAME TO short_name;
ALTER TABLE teams ALTER COLUMN short_name SET NOT NULL;
