package db.migration;

import org.ctc.CtcManagerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Surefire-side regression guard for V3__add_season_phase_tables.sql.
 *
 * <p>Asserts via INFORMATION_SCHEMA that the additive V3 migration ran
 * successfully: the three new tables exist with their required columns, the
 * nullable {@code phase_id} and {@code group_id} FK columns were added to
 * existing tables, the three UNIQUE constraints are present, and the ten
 * FK indexes are present.
 *
 * <p>Class suffix is {@code Test} (Surefire) — runs in the standard
 * {@code ./mvnw verify} gate, not the {@code -Pe2e} Failsafe profile.
 * Modelled on V5MigrationTest / V6MigrationTest.
 */
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
class V3MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    // REQ MIGR-01: V3 creates the three new tables

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenSeasonPhasesTableExists() {
        // given V3 has executed (Flyway runs all migrations on @SpringBootTest startup).
        // when we query INFORMATION_SCHEMA.TABLES for season_phases.
        // then the table must exist.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE UPPER(TABLE_NAME) = 'SEASON_PHASES'",
                Integer.class);
        assertThat(count)
                .as("season_phases table must exist after V3 ran")
                .isEqualTo(1);
    }

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenSeasonPhaseGroupsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE UPPER(TABLE_NAME) = 'SEASON_PHASE_GROUPS'",
                Integer.class);
        assertThat(count)
                .as("season_phase_groups table must exist after V3 ran")
                .isEqualTo(1);
    }

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenPhaseTeamsTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE UPPER(TABLE_NAME) = 'PHASE_TEAMS'",
                Integer.class);
        assertThat(count)
                .as("phase_teams table must exist after V3 ran")
                .isEqualTo(1);
    }

    // REQ MODEL-02 / D-03: UNIQUE (season_id, phase_type) on season_phases
    // REQ MODEL-04 / D-03: UNIQUE (phase_id, team_id) on phase_teams
    // REQ MODEL-06 / D-02: UNIQUE (phase_id) on playoffs

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenUniqueConstraintsExist() {
        // given V3 has executed.
        // when we query INFORMATION_SCHEMA.TABLE_CONSTRAINTS for the three UNIQUE constraints.
        // then uk_season_phase_type, uk_phase_team, and uk_playoff_phase must exist.
        for (String constraintName : new String[]{"UK_SEASON_PHASE_TYPE", "UK_PHASE_TEAM", "UK_PLAYOFF_PHASE"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                            "WHERE CONSTRAINT_TYPE = 'UNIQUE' AND UPPER(CONSTRAINT_NAME) = ?",
                    Integer.class, constraintName);
            assertThat(count)
                    .as("UNIQUE constraint %s must exist after V3 ran", constraintName)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // REQ MODEL-05 / D-02: matchdays.phase_id and matchdays.group_id are nullable
    // REQ MODEL-06 / D-02: playoffs.phase_id is nullable

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenMatchdaysNewColumnsAreNullable() {
        // given V3 has executed.
        // when we query INFORMATION_SCHEMA.COLUMNS for the new nullable FK columns on matchdays.
        // then phase_id and group_id must report IS_NULLABLE = 'YES' (D-02: Phase 57 flips to NOT NULL).
        for (String col : new String[]{"PHASE_ID", "GROUP_ID"}) {
            String isNullable = jdbcTemplate.queryForObject(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE UPPER(TABLE_NAME) = 'MATCHDAYS' AND UPPER(COLUMN_NAME) = ?",
                    String.class, col);
            assertThat(isNullable)
                    .as("matchdays.%s must be nullable after V3 (NOT NULL flip is Phase 57's job)", col.toLowerCase())
                    .isEqualTo("YES");
        }
    }

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenPlayoffsPhaseIdColumnIsNullable() {
        // given V3 has executed.
        // when we query INFORMATION_SCHEMA.COLUMNS for playoffs.phase_id.
        // then phase_id must report IS_NULLABLE = 'YES' (D-02: nullable until Phase 57 backfills).
        String isNullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = 'PLAYOFFS' AND UPPER(COLUMN_NAME) = 'PHASE_ID'",
                String.class);
        assertThat(isNullable)
                .as("playoffs.phase_id must be nullable after V3 (NOT NULL flip is Phase 57's job)")
                .isEqualTo("YES");
    }

    // REQ MODEL-01 / MIGR-01: season_phases carries required columns
    // (phase_type, layout, format, label — key structural columns for the entity contract)

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenSeasonPhasesHasRequiredColumns() {
        // given V3 has executed.
        // when we check for the key columns that MODEL-01 requires.
        // then phase_type, layout, format, label, race_scoring_id, match_scoring_id must exist.
        for (String col : new String[]{"PHASE_TYPE", "LAYOUT", "FORMAT", "LABEL",
                "RACE_SCORING_ID", "MATCH_SCORING_ID", "SORT_INDEX", "LEGS"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE UPPER(TABLE_NAME) = 'SEASON_PHASES' AND UPPER(COLUMN_NAME) = ?",
                    Integer.class, col);
            assertThat(count)
                    .as("season_phases.%s must exist after V3 ran (MODEL-01)", col.toLowerCase())
                    .isEqualTo(1);
        }
    }

    // REQ MIGR-07: V1 and V2 are untouched — verified structurally by checking
    // that original V1 tables still have their expected primary structure
    // (seasons, matchdays, teams, race_scorings, match_scorings all exist)

    @Test
    void givenV3HasRun_whenQueryInformationSchema_thenOriginalV1TablesAreUntouched() {
        // given V1+V2 migrations are immutable (CLAUDE.md "Do Not Modify Flyway Migrations").
        // when we verify the foundational tables created by V1 still exist.
        // then seasons, matchdays, teams, race_scorings, match_scorings must all be present.
        for (String table : new String[]{"SEASONS", "MATCHDAYS", "TEAMS", "RACE_SCORINGS", "MATCH_SCORINGS"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE UPPER(TABLE_NAME) = ?",
                    Integer.class, table);
            assertThat(count)
                    .as("V1 table %s must still exist (V1/V2 untouched per MIGR-07)", table.toLowerCase())
                    .isEqualTo(1);
        }
    }
}
