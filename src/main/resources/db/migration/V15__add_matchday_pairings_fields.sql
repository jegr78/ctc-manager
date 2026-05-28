ALTER TABLE matchdays ADD COLUMN pick_deadline TIMESTAMP NULL;

ALTER TABLE matchdays ADD COLUMN scheduled_weekend VARCHAR(64) NULL;

ALTER TABLE discord_global_config ADD COLUMN matchday_pairings_template TEXT NULL;
