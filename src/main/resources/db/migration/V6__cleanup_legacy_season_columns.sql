-- Phase 61 MIGR-06 + D-01 scope-extension: drop all legacy season-level fields and bridge tables.
-- After Phase 56-60 the canonical model is Season -> SeasonPhase -> Matchday/Playoff;
-- the columns/tables dropped here are denormalized residue from the pre-v1.9 flat model.
-- Order: 1) M:N table, 2) named FK/UK constraints, 3) FK indexes, 4) bridge FK columns,
--        5) seasons legacy columns.
-- Compatible with H2 2.x and MariaDB 10.7+.
-- IRREVERSIBLE -- ops must take a backup before applying to prod (Tracked Behavior Change).

DROP TABLE IF EXISTS playoff_seasons;

-- Drop named FK/UK constraints explicitly before dropping the columns they reference.
-- MariaDB 10.7+ does NOT auto-cascade named-constraint drops on column drop; H2 ignores
-- these IF EXISTS clauses gracefully when the constraint name is unknown.
ALTER TABLE matchdays DROP CONSTRAINT IF EXISTS fk_md_season;
ALTER TABLE playoffs DROP CONSTRAINT IF EXISTS fk_playoff_season;
ALTER TABLE playoffs DROP CONSTRAINT IF EXISTS uk_playoff_season;

-- V2 created explicit FK indexes on the bridge columns; drop them up-front so the column-drop
-- below cannot fail on "index references missing column" under MariaDB. H2 auto-drops indexes
-- with the column, but `DROP INDEX IF EXISTS` is portable and safe on both engines.
DROP INDEX IF EXISTS idx_matchdays_season_id;
DROP INDEX IF EXISTS idx_playoffs_season_id;

ALTER TABLE matchdays DROP COLUMN season_id;
ALTER TABLE playoffs DROP COLUMN season_id;

-- Drop the named scoring FKs on seasons before the underlying column drop. V5 made them nullable,
-- but the named constraint itself still needs to be dropped explicitly on MariaDB.
ALTER TABLE seasons DROP CONSTRAINT IF EXISTS fk_season_race_scoring;
ALTER TABLE seasons DROP CONSTRAINT IF EXISTS fk_season_match_scoring;

ALTER TABLE seasons DROP COLUMN format;
ALTER TABLE seasons DROP COLUMN total_rounds;
ALTER TABLE seasons DROP COLUMN legs;
ALTER TABLE seasons DROP COLUMN event_duration_minutes;
ALTER TABLE seasons DROP COLUMN start_date;
ALTER TABLE seasons DROP COLUMN end_date;
ALTER TABLE seasons DROP COLUMN race_scoring_id;
ALTER TABLE seasons DROP COLUMN match_scoring_id;
