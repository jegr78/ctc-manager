package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class V32IncidentRulingConnectionMigrationTest {
    @Test
    void givenClosedMigrationConnection_whenWritingRulingStates_thenKeepValidAndInvalidMembershipRules() throws Exception {
        // given
        String url = "jdbc:h2:mem:ruling-connection-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        UUID report = UUID.randomUUID(), thread = UUID.randomUUID(), ruling = UUID.randomUUID(), revision = UUID.randomUUID();
        try {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE incident_reports (id UUID PRIMARY KEY)");
                sql.execute("CREATE TABLE incident_thread_publications (id UUID PRIMARY KEY)");
                sql.execute("CREATE TABLE incident_deliveries (id UUID PRIMARY KEY, operation VARCHAR(16))");
                execute(connection, "INSERT INTO incident_reports(id) VALUES (?)", report);
                execute(connection, "INSERT INTO incident_thread_publications(id) VALUES (?)", thread);
                var migration = mock(Context.class);
                when(migration.getConnection()).thenReturn(connection);
                new V32__AddIncidentRulings().migrate(migration);
            }

            // when / then: the migration session is closed before these writes.
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                execute(connection, "INSERT INTO incident_rulings(id,report_id,aggregate_version,latest_revision_number) VALUES (?,?,1,1)", ruling, report);
                execute(connection, "INSERT INTO incident_ruling_revisions(id,ruling_id,revision_number,ruling_text,reason,actor_id,accepted_at) VALUES (?,?,1,'Test ruling','','test-manager',TIMESTAMP '2026-11-04 12:00:00')", revision, ruling);
                assertThatCode(() -> execute(connection, "INSERT INTO incident_ruling_publications(id,revision_id,thread_publication_id,guild_id,channel_id,content,nonce,requested_by,requested_at,state,aggregate_version,attempts,next_attempt_at) VALUES (?,?,?,'100','800','Test ruling','test-nonce','test-manager',TIMESTAMP '2026-11-04 12:00:00','PENDING',0,0,TIMESTAMP '2026-11-04 12:00:00')", UUID.randomUUID(), revision, thread))
                        .as("valid publication remains writable after its migration session closes").doesNotThrowAnyException();
                for (String state : List.of("PENDING", "CLAIMED", "SENT", "FAILED", "UNCERTAIN", "SUPERSEDED")) {
                    assertThatCode(() -> sql.executeUpdate("UPDATE incident_ruling_publications SET state='" + state + "'"))
                            .as("supported ruling state %s", state).doesNotThrowAnyException();
                }
                for (String state : List.of("UNKNOWN", "pending", "")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_ruling_publications SET state='" + state + "'"))
                            .as("unsupported ruling state %s", state)
                            .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23513"));
                }
                try (var rows = sql.executeQuery("SELECT state FROM incident_ruling_publications")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("SUPERSEDED");
                    assertThat(rows.next()).isFalse();
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("SHUTDOWN");
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }
}
