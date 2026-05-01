package db.migration;

import org.ctc.CtcManagerApplication;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Surefire-side regression guard for V6__cleanup_legacy_season_columns.sql.
 *
 * <p>Asserts via INFORMATION_SCHEMA that the destructive cleanup migration ran successfully:
 * <ul>
 *     <li>Eight legacy seasons columns are gone.</li>
 *     <li>The {@code playoff_seasons} M:N join table is gone.</li>
 *     <li>The {@code matchdays.season_id} and {@code playoffs.season_id} bridge FK columns are gone.</li>
 *     <li>JPA mapping still loads (Hibernate {@code ddl-auto=validate} agrees with the trimmed entities).</li>
 * </ul>
 *
 * <p>Class suffix is {@code Test} (Surefire) per — runs in the standard
 * {@code ./mvnw verify} gate, not the {@code -Pe2e} Failsafe profile.
 */
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
class V6MigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SeasonRepository seasonRepository;

    @Test
    void givenV6HasRun_whenQueryInformationSchema_thenSeasonsLegacyColumnsAreGone() {
        // given V6 has executed (Flyway runs all migrations on @SpringBootTest startup).
        // when we query INFORMATION_SCHEMA.COLUMNS for the seasons table.
        // then none of the 8 legacy columns are present.
        for (String col : new String[]{
                "FORMAT", "TOTAL_ROUNDS", "LEGS", "EVENT_DURATION_MINUTES",
                "START_DATE", "END_DATE", "RACE_SCORING_ID", "MATCH_SCORING_ID"}) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE UPPER(TABLE_NAME) = 'SEASONS' AND UPPER(COLUMN_NAME) = ?",
                    Integer.class, col);
            assertThat(count)
                    .as("seasons.%s must be dropped by V6", col.toLowerCase())
                    .isZero();
        }
    }

    @Test
    void givenV6HasRun_whenQueryInformationSchema_thenPlayoffSeasonsTableIsGone() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE UPPER(TABLE_NAME) = 'PLAYOFF_SEASONS'",
                Integer.class);
        assertThat(tableCount)
                .as("playoff_seasons M:N table must be dropped by V6")
                .isZero();
    }

    @Test
    void givenV6HasRun_whenQueryInformationSchema_thenBridgeFkColumnsAreGone() {
        Integer matchdaySeason = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = 'MATCHDAYS' AND UPPER(COLUMN_NAME) = 'SEASON_ID'",
                Integer.class);
        assertThat(matchdaySeason)
                .as("matchdays.season_id bridge column must be dropped by V6 (D-01 scope-extension)")
                .isZero();

        Integer playoffSeason = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE UPPER(TABLE_NAME) = 'PLAYOFFS' AND UPPER(COLUMN_NAME) = 'SEASON_ID'",
                Integer.class);
        assertThat(playoffSeason)
                .as("playoffs.season_id bridge column must be dropped by V6 (D-01 scope-extension)")
                .isZero();
    }

    @Test
    void givenV6HasRun_whenLoadAllSeasons_thenJpaMappingStillWorks() {
        // Hibernate ddl-auto=validate (active in `dev` profile) agrees with the trimmed Season
        // entity post-V6. seasonRepository.findAll() exercises the schema-vs-entity match.
        assertThat(seasonRepository.findAll())
                .as("Trimmed Season entity must still load against post-V6 schema")
                .isNotNull();
    }
}
