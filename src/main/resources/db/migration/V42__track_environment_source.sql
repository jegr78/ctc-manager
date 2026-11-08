ALTER TABLE tracks ADD COLUMN environment_source VARCHAR(1500) DEFAULT NULL;
ALTER TABLE tracks ADD COLUMN environment_import_hash VARCHAR(64) DEFAULT NULL;
ALTER TABLE tracks ADD COLUMN environment_override BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE tracks SET environment_override = TRUE WHERE environment_version > 0 OR rain_capability <> 'UNKNOWN' OR (time_of_day_options IS NOT NULL AND TRIM(time_of_day_options) <> '');
ALTER TABLE tracks ADD CONSTRAINT chk_track_environment_source CHECK ((environment_source IS NULL AND environment_import_hash IS NULL) OR (environment_source IS NOT NULL AND environment_import_hash IS NOT NULL AND CHAR_LENGTH(environment_import_hash) = 64));
ALTER TABLE tracks ADD CONSTRAINT chk_track_environment_override CHECK (environment_override IN (FALSE, TRUE));
