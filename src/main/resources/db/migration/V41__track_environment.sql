ALTER TABLE tracks ADD COLUMN time_of_day_options VARCHAR(3000) DEFAULT NULL;
ALTER TABLE tracks ADD COLUMN rain_capability VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE tracks ADD COLUMN environment_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tracks ADD CONSTRAINT chk_track_rain CHECK (CASE rain_capability WHEN 'UNKNOWN' THEN 1 WHEN 'DRY_ONLY' THEN 1 WHEN 'RAIN' THEN 1 ELSE 0 END = 1);
ALTER TABLE tracks ADD CONSTRAINT chk_track_environment_version CHECK (environment_version >= 0);
