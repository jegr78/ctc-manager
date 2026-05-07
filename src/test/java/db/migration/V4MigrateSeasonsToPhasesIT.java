package db.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD-RED integration test for V4__MigrateSeasonsToPhases.
 *
 * Uses a programmatic Flyway harness (NOT @SpringBootTest) to exercise
 * the V4 migration against an isolated H2 in-memory database.
 * Test sequence per D-15:
 *   1. Build isolated H2 via EmbeddedDatabaseBuilder
 *   2. Run V1+V2+V3 only (target=3)
 *   3. Seed legacy data (pre-V4 state per D-17)
 *   4. Run V4 (target=4)
 *   5. Assert on resulting state
 *
 * This class is in the RED state: all six tests FAIL because
 * V4__MigrateSeasonsToPhases.java does not yet exist (Plan 02 will add it).
 *
 * Test data uses the "Phase57-" prefix per CLAUDE.md "Isolate Test Data Completely".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V4MigrateSeasonsToPhasesIT {

    // Deterministic UUID constants — easier to debug specific row failures.
    private static final UUID RACE_SCORING_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MATCH_SCORING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SEASON_1_ID      = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID SEASON_2_ID      = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID SEASON_3_ID      = UUID.fromString("00000000-0000-0000-0000-000000000030"); // empty season
    private static final UUID TEAM_S1_A        = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TEAM_S1_B        = UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID TEAM_S2_A        = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID TEAM_S2_B        = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID PLAYOFF_1_ID     = UUID.fromString("00000000-0000-0000-0000-000000000013");
    private static final UUID MD_S1_1          = UUID.fromString("00000000-0000-0000-0000-000000000014");
    private static final UUID MD_S1_2          = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private static final UUID MD_S2_1          = UUID.fromString("00000000-0000-0000-0000-000000000023");
    private static final UUID MD_S2_2          = UUID.fromString("00000000-0000-0000-0000-000000000024");

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    void setUp() {
        // Step 1: Build isolated H2 in-memory DB
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);

        // Step 2: Run V1+V2+V3 only — stop before V4
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("3")
                .load()
                .migrate();

        // Step 3: Seed legacy data (pre-V4 state per D-17)
        seedLegacyData();

        // Step 4: Run V4 (the class under test — does NOT exist yet in Plan 01)
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();
    }

    // Test methods (D-16 locked method names — do NOT rename or abbreviate)

    @Test
    void givenLegacySeasons_whenMigrationRuns_thenEachSeasonHasOneRegularPhase() {
        // given — 3 seasons seeded; V4 already ran in @BeforeAll
        // when — migration completed

        // then: exactly 3 REGULAR phases created (one per season)
        Integer regularPhaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM season_phases WHERE phase_type = 'REGULAR'", Integer.class);
        assertThat(regularPhaseCount).isEqualTo(3);

        // verify field copy: SEASON_1's REGULAR phase has format=LEAGUE, total_rounds=10, legs=2, event_duration_minutes=90
        Map<String, Object> phase1 = jdbcTemplate.queryForMap(
                "SELECT * FROM season_phases WHERE season_id = ? AND phase_type = 'REGULAR'", SEASON_1_ID);
        assertThat(phase1.get("format")).isEqualTo("LEAGUE");
        assertThat(phase1.get("layout")).isEqualTo("LEAGUE");
        assertThat(phase1.get("sort_index")).isEqualTo(0);
        assertThat(phase1.get("total_rounds")).isEqualTo(10);
        assertThat(phase1.get("legs")).isEqualTo(2);
        assertThat(phase1.get("event_duration_minutes")).isEqualTo(90);
        assertThat(phase1.get("label")).isNull(); // D-06: label = null for REGULAR
        // race_scoring_id + match_scoring_id copied 1:1
        assertThat(toUUID(phase1.get("race_scoring_id"))).isEqualTo(RACE_SCORING_ID);
        assertThat(toUUID(phase1.get("match_scoring_id"))).isEqualTo(MATCH_SCORING_ID);
    }

    @Test
    void givenLegacyPlayoff_whenMigrationRuns_thenPlayoffLinkedViaPhaseId() {
        // given — 1 playoff on SEASON_1_ID seeded; V4 already ran in @BeforeAll
        // when — migration completed

        // then: exactly 1 PLAYOFF phase created
        Integer playoffPhaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM season_phases WHERE phase_type = 'PLAYOFF'", Integer.class);
        assertThat(playoffPhaseCount).isEqualTo(1);

        // playoffs.phase_id must be NOT NULL and point to the PLAYOFF season_phases row
        Map<String, Object> playoffRow = jdbcTemplate.queryForMap(
                "SELECT p.phase_id, sp.phase_type, sp.sort_index, sp.layout, sp.format, "
                + "sp.legs, sp.label, sp.season_id "
                + "FROM playoffs p "
                + "JOIN season_phases sp ON p.phase_id = sp.id "
                + "WHERE p.id = ?",
                PLAYOFF_1_ID);

        assertThat(playoffRow.get("phase_id")).isNotNull();
        assertThat(playoffRow.get("phase_type")).isEqualTo("PLAYOFF");
        assertThat(playoffRow.get("sort_index")).isEqualTo(10);
        assertThat(playoffRow.get("layout")).isEqualTo("BRACKET");
        assertThat(playoffRow.get("format")).isEqualTo("LEAGUE"); // D-08: explicit value 'LEAGUE' per V4 migration (not DB DEFAULT)
        assertThat(playoffRow.get("legs")).isEqualTo(1);
        assertThat(playoffRow.get("label")).isEqualTo("Phase57-Test-Playoff-1"); // carried from playoff.name per D-08
        assertThat(toUUID(playoffRow.get("season_id"))).isEqualTo(SEASON_1_ID);
    }

    @Test
    void givenLegacyMatchdays_whenMigrationRuns_thenAllMatchdaysHavePhaseId() {
        // given — 4 matchdays seeded (2 per non-empty season); V4 already ran in @BeforeAll
        // when — migration completed

        // then: no matchday has NULL phase_id
        Integer nullPhaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matchdays WHERE phase_id IS NULL", Integer.class);
        assertThat(nullPhaseCount).isEqualTo(0);

        // total matchday count unchanged
        Integer totalMatchdays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matchdays", Integer.class);
        assertThat(totalMatchdays).isEqualTo(4);

        // each matchday's phase_id points to the REGULAR phase of its own season
        Integer correctlyLinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matchdays m "
                + "JOIN season_phases sp ON m.phase_id = sp.id "
                + "WHERE sp.season_id = m.season_id AND sp.phase_type = 'REGULAR'",
                Integer.class);
        assertThat(correctlyLinked).isEqualTo(4);
    }

    @Test
    void givenLegacySeasonTeams_whenMigrationRuns_thenPhaseTeamsPopulated() {
        // given — 4 season_teams seeded (2 per non-empty season); V4 already ran in @BeforeAll
        // when — migration completed

        // then: exactly 4 phase_teams created (one per season_team)
        Integer phaseTeamCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phase_teams", Integer.class);
        assertThat(phaseTeamCount).isEqualTo(4);

        // LEAGUE layout: all group_id values are NULL
        Integer withGroupId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phase_teams WHERE group_id IS NOT NULL", Integer.class);
        assertThat(withGroupId).isEqualTo(0);

        // each phase_teams row's phase_id points to a REGULAR season_phase whose season contains the team
        Integer correctlyLinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM phase_teams pt "
                + "JOIN season_phases sp ON pt.phase_id = sp.id "
                + "JOIN season_teams st ON st.team_id = pt.team_id AND st.season_id = sp.season_id "
                + "WHERE sp.phase_type = 'REGULAR'",
                Integer.class);
        assertThat(correctlyLinked).isEqualTo(4);
    }

    @Test
    void givenLegacyData_whenMigrationRuns_thenBridgeColumnsRemainIntact() {
        // given — all legacy bridge columns seeded; V4 already ran in @BeforeAll
        // when — migration completed

        // then: bridge column matchdays.season_id still intact for all matchdays
        Integer matchdaysWithSeasonId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM matchdays WHERE season_id IS NOT NULL", Integer.class);
        assertThat(matchdaysWithSeasonId).isEqualTo(4);

        // bridge column playoffs.season_id still intact
        Integer playoffsWithSeasonId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM playoffs WHERE season_id IS NOT NULL", Integer.class);
        assertThat(playoffsWithSeasonId).isEqualTo(1);

        // playoff_seasons M:N table still exists (SELECT should not throw)
        Integer playoffSeasonsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM playoff_seasons", Integer.class);
        assertThat(playoffSeasonsCount).isGreaterThanOrEqualTo(0);

        // SEASON_1's legacy columns are still populated (bridge columns preserved per D-01 Phase 56 and ROADMAP-SC5)
        Map<String, Object> season1 = jdbcTemplate.queryForMap(
                "SELECT format, total_rounds, legs FROM seasons WHERE id = ?", SEASON_1_ID);
        assertThat(season1.get("format")).isEqualTo("LEAGUE");
        assertThat(season1.get("total_rounds")).isEqualTo(10);
        assertThat(season1.get("legs")).isEqualTo(2);
    }

    @Test
    void givenMigratedSchema_whenMatchdayInsertedWithoutPhaseId_thenViolatesNotNullConstraint() {
        // when / then: after V4 NOT-NULL flip, inserting a matchday without phase_id must fail
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO matchdays (id, season_id, label, sort_index, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        UUID.randomUUID(), SEASON_1_ID, "Phase57-NoPhase", 99))
                .isInstanceOfAny(DataIntegrityViolationException.class,
                        org.springframework.jdbc.UncategorizedSQLException.class);
    }

    // Seed helper (pre-V4 state per D-17)

    private void seedLegacyData() {
        // Insert 1 race_scoring + 1 match_scoring (FK constraints on seasons)
        jdbcTemplate.update(
                "INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                RACE_SCORING_ID, "Phase57-Test-RaceScoring", "25,18,15,12,10,8,6,4,2,1", 0);

        jdbcTemplate.update(
                "INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MATCH_SCORING_ID, "Phase57-Test-MatchScoring", 3, 1, 0);

        // Insert 4 teams (2 per non-empty season)
        jdbcTemplate.update(
                "INSERT INTO teams (id, name, short_name, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                TEAM_S1_A, "Phase57-Team-S1A", "P57S1A");
        jdbcTemplate.update(
                "INSERT INTO teams (id, name, short_name, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                TEAM_S1_B, "Phase57-Team-S1B", "P57S1B");
        jdbcTemplate.update(
                "INSERT INTO teams (id, name, short_name, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                TEAM_S2_A, "Phase57-Team-S2A", "P57S2A");
        jdbcTemplate.update(
                "INSERT INTO teams (id, name, short_name, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                TEAM_S2_B, "Phase57-Team-S2B", "P57S2B");

        // Insert Season 1: full (with playoff, 2 teams, 2 matchdays)
        jdbcTemplate.update(
                "INSERT INTO seasons (id, name, season_year, season_number, format, total_rounds, legs, "
                + "event_duration_minutes, start_date, end_date, active, "
                + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SEASON_1_ID, "Phase57-Test-Season-1", 2025, 1, "LEAGUE", 10, 2, 90,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30), false,
                RACE_SCORING_ID, MATCH_SCORING_ID);

        // Insert Season 2: matchdays/teams but NO playoff
        jdbcTemplate.update(
                "INSERT INTO seasons (id, name, season_year, season_number, format, legs, active, "
                + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SEASON_2_ID, "Phase57-Test-Season-2", 2025, 2, "SWISS", 1, false,
                RACE_SCORING_ID, MATCH_SCORING_ID);

        // Insert Season 3: empty (no matchdays, no teams, no playoff) — D-17 edge case
        jdbcTemplate.update(
                "INSERT INTO seasons (id, name, season_year, season_number, format, legs, active, "
                + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SEASON_3_ID, "Phase57-Test-Season-3-Empty", 2026, 1, "ROUND_ROBIN", 1, false,
                RACE_SCORING_ID, MATCH_SCORING_ID);

        // Insert 4 season_teams (2 per non-empty season); Season 3 has ZERO season_teams
        jdbcTemplate.update(
                "INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SEASON_1_ID, TEAM_S1_A);
        jdbcTemplate.update(
                "INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SEASON_1_ID, TEAM_S1_B);
        jdbcTemplate.update(
                "INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SEASON_2_ID, TEAM_S2_A);
        jdbcTemplate.update(
                "INSERT INTO season_teams (id, season_id, team_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), SEASON_2_ID, TEAM_S2_B);

        // Insert 4 matchdays (2 per non-empty season); phase_id and group_id LEFT NULL (V3 nullable)
        jdbcTemplate.update(
                "INSERT INTO matchdays (id, season_id, label, sort_index, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MD_S1_1, SEASON_1_ID, "Phase57-MD-S1-1", 0);
        jdbcTemplate.update(
                "INSERT INTO matchdays (id, season_id, label, sort_index, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MD_S1_2, SEASON_1_ID, "Phase57-MD-S1-2", 1);
        jdbcTemplate.update(
                "INSERT INTO matchdays (id, season_id, label, sort_index, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MD_S2_1, SEASON_2_ID, "Phase57-MD-S2-1", 0);
        jdbcTemplate.update(
                "INSERT INTO matchdays (id, season_id, label, sort_index, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                MD_S2_2, SEASON_2_ID, "Phase57-MD-S2-2", 1);

        // Insert 1 playoff on SEASON_1_ID; SEASON_2_ID gets NO playoff (D-17 edge case)
        jdbcTemplate.update(
                "INSERT INTO playoffs (id, season_id, name, start_date, end_date, event_duration_minutes, "
                + "created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                PLAYOFF_1_ID, SEASON_1_ID, "Phase57-Test-Playoff-1",
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 8, 31), 60);
    }

    // UUID helper — defensive conversion for queryForMap results
    // H2 returns java.util.UUID; MariaDB may return byte[] (Pitfall 1 in RESEARCH.md)

    private static UUID toUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        if (value instanceof byte[] b) {
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        if (value instanceof String s) return UUID.fromString(s);
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
    }
}
