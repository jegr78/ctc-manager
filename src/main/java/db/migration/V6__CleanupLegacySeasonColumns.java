package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V6 Flyway Java migration: drops all legacy season-level fields and bridge tables.
 *
 * <p>Phase 61 MIGR-06 + D-01 scope-extension. After Phase 56-60 the canonical model is
 * Season → SeasonPhase → Matchday/Playoff; the columns/tables dropped here are denormalized
 * residue from the pre-v1.9 flat model.
 *
 * <p>Order:
 * <ol>
 *     <li>M:N table {@code playoff_seasons}</li>
 *     <li>Named FK / UK constraints (must precede column drop on MariaDB — H2 ignores)</li>
 *     <li>Bridge FK columns (matchdays.season_id, playoffs.season_id) — indexes auto-dropped
 *         with their column on both engines</li>
 *     <li>Seasons legacy columns (8 fields)</li>
 * </ol>
 *
 * <p>Originally shipped as V6__cleanup_legacy_season_columns.sql; the {@code DROP INDEX IF EXISTS}
 * statements were standalone (H2 syntax — MariaDB requires {@code DROP INDEX name ON table}).
 * Converted to a dialect-aware Java migration (following V4/V5 pattern) so the same migration
 * runs cleanly against H2 (dev/test) and MariaDB (local/docker/prod). Surfaced by Phase 61
 * UAT Test 3 — V6 migration on MariaDB docker profile.
 *
 * <p>IRREVERSIBLE — ops must take a backup before applying to prod (Tracked Behavior Change).
 */
public class V6__CleanupLegacySeasonColumns extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V6__CleanupLegacySeasonColumns.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String dialect = connection.getMetaData().getDatabaseProductName();
        log.info("V6 migration starting on dialect: {}", dialect);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS playoff_seasons");

            // Drop named FK/UK constraints explicitly before dropping the columns they reference.
            // MariaDB 10.5+ supports DROP CONSTRAINT IF EXISTS for FK and UK; H2 ignores unknown
            // constraint names gracefully under IF EXISTS.
            stmt.execute("ALTER TABLE matchdays DROP CONSTRAINT IF EXISTS fk_md_season");
            stmt.execute("ALTER TABLE playoffs DROP CONSTRAINT IF EXISTS fk_playoff_season");
            stmt.execute("ALTER TABLE playoffs DROP CONSTRAINT IF EXISTS uk_playoff_season");

            // Bridge FK columns. The named FK indexes from V2 (idx_matchdays_season_id /
            // idx_playoffs_season_id) reference only this column, so both H2 and MariaDB
            // auto-drop the index when the column is dropped — no explicit DROP INDEX needed
            // (and explicit DROP INDEX needs different syntax across dialects).
            stmt.execute("ALTER TABLE matchdays DROP COLUMN season_id");
            stmt.execute("ALTER TABLE playoffs DROP COLUMN season_id");

            // Named scoring FKs on seasons. V5 made the columns nullable; here we drop the
            // named constraint before the underlying column drop (MariaDB requirement).
            stmt.execute("ALTER TABLE seasons DROP CONSTRAINT IF EXISTS fk_season_race_scoring");
            stmt.execute("ALTER TABLE seasons DROP CONSTRAINT IF EXISTS fk_season_match_scoring");

            stmt.execute("ALTER TABLE seasons DROP COLUMN format");
            stmt.execute("ALTER TABLE seasons DROP COLUMN total_rounds");
            stmt.execute("ALTER TABLE seasons DROP COLUMN legs");
            stmt.execute("ALTER TABLE seasons DROP COLUMN event_duration_minutes");
            stmt.execute("ALTER TABLE seasons DROP COLUMN start_date");
            stmt.execute("ALTER TABLE seasons DROP COLUMN end_date");
            stmt.execute("ALTER TABLE seasons DROP COLUMN race_scoring_id");
            stmt.execute("ALTER TABLE seasons DROP COLUMN match_scoring_id");
        }

        log.info("V6 migration complete on dialect: {}", dialect);
    }
}
