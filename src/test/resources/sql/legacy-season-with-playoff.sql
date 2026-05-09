-- QUAL-03 fixture: legacy migrated season WITH playoff (post-V6 schema).
-- Same legacy-shape pattern as legacy-season-without-playoff.sql, additionally inserts a
-- PLAYOFF SeasonPhase + a Playoff row linked via phase_id (post-V6: the legacy bridge column on
-- playoffs is gone; the phase association is now the single source of truth).
-- Deterministic UUIDs in the 0000-0061-1000-* range -- defensively distinct from the
-- without-playoff fixture so even concurrent tests cannot PK-collide.

INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000001', 'Phase61-Legacy-RaceScoring-WP',
        '25,18,15,12,10,8,6,4,2,1', 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000002', 'Phase61-Legacy-MatchScoring-WP', 3, 1, 0,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Season post-V6.
INSERT INTO seasons (id, name, season_year, season_number, active, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000010', 'Test-Legacy-Season-2097', 2097, 1, false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- REGULAR phase (LEAGUE layout).
INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000011',
        '00000000-0000-0061-1000-000000000010',
        0, 'REGULAR', 'LEAGUE', 'LEAGUE', 2,
        '00000000-0000-0061-1000-000000000001',
        '00000000-0000-0061-1000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- PLAYOFF phase (BRACKET layout) -- additional vs the without-playoff fixture.
INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, legs,
                           race_scoring_id, match_scoring_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000012',
        '00000000-0000-0061-1000-000000000010',
        10, 'PLAYOFF', 'BRACKET', 'LEAGUE', 1,
        '00000000-0000-0061-1000-000000000001',
        '00000000-0000-0061-1000-000000000002',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Teams.
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000020', 'Test Legacy Team A', 'T-LEG-A',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO teams (id, name, short_name, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000021', 'Test Legacy Team B', 'T-LEG-B',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- season_teams.
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000030', '00000000-0000-0061-1000-000000000010',
        '00000000-0000-0061-1000-000000000020', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000031', '00000000-0000-0061-1000-000000000010',
        '00000000-0000-0061-1000-000000000021', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- phase_teams on REGULAR phase.
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000040', '00000000-0000-0061-1000-000000000011',
        '00000000-0000-0061-1000-000000000020', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000041', '00000000-0000-0061-1000-000000000011',
        '00000000-0000-0061-1000-000000000021', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Matchdays on REGULAR phase.
INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000050', '00000000-0000-0061-1000-000000000011',
        NULL, 'Matchday 1', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO matchdays (id, phase_id, group_id, label, sort_index, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000051', '00000000-0000-0061-1000-000000000011',
        NULL, 'Matchday 2', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO races (id, matchday_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000060', '00000000-0000-0061-1000-000000000050',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO races (id, matchday_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000061', '00000000-0000-0061-1000-000000000051',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Drivers.
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000070', 'T_legacy_wp_drv01', 'Test Legacy WP Driver A', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO drivers (id, psn_id, nickname, active, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000071', 'T_legacy_wp_drv02', 'Test Legacy WP Driver B', true,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000080', '00000000-0000-0061-1000-000000000010',
        '00000000-0000-0061-1000-000000000070', '00000000-0000-0061-1000-000000000020',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO season_drivers (id, season_id, driver_id, team_id, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000081', '00000000-0000-0061-1000-000000000010',
        '00000000-0000-0061-1000-000000000071', '00000000-0000-0061-1000-000000000021',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Playoff (post-V6 schema): playoffs (id, phase_id, name, start_date, end_date,
-- event_duration_minutes, created_at, updated_at). NO season_id column anymore.
INSERT INTO playoffs (id, phase_id, name, created_at, updated_at)
VALUES ('00000000-0000-0061-1000-000000000090',
        '00000000-0000-0061-1000-000000000012',
        'Test-Legacy-Playoff',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
