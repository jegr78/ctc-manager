package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V5 Flyway Java migration: legacy scoring FK columns become nullable.
 *
 * <p>The slim Season form no longer requires {@code raceScoring}/{@code matchScoring}
 * at season-creation time; scoring is configured per-phase. The auto-bootstrapped
 * REGULAR phase starts with scoring={@code null} and the user fills it in from the
 * Phase tab. The columns themselves remain (existing data preserved).
 *
 * <p>Java migration (not SQL) because {@code ALTER COLUMN ... DROP NOT NULL} is H2-only —
 * MariaDB requires {@code MODIFY COLUMN ... NULL}. Dialect detection via
 * {@code getDatabaseProductName()}.
 */
public class V5__NullableLegacyScoringColumns extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V5__NullableLegacyScoringColumns.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String dialect = connection.getMetaData().getDatabaseProductName();
        log.info("V5 migration starting on dialect: {}", dialect);

        try (Statement stmt = connection.createStatement()) {
            if ("H2".equals(dialect)) {
                stmt.execute("ALTER TABLE seasons ALTER COLUMN race_scoring_id DROP NOT NULL");
                stmt.execute("ALTER TABLE seasons ALTER COLUMN match_scoring_id DROP NOT NULL");
                stmt.execute("ALTER TABLE season_phases ALTER COLUMN race_scoring_id DROP NOT NULL");
                stmt.execute("ALTER TABLE season_phases ALTER COLUMN match_scoring_id DROP NOT NULL");
            } else {
                // MariaDB (and fail-safe fallback for any other dialect)
                stmt.execute("ALTER TABLE seasons MODIFY COLUMN race_scoring_id UUID NULL");
                stmt.execute("ALTER TABLE seasons MODIFY COLUMN match_scoring_id UUID NULL");
                stmt.execute("ALTER TABLE season_phases MODIFY COLUMN race_scoring_id UUID NULL");
                stmt.execute("ALTER TABLE season_phases MODIFY COLUMN match_scoring_id UUID NULL");
            }
        }

        log.info("V5 migration complete on dialect: {}", dialect);
    }
}
