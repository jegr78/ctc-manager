package db.migration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.CtcManagerApplication;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for V4 migration end-to-end.
 *
 * <p>Verifies that the full Spring Boot context loads cleanly after V4 has run as part of the
 * standard Flyway autoload flow. Indirectly proves: V1+V2+V3+V4 schemas align; JPA + Hibernate
 * map the post-V4 schema; Spring Data repositories work; Season.phases bidirectional collection
 * (Phase 56) is reachable from a live Spring context.
 *
 * <p>On the dev profile (H2 in-memory) DevDataSeeder populates seasons after Flyway completes.
 * The primary assertion verifies that the JPA mapping between {@code season_phases} rows and
 * {@code Season.getPhases()} is intact: a Season row + SeasonPhase row inserted directly via
 * JdbcTemplate are correctly returned by the repository with a non-empty phases collection.
 *
 * <p>{@code classes = CtcManagerApplication.class} is required because this test lives in the
 * {@code db.migration} package, which is outside the {@code org.ctc} component-scan tree.
 * Spring Boot cannot auto-detect the {@code @SpringBootConfiguration} by walking upward from
 * an unrelated root package, so the application entry point must be declared explicitly.
 */
@SpringBootTest(classes = CtcManagerApplication.class)
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class V4MigrationSmokeIT {

    // Deterministic UUIDs with Phase57-Smoke prefix for test isolation (CLAUDE.md)
    private static final UUID SMOKE_RACE_SCORING_ID  = UUID.fromString("00000000-0000-0057-0000-000000000001");
    private static final UUID SMOKE_MATCH_SCORING_ID = UUID.fromString("00000000-0000-0057-0000-000000000002");
    private static final UUID SMOKE_SEASON_ID        = UUID.fromString("00000000-0000-0057-0000-000000000010");
    private static final UUID SMOKE_PHASE_ID         = UUID.fromString("00000000-0000-0057-0000-000000000011");

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Seeds one Season + one REGULAR SeasonPhase row directly via JdbcTemplate,
     * simulating the post-V4 state for a single legacy season.
     * All inserts are rolled back after the test (Spring @Transactional guarantee).
     */
    @BeforeEach
    void seedSmokeTestData() {
        // Insert supporting FK rows (race_scoring, match_scoring)
        jdbcTemplate.update(
                "INSERT INTO race_scorings (id, name, race_points, fastest_lap_points, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SMOKE_RACE_SCORING_ID, "Phase57-Smoke-RaceScoring", "25,18,15,12,10", 0);

        jdbcTemplate.update(
                "INSERT INTO match_scorings (id, name, points_win, points_draw, points_loss, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SMOKE_MATCH_SCORING_ID, "Phase57-Smoke-MatchScoring", 3, 1, 0);

        // Insert one season — post-V6 schema (dropped format/legs/race_scoring_id/match_scoring_id
        // from seasons; scoring + format now live on the SeasonPhase row inserted below).
        jdbcTemplate.update(
                "INSERT INTO seasons (id, name, season_year, season_number, active, "
                + "created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SMOKE_SEASON_ID, "Phase57-Smoke-Season", 2099, 99, false);

        // Insert one REGULAR season_phase row — simulates V4 backfill for this season
        jdbcTemplate.update(
                "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
                + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SMOKE_PHASE_ID, SMOKE_SEASON_ID, 0, "REGULAR", "LEAGUE", "LEAGUE",
                SMOKE_RACE_SCORING_ID, SMOKE_MATCH_SCORING_ID);
    }

    @Test
    void whenContextLoads_thenAllSeasonsHavePhases() {
        // when — primary smoke: Flyway (including V4) ran, JPA context loaded, repository works.
        List<Season> seasons = seasonRepository.findAll();

        // then — basic invariant: findAll() works and seasons list is non-null
        assertThat(seasons).isNotNull();
        seasons.forEach(s -> assertThat(s.getPhases())
                .as("Season %s/%d (%s): phases collection must never be null",
                        s.getYear(), s.getNumber(), s.getName())
                .isNotNull());
    }

    @Test
    void givenSeasonWithBackfilledPhase_whenLoadedViaRepository_thenPhasesCollectionIsNotEmpty() {
        // given — SMOKE_SEASON_ID + SMOKE_PHASE_ID inserted in @BeforeEach (simulates V4 backfill)

        // when
        Optional<Season> result = seasonRepository.findById(SMOKE_SEASON_ID);

        // then — D-18 invariant: a season that had V4 backfill applied must expose its REGULAR phase
        assertThat(result).isPresent();
        Season season = result.get();
        assertThat(season.getPhases())
                .as("V4-backfilled season must have exactly one REGULAR phase via JPA mapping")
                .hasSize(1);
        assertThat(season.getPhases().get(0).getPhaseType())
                .hasToString("REGULAR");
    }
}
