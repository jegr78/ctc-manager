package db.migration;

import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Surefire-side regression guard for V5__NullableLegacyScoringColumns (Java migration).
 *
 * <p>Asserts via INFORMATION_SCHEMA that the dialect-aware migration ran successfully:
 * the {@code race_scoring_id} and {@code match_scoring_id} FK columns on
 * {@code season_phases} are NULLABLE after Flyway has executed V1→V6 (V6 drops the
 * matching columns from the {@code seasons} table, so only {@code season_phases}
 * carries them post-migration).
 *
 * <p>Replaces the broken V5 SQL migration whose {@code ALTER COLUMN ... DROP NOT NULL}
 * syntax was PostgreSQL/H2-only and raised MariaDB error 1064 (uncovered by Phase 61
 * UAT Test 3 — V6 migration on MariaDB docker profile).
 */
@CtcDevSpringBootContext
class V5MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void givenV5HasRun_whenQueryInformationSchema_thenSeasonPhasesScoringColumnsAreNullable() {
        // given V5 (Java migration) has executed (Flyway runs all migrations on @SpringBootTest startup).
        // when we query INFORMATION_SCHEMA.COLUMNS for the season_phases table.
        // then race_scoring_id and match_scoring_id must report IS_NULLABLE = 'YES'.
        for (String col : new String[]{"RACE_SCORING_ID", "MATCH_SCORING_ID"}) {
            String isNullable = jdbcTemplate.queryForObject(
                    "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE UPPER(TABLE_NAME) = 'SEASON_PHASES' AND UPPER(COLUMN_NAME) = ?",
                    String.class, col);
            assertThat(isNullable)
                    .as("season_phases.%s must be nullable after V5 ran", col.toLowerCase())
                    .isEqualTo("YES");
        }
    }
}
