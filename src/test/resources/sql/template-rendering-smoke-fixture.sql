-- PLAT-06 fixture: minimal-complete deterministic-UUID seed for TemplateRenderingSmokeIT.
-- One row per operative entity so that every /admin/** GET route discovered by
-- RequestMappingHandlerMapping has at least one resolvable target.
-- UUID range 00000000-0000-0071-0000-* (phase 71 traceability).
-- Names prefixed with "Test-Smoke-" / "T-SMOKE-" per CLAUDE.md test-data isolation.
-- Coexists with DevDataSeeder rows (different UUID range) AND with Phase 61 fixture
-- (different sub-range) — no PK collisions.
-- Column lists pre-resolved against src/main/resources/db/migration/V1__initial_schema.sql
-- and V3__add_season_phase_tables.sql per plan-checker WARNING W4 (2026-05-11).

-- Key UUID constants (mirrored as static finals in TemplateRenderingSmokeIT):
--   RACE_SCORING_SMOKE_ID      = 00000000-0000-0071-0000-000000000001
--   MATCH_SCORING_SMOKE_ID     = 00000000-0000-0071-0000-000000000002
--   SEASON_SMOKE_ID            = 00000000-0000-0071-0000-000000000010
--   PHASE_SMOKE_ID             = 00000000-0000-0071-0000-000000000011
--   GROUP_SMOKE_ID             = 00000000-0000-0071-0000-000000000012
--   TEAM_A_SMOKE_ID            = 00000000-0000-0071-0000-000000000020
--   TEAM_B_SMOKE_ID            = 00000000-0000-0071-0000-000000000021
--   SEASON_TEAM_A_SMOKE_ID     = 00000000-0000-0071-0000-000000000030
--   SEASON_TEAM_B_SMOKE_ID     = 00000000-0000-0071-0000-000000000031
--   PHASE_TEAM_A_SMOKE_ID      = 00000000-0000-0071-0000-000000000040
--   PHASE_TEAM_B_SMOKE_ID      = 00000000-0000-0071-0000-000000000041
--   MATCHDAY_SMOKE_ID          = 00000000-0000-0071-0000-000000000050
--   MATCH_SMOKE_ID             = 00000000-0000-0071-0000-000000000055
--   RACE_SMOKE_ID              = 00000000-0000-0071-0000-000000000060
--   RACE_LINEUP_SMOKE_ID       = 00000000-0000-0071-0000-000000000065
--   DRIVER_A_SMOKE_ID          = 00000000-0000-0071-0000-000000000070
--   DRIVER_B_SMOKE_ID          = 00000000-0000-0071-0000-000000000071
--   SEASON_DRIVER_A_SMOKE_ID   = 00000000-0000-0071-0000-000000000080
--   SEASON_DRIVER_B_SMOKE_ID   = 00000000-0000-0071-0000-000000000081
--   RACE_RESULT_SMOKE_ID       = 00000000-0000-0071-0000-000000000090

-- Reference data: scoring rows (V1 race_scorings + match_scorings).
INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000001', 'Phase71-Smoke-RaceScoring',
        '25,18,15,12,10,8,6,4,2,1', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000002', 'Phase71-Smoke-MatchScoring', 3, 1, 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Season (V1 seasons: identity-only columns post-V6).
INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000010', 'Test-Smoke-Season-2071', 2071, 1, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- REGULAR phase carrying scoring + legs + format (V3 season_phases canonical shape).
INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000011',
        '00000000-0000-0071-0000-000000000010',
        0, 'REGULAR', 'LEAGUE', 'LEAGUE', 2,
        '00000000-0000-0071-0000-000000000001',
        '00000000-0000-0071-0000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- One group on the REGULAR phase (V3 season_phase_groups). Provided so that
-- /admin/seasons/{seasonId}/phases/{phaseId}/groups/{groupId} routes have a target.
INSERT INTO season_phase_groups (id, phase_id, name, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000012',
        '00000000-0000-0071-0000-000000000011',
        'Test-Smoke-Group-A', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Two teams (V1 teams).
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000020', 'Test Smoke Team A', 'T-SMOKE-A',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000021', 'Test Smoke Team B', 'T-SMOKE-B',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- season_teams (V1 season_teams).
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000030', '00000000-0000-0071-0000-000000000010',
        '00000000-0000-0071-0000-000000000020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000031', '00000000-0000-0071-0000-000000000010',
        '00000000-0000-0071-0000-000000000021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- phase_teams (V3 phase_teams — group_id assigned to the seeded group for completeness).
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000040', '00000000-0000-0071-0000-000000000011',
        '00000000-0000-0071-0000-000000000020', '00000000-0000-0071-0000-000000000012',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000041', '00000000-0000-0071-0000-000000000011',
        '00000000-0000-0071-0000-000000000021', '00000000-0000-0071-0000-000000000012',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Drivers (V1 drivers — nickname NOT NULL per V1 contract).
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000070', 'T_smoke_drv01', 'Test Smoke Driver A', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000071', 'T_smoke_drv02', 'Test Smoke Driver B', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- season_drivers (V1 season_drivers).
INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000080', '00000000-0000-0071-0000-000000000010',
        '00000000-0000-0071-0000-000000000070', '00000000-0000-0071-0000-000000000020',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000081', '00000000-0000-0071-0000-000000000010',
        '00000000-0000-0071-0000-000000000071', '00000000-0000-0071-0000-000000000021',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Matchday on the REGULAR phase (post-V6 matchdays: no season_id column; group_id NULLABLE).
INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000050', '00000000-0000-0071-0000-000000000011',
        '00000000-0000-0071-0000-000000000012', 'Matchday 1', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Match on the matchday (V1 matches: home_team_id NOT NULL; away_team_id NULLABLE;
-- home_score + away_score NULLABLE; bye NOT NULL DEFAULT FALSE).
INSERT INTO matches (id, matchday_id, home_team_id, away_team_id, home_score, away_score, bye,
                    created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000055', '00000000-0000-0071-0000-000000000050',
        '00000000-0000-0071-0000-000000000020', '00000000-0000-0071-0000-000000000021',
        NULL, NULL, FALSE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Race linked to matchday + match (V1 races: matchday_id NOT NULL; match_id NULLABLE;
-- track/car/home/away/date_time/calendar_event_id all NULLABLE).
INSERT INTO races (id, matchday_id, match_id, home_team_id, away_team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000060', '00000000-0000-0071-0000-000000000050',
        '00000000-0000-0071-0000-000000000055',
        '00000000-0000-0071-0000-000000000020', '00000000-0000-0071-0000-000000000021',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- race_lineups (V1: race_id + driver_id + team_id all NOT NULL).
INSERT INTO race_lineups (id, race_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000065', '00000000-0000-0071-0000-000000000060',
        '00000000-0000-0071-0000-000000000070', '00000000-0000-0071-0000-000000000020',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- race_results (V1: race_id, driver_id, position, quali_position all NOT NULL;
-- fastest_lap + points_* have DEFAULTs).
INSERT INTO race_results (id, race_id, driver_id, position, quali_position, fastest_lap,
                          points_race, points_quali, points_fl, points_total,
                          created_at, updated_at)
VALUES ('00000000-0000-0071-0000-000000000090', '00000000-0000-0071-0000-000000000060',
        '00000000-0000-0071-0000-000000000070', 1, 1, FALSE, 25, 0, 0, 25,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
