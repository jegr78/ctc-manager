-- QUAL-03 fixture: legacy migrated season WITHOUT playoff (post-V6 schema).
-- Simulates a season that originally lived in pre-v1.9 flat shape, was migrated by V4 (REGULAR
-- phase backfilled, scoring + format moved to phase, matchdays re-keyed), and now exists in the
-- post-V6 canonical form. Read-only assertions follow in LegacyMigratedSeasonE2ETest.
-- Deterministic UUIDs in the 0000-0061-0000-* range to avoid PK collisions with other fixtures
-- and DevDataSeeder data.

-- Reference data: scoring rows on the phase (post-V6: scoring lives on SeasonPhase, not Season).
INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000001', 'Phase61-Legacy-RaceScoring',
        '25,18,15,12,10,8,6,4,2,1', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000002', 'Phase61-Legacy-MatchScoring', 3, 1, 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Season post-V6: identity-only columns (NO format/legs/scoring/dates).
INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000010', 'Test-Legacy-Season-2098', 2098, 1, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- One REGULAR phase carrying scoring + legs + format (canonical post-Phase-57 form, LEAGUE layout).
INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000011',
        '00000000-0000-0061-0000-000000000010',
        0, 'REGULAR', 'LEAGUE', 'LEAGUE', 2,
        '00000000-0000-0061-0000-000000000001',
        '00000000-0000-0061-0000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Two teams in the season. Test-data isolation per CLAUDE.md: T-LEG- prefix + Test display name.
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000020', 'Test Legacy Team A', 'T-LEG-A',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000021', 'Test Legacy Team B', 'T-LEG-B',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- season_teams: legacy season-wide roster (MODEL-08 keeps season_teams alongside phase_teams).
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000030', '00000000-0000-0061-0000-000000000010',
        '00000000-0000-0061-0000-000000000020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000031', '00000000-0000-0061-0000-000000000010',
        '00000000-0000-0061-0000-000000000021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- phase_teams on the REGULAR phase (LEAGUE layout, group_id NULL per MIGR-05).
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000040', '00000000-0000-0061-0000-000000000011',
        '00000000-0000-0061-0000-000000000020', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000041', '00000000-0000-0061-0000-000000000011',
        '00000000-0000-0061-0000-000000000021', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Two matchdays on the REGULAR phase (post-V6: no season_id column on matchdays).
INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000050', '00000000-0000-0061-0000-000000000011',
        NULL, 'Matchday 1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000051', '00000000-0000-0061-0000-000000000011',
        NULL, 'Matchday 2', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- One race per matchday. V1 schema authoritative: races (id, matchday_id, ...optional FKs...,
-- created_at, updated_at). Optional FK columns left NULL for the read-only fixture.
INSERT INTO races (id, matchday_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000060', '00000000-0000-0061-0000-000000000050',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO races (id, matchday_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000061', '00000000-0000-0061-0000-000000000051',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Drivers. V1 schema authoritative: drivers (id, psn_id, nickname NOT NULL, active, ...).
-- The column is `nickname`, NOT `name` -- enforce V1 contract per acceptance grep.
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000070', 'T_legacy_drv01', 'Test Legacy Driver A', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000071', 'T_legacy_drv02', 'Test Legacy Driver B', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- season_drivers (legacy roster shape preserved by MODEL-08).
INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000080', '00000000-0000-0061-0000-000000000010',
        '00000000-0000-0061-0000-000000000070', '00000000-0000-0061-0000-000000000020',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-0000-000000000081', '00000000-0000-0061-0000-000000000010',
        '00000000-0000-0061-0000-000000000071', '00000000-0000-0061-0000-000000000021',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
