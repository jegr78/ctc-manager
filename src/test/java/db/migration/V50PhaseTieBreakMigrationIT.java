package db.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class V50PhaseTieBreakMigrationIT {
    @Autowired DataSource dataSource;

    @Test
    void givenV50_whenReadingColumnDefaults_thenTieBreakIsOffAndMatchupsUndecided() throws SQLException {
        // when / then
        assertThat(defaultOf("SEASON_PHASES", "TIE_BREAK_RACE_WINNER").toLowerCase()).as("phase flag default").isIn("false", "0");
        assertThat(defaultOf("PLAYOFF_MATCHUPS", "DECIDED_BY_TIE_BREAK").toLowerCase()).as("matchup flag default").isIn("false", "0");
        assertThat(nullable("MATCHES", "TIE_BREAK_WINNER_ID")).as("tie-break winner is optional").isTrue();
    }

    @Test
    void givenV50_whenTieBreakWinnerReferencesUnknownTeam_thenForeignKeyViolation() {
        // when / then
        assertThatThrownBy(() -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("UPDATE matches SET tie_break_winner_id = ? WHERE id = (SELECT MIN(id) FROM matches)")) {
                statement.setObject(1, UUID.randomUUID().toString());
                if (statement.executeUpdate() == 0) throw new SQLException("no match row to update");
            }
        }).isInstanceOf(SQLException.class).hasMessageNotContaining("no match row");
    }

    private String defaultOf(String table, String column) throws SQLException {
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, null, matchCase(connection, table), matchCase(connection, column))) {
            assertThat(columns.next()).as(table + "." + column + " exists").isTrue();
            return String.valueOf(columns.getString("COLUMN_DEF"));
        }
    }

    private boolean nullable(String table, String column) throws SQLException {
        try (var connection = dataSource.getConnection();
             var columns = connection.getMetaData().getColumns(null, null, matchCase(connection, table), matchCase(connection, column))) {
            assertThat(columns.next()).as(table + "." + column + " exists").isTrue();
            return "YES".equals(columns.getString("IS_NULLABLE"));
        }
    }

    private static String matchCase(java.sql.Connection connection, String name) throws SQLException {
        return connection.getMetaData().storesLowerCaseIdentifiers() ? name.toLowerCase() : name;
    }
}
