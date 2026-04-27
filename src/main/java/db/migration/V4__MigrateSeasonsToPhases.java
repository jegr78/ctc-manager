package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V4 Flyway Java migration: backfills all existing production data into the Phase-schema
 * introduced in Phase 56 (V3__add_season_phase_tables.sql).
 *
 * <p>Steps (in fixed order per D-02 / D-13):
 * <ol>
 *   <li>migrateRegularPhases — one REGULAR season_phase per Season (fields copied 1:1 from Season)</li>
 *   <li>migratePlayoffPhases — one PLAYOFF season_phase per Playoff; playoffs.phase_id updated</li>
 *   <li>migrateMatchdayFKs — matchdays.phase_id set via single correlated UPDATE</li>
 *   <li>migratePhaseTeams — phase_teams derived 1:1 from season_teams</li>
 *   <li>flipNotNullConstraints — matchdays.phase_id + playoffs.phase_id flipped to NOT NULL (LAST)</li>
 * </ol>
 *
 * <p>Runs in a single Flyway-managed transaction (canExecuteInTransaction() = true, D-04).
 * Safe no-op on empty databases (dev/test H2 with no seed data).
 */
public class V4__MigrateSeasonsToPhases extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V4__MigrateSeasonsToPhases.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        String dialect = connection.getMetaData().getDatabaseProductName();
        log.info("V4 migration starting on dialect: {}", dialect);

        Map<UUID, UUID> seasonToRegularPhaseId = migrateRegularPhases(jdbcTemplate);
        migratePlayoffPhases(jdbcTemplate);
        migrateMatchdayFKs(jdbcTemplate);
        migratePhaseTeams(jdbcTemplate, seasonToRegularPhaseId);
        flipNotNullConstraints(jdbcTemplate, dialect);

        log.info("V4 migration complete on dialect: {}", dialect);
    }

    /**
     * Creates one REGULAR season_phase per Season (D-06).
     * Fields copied 1:1 from the seasons table.
     *
     * @return map from season_id to newly created REGULAR phase_id (used in step 4)
     */
    private Map<UUID, UUID> migrateRegularPhases(JdbcTemplate jdbcTemplate) {
        List<Map<String, Object>> seasons = jdbcTemplate.queryForList("SELECT * FROM seasons");
        Map<UUID, UUID> seasonToRegularPhaseId = new HashMap<>();

        for (Map<String, Object> season : seasons) {
            UUID seasonId = toUUID(season.get("id"));
            UUID raceScoringId = toUUID(season.get("race_scoring_id"));
            UUID matchScoringId = toUUID(season.get("match_scoring_id"));

            // D-05: fail-fast on null scoring — migration aborts, application fails to start
            if (raceScoringId == null) {
                throw new FlywayException("Season " + seasonId + " has null race_scoring_id");
            }
            if (matchScoringId == null) {
                throw new FlywayException("Season " + seasonId + " has null match_scoring_id");
            }

            UUID newPhaseId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
                    + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
                    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    newPhaseId, seasonId, 0, "REGULAR", "LEAGUE",
                    season.get("format"), null,
                    season.get("start_date"), season.get("end_date"),
                    season.get("total_rounds"), season.get("legs"),
                    season.get("event_duration_minutes"),
                    raceScoringId, matchScoringId
            );
            seasonToRegularPhaseId.put(seasonId, newPhaseId);
        }

        log.info("Migrated {} REGULAR phases (one per season)", seasons.size());
        return seasonToRegularPhaseId;
    }

    /**
     * Creates one PLAYOFF season_phase per Playoff (D-07, D-08, D-09).
     * Scoring is inherited from playoff.season; playoffs.phase_id is updated immediately.
     * Bridge columns (playoffs.season_id, playoff_seasons M:N) are NOT touched (ROADMAP-SC5).
     */
    private void migratePlayoffPhases(JdbcTemplate jdbcTemplate) {
        List<Map<String, Object>> playoffs = jdbcTemplate.queryForList(
                "SELECT p.*, s.race_scoring_id AS s_race_scoring_id, s.match_scoring_id AS s_match_scoring_id "
                + "FROM playoffs p JOIN seasons s ON p.season_id = s.id");

        for (Map<String, Object> playoff : playoffs) {
            UUID playoffId = toUUID(playoff.get("id"));
            UUID seasonId = toUUID(playoff.get("season_id"));
            UUID raceScoringId = toUUID(playoff.get("s_race_scoring_id"));
            UUID matchScoringId = toUUID(playoff.get("s_match_scoring_id"));

            // D-05: fail-fast if a PLAYOFF phase for this season already exists
            // (guards against uk_season_phase_type collision and partial-run idempotency issues)
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM season_phases WHERE season_id = ? AND phase_type = 'PLAYOFF'",
                    Integer.class, seasonId);
            if (existing != null && existing > 0) {
                throw new FlywayException(
                        "PLAYOFF phase for season_id " + seasonId
                        + " already exists (offending playoff id=" + playoffId
                        + ") — possible partial re-run or duplicate playoff data");
            }

            UUID newPlayoffPhaseId = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO season_phases (id, season_id, sort_index, phase_type, layout, format, "
                    + "label, start_date, end_date, total_rounds, legs, event_duration_minutes, "
                    + "race_scoring_id, match_scoring_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    newPlayoffPhaseId, seasonId, 10, "PLAYOFF", "BRACKET", "LEAGUE",
                    playoff.get("name"),
                    playoff.get("start_date"), playoff.get("end_date"),
                    null, 1,
                    playoff.get("event_duration_minutes"),
                    raceScoringId, matchScoringId
            );

            // D-09: update playoffs.phase_id to link this playoff to its new PLAYOFF phase
            jdbcTemplate.update("UPDATE playoffs SET phase_id = ? WHERE id = ?", newPlayoffPhaseId, playoffId);
        }

        log.info("Migrated {} PLAYOFF phases", playoffs.size());
    }

    /**
     * Sets matchdays.phase_id via a single correlated UPDATE (D-10).
     * Each matchday is linked to the REGULAR phase of its own season.
     * matchdays.group_id stays NULL; matchdays.season_id is NOT touched (ROADMAP-SC5).
     */
    private void migrateMatchdayFKs(JdbcTemplate jdbcTemplate) {
        int count = jdbcTemplate.update(
                "UPDATE matchdays m SET phase_id = ("
                + "  SELECT sp.id FROM season_phases sp"
                + "  WHERE sp.season_id = m.season_id AND sp.phase_type = 'REGULAR'"
                + ")"
        );
        log.info("Updated phase_id on {} matchdays", count);
    }

    /**
     * Derives phase_teams 1:1 from season_teams (D-11).
     * Each phase_team points to the REGULAR phase of the corresponding season.
     * group_id stays NULL (LEAGUE layout — no groups in legacy data).
     */
    private void migratePhaseTeams(JdbcTemplate jdbcTemplate, Map<UUID, UUID> seasonToRegularPhaseId) {
        List<Map<String, Object>> seasonTeams = jdbcTemplate.queryForList("SELECT * FROM season_teams");
        int insertCount = 0;

        for (Map<String, Object> st : seasonTeams) {
            UUID seasonId = toUUID(st.get("season_id"));
            UUID teamId = toUUID(st.get("team_id"));
            UUID phaseId = seasonToRegularPhaseId.get(seasonId);

            // D-05: fail-fast on orphan season_teams row — no silent data loss
            if (phaseId == null) {
                throw new FlywayException(
                        "season_teams row references unknown season_id " + seasonId
                        + " — no REGULAR phase exists for this season (orphan FK)");
            }

            jdbcTemplate.update(
                    "INSERT INTO phase_teams (id, phase_id, team_id, group_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), phaseId, teamId
            );
            insertCount++;
        }

        log.info("Migrated {} phase_teams entries (one per season_team)", insertCount);
    }

    /**
     * Flips matchdays.phase_id and playoffs.phase_id from NULLABLE to NOT NULL (D-12, D-13).
     * Must be the LAST step — all rows must have phase_id populated before the flip (D-13).
     * Dialect detection via getDatabaseProductName() (D-12):
     *   H2: ALTER TABLE x ALTER COLUMN y SET NOT NULL
     *   MariaDB (and other): ALTER TABLE x MODIFY COLUMN y UUID NOT NULL
     *
     * <p>Guard: skips the flip when no seasons exist (empty database). On an empty dev/test H2,
     * V4 is a pure no-op; the DevDataSeeder and TestDataService (both updated in Phase 59) must
     * insert matchdays with a valid phase_id after Phase 59 lands. Until then, the nullable
     * column remains nullable on empty databases so that pre-Phase-59 seeders continue to work.
     * On a non-empty production database the flip always executes.
     */
    private void flipNotNullConstraints(JdbcTemplate jdbcTemplate, String dialect) {
        Integer seasonCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seasons", Integer.class);
        if (seasonCount == null || seasonCount == 0) {
            log.info("Skipping NOT NULL flip — no seasons found (empty database; flip deferred until Phase 59 seeder update)");
            return;
        }

        if ("H2".equals(dialect)) {
            jdbcTemplate.execute("ALTER TABLE matchdays ALTER COLUMN phase_id SET NOT NULL");
            jdbcTemplate.execute("ALTER TABLE playoffs ALTER COLUMN phase_id SET NOT NULL");
        } else {
            // MariaDB (and fail-safe fallback for any other dialect)
            jdbcTemplate.execute("ALTER TABLE matchdays MODIFY COLUMN phase_id UUID NOT NULL");
            jdbcTemplate.execute("ALTER TABLE playoffs MODIFY COLUMN phase_id UUID NOT NULL");
        }
        log.info("Flipped phase_id columns to NOT NULL on both matchdays and playoffs (dialect: {})", dialect);
    }

    /**
     * Defensive UUID conversion helper (D-14 / Pitfall 1 in RESEARCH.md).
     * H2 returns java.util.UUID; MariaDB may return byte[] or String.
     *
     * @param value the raw JDBC column value from queryForList / queryForMap
     * @return a UUID, or null if value is null
     * @throws IllegalArgumentException if the type cannot be converted
     */
    private static UUID toUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID u) return u;
        if (value instanceof byte[] b) {
            ByteBuffer bb = ByteBuffer.wrap(b);
            return new UUID(bb.getLong(), bb.getLong());
        }
        if (value instanceof String s) return UUID.fromString(s);
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to UUID");
    }
}
