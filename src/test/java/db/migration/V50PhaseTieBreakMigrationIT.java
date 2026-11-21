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
        String matchId;
        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT MIN(id) FROM matches");
             var rows = query.executeQuery()) {
            rows.next();
            matchId = rows.getString(1);
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
        assertThat(matchId).as("seeded match to update").isNotNull();
        assertThatThrownBy(() -> {
            try (var connection = dataSource.getConnection();
                 var statement = connection.prepareStatement("UPDATE matches SET tie_break_winner_id = ? WHERE id = ?")) {
                statement.setObject(1, UUID.randomUUID().toString());
                statement.setString(2, matchId);
                statement.executeUpdate();
            }
        }).isInstanceOf(SQLException.class);
    }

    private String defaultOf(String table, String column) throws SQLException {
        return String.valueOf(column(table, column, "COLUMN_DEF"));
    }

    private boolean nullable(String table, String column) throws SQLException {
        return "YES".equals(column(table, column, "IS_NULLABLE"));
    }

    private String column(String table, String column, String attribute) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            for (var name : new String[]{table.toLowerCase(), table.toUpperCase()}) {
                try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, name, null)) {
                    while (columns.next()) {
                        if (columns.getString("COLUMN_NAME").equalsIgnoreCase(column)) return columns.getString(attribute);
                    }
                }
            }
        }
        throw new AssertionError(table + "." + column + " does not exist");
    }
}
