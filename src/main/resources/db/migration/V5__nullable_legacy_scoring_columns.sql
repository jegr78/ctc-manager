-- Phase 60 UI-01: legacy scoring FK columns become nullable
-- Slim Season form (UI-01) no longer requires raceScoring/matchScoring at season-creation time;
-- scoring is now configured per-phase via the new Phase form. The auto-bootstrapped REGULAR phase
-- starts with scoring=null and the user fills it in from the Phase tab.
-- The columns themselves remain (existing data preserved) — a future cleanup may drop them once
-- all consumers read scoring from SeasonPhase exclusively.
-- Compatible with H2 2.x and MariaDB 10.7+.

ALTER TABLE seasons ALTER COLUMN race_scoring_id DROP NOT NULL;
ALTER TABLE seasons ALTER COLUMN match_scoring_id DROP NOT NULL;

ALTER TABLE season_phases ALTER COLUMN race_scoring_id DROP NOT NULL;
ALTER TABLE season_phases ALTER COLUMN match_scoring_id DROP NOT NULL;
